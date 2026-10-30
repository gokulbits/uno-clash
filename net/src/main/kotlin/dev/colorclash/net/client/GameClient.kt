package dev.colorclash.net.client

import dev.colorclash.protocol.AUTO_AVATAR_COLOR
import dev.colorclash.protocol.ClientMessage
import dev.colorclash.protocol.Codec
import dev.colorclash.protocol.JoinError
import dev.colorclash.protocol.LobbyView
import dev.colorclash.protocol.MAX_PHOTO_CHARS
import dev.colorclash.protocol.PROTOCOL_VERSION
import dev.colorclash.protocol.PlayerIntent
import dev.colorclash.protocol.PlayerView
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.protocol.WS_PATH
import dev.colorclash.protocol.isWellFormed
import dev.colorclash.net.Rejoin
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class ClientConfig(
    val pingInterval: Duration = 2.seconds,
    /**
     * No message from the host for this long and we assume the link died. Phone Wi-Fi can stall for
     * several seconds (power save, scans, Bluetooth sharing the radio), so this must ride those out.
     */
    val pingTimeout: Duration = 15.seconds,
    val connectTimeout: Duration = 4.seconds,
    /** From starting a connection to the socket being open. */
    val handshakeTimeout: Duration = 10.seconds,
    /** Give up on a first connection that never worked after this long. */
    val firstConnectFor: Duration = 10.seconds,
    /** Keep trying to get back into a game we were in for this long. */
    val reconnectFor: Duration = 5.minutes,
    val minBackoff: Duration = 300.milliseconds,
    val maxBackoff: Duration = 3.seconds,
)

sealed interface ClientStatus {
    data object Idle : ClientStatus
    data object Connecting : ClientStatus
    data object Connected : ClientStatus
    data class Reconnecting(val attempt: Int) : ClientStatus
    data class Failed(val message: String, val error: JoinError? = null) : ClientStatus
    data class Closed(val reason: String) : ClientStatus
}

/**
 * One player's connection to a host. Reconnects with its session token on its own and
 * gets a full resync from the host each time.
 */
class GameClient(
    parent: CoroutineScope,
    private val config: ClientConfig = ClientConfig(),
) {
    /**
     * A child of the caller's scope, so [close] reaps every attempt this client ever started and
     * not just the latest. A dead link is deliberately never waited on, so without this a long
     * outage left a pile of half-torn-down sessions in a process-lifetime scope.
     */
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    private val http = HttpClient(CIO) {
        // The host is just another phone on the network; it gets the same limit it gives its guests.
        install(WebSockets) { maxFrameSize = MAX_FRAME_BYTES }
        engine {
            requestTimeout = 0
            endpoint.connectTimeout = config.connectTimeout.inWholeMilliseconds
        }
    }

    private val _status = MutableStateFlow<ClientStatus>(ClientStatus.Idle)
    val status: StateFlow<ClientStatus> = _status.asStateFlow()

    private val _lobby = MutableStateFlow<LobbyView?>(null)
    val lobby: StateFlow<LobbyView?> = _lobby.asStateFlow()

    private val _game = MutableStateFlow<PlayerView?>(null)
    val game: StateFlow<PlayerView?> = _game.asStateFlow()

    /** Every game update in order, for animations and sounds. */
    private val _updates = MutableSharedFlow<ServerMessage.Game>(extraBufferCapacity = 256)
    val updates: SharedFlow<ServerMessage.Game> = _updates.asSharedFlow()

    /** Reactions from everyone, yours included once the host echoes them. */
    private val _reactions = MutableSharedFlow<ServerMessage.Reaction>(extraBufferCapacity = 32)
    val reactions: SharedFlow<ServerMessage.Reaction> = _reactions.asSharedFlow()

    /** Photos by id (base64 JPEG), kept across reconnects. */
    private val _photos = MutableStateFlow<Map<String, String>>(emptyMap())
    val photos: StateFlow<Map<String, String>> = _photos.asStateFlow()

    /** Short messages worth showing, like a rejected move. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    @Volatile var sessionToken: String? = null
        private set

    @Volatile private var outbox: Channel<ClientMessage>? = null
    @Volatile private var session: DefaultClientWebSocketSession? = null
    @Volatile private var leaving = false
    private var loop: Job? = null
    @Volatile private var sessionJob: Job? = null
    private var everConnected = false
    private var attempt = 0
    private var lostAt: TimeMark? = null

    fun connect(
        host: String,
        port: Int,
        roomCode: String,
        name: String,
        avatar: Int = 0,
        sessionToken: String? = null,
        avatarColor: Int = AUTO_AVATAR_COLOR,
        avatarFrame: Int = 0,
        photo: String? = null,
    ) {
        check(loop == null) { "Already connected; make a new client to join somewhere else" }
        this.sessionToken = sessionToken
        val target = Target(host, port, roomCode.trim().uppercase(), name, avatar, avatarColor, avatarFrame, photo)
        loop = scope.launch { connectLoop(target) }
    }

    fun send(intent: PlayerIntent) = post(ClientMessage.Intent(intent))

    fun setReady(ready: Boolean) = post(ClientMessage.SetReady(ready))

    fun react(reaction: Int) = post(ClientMessage.React(reaction))

    /** Take your seat back from the bot after timing out too often. */
    fun resume() = post(ClientMessage.Resume)

    fun leave() {
        leaving = true
        if (!post(ClientMessage.Leave)) close()
    }

    fun close() {
        scope.cancel()
        http.close()
        if (_status.value !is ClientStatus.Failed) _status.value = ClientStatus.Closed("Closed")
    }

    /** Kills the socket without saying goodbye, as if Wi-Fi dropped. For tests. */
    internal fun dropConnection() {
        session?.cancel()
    }

    private fun post(message: ClientMessage): Boolean {
        val sent = outbox?.trySend(message)?.isSuccess == true
        if (!sent) _notices.tryEmit("Not connected to the host")
        return sent
    }

    private suspend fun connectLoop(target: Target) {
        _status.value = ClientStatus.Connecting
        while (true) {
            val outcome = nextSessionOutcome(target)
            when (outcome) {
                is Outcome.Rejected -> {
                    _status.value = ClientStatus.Failed(outcome.message, outcome.error)
                    return
                }
                is Outcome.Ended -> {
                    _status.value = ClientStatus.Closed(outcome.reason)
                    return
                }
                Outcome.Dropped -> {
                    if (leaving) {
                        _status.value = ClientStatus.Closed("You left the room")
                        return
                    }
                    val since = lostAt ?: TimeSource.Monotonic.markNow().also { lostAt = it }
                    val limit = if (everConnected) config.reconnectFor else config.firstConnectFor
                    if (since.elapsedNow() > limit) {
                        val message = if (everConnected) "Lost connection to the host" else "Couldn't reach the host"
                        _status.value = ClientStatus.Failed(message)
                        return
                    }
                    attempt++
                    if (everConnected) _status.value = ClientStatus.Reconnecting(attempt)
                    delay(backoff(attempt))
                }
            }
        }
    }

    /**
     * Runs one connection in its own job and returns as soon as it's over. On a dead link Ktor's
     * polite teardown (and even the opening handshake) can hang for a long time, so we never wait
     * for it: the old job is cancelled and left to finish in the background.
     */
    private suspend fun nextSessionOutcome(target: Target): Outcome {
        val opened = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Outcome>()
        val job = scope.launch {
            try {
                runSession(target, opened, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connection refused, reset, unreachable...
            } finally {
                result.complete(Outcome.Dropped)
            }
        }
        sessionJob = job
        val handshake = withTimeoutOrNull(config.handshakeTimeout) {
            select {
                opened.onAwait { true }
                result.onAwait { false }
            }
        }
        val outcome = if (handshake == null) Outcome.Dropped else result.await()
        job.cancel()
        return outcome
    }

    private suspend fun runSession(target: Target, opened: CompletableDeferred<Unit>, result: CompletableDeferred<Outcome>) {
        var outcome: Outcome = Outcome.Dropped
        http.webSocket(host = target.host, port = target.port, path = WS_PATH) {
            session = this
            opened.complete(Unit)
            val out = Channel<ClientMessage>(Channel.UNLIMITED)
            // A fresh nonce per attempt: the host spends each one, so a reused proof is refused.
            val token = sessionToken
            val nonce = token?.let { Rejoin.newNonce() }
            out.trySend(
                ClientMessage.Hello(
                    protocolVersion = PROTOCOL_VERSION,
                    roomCode = target.roomCode,
                    name = target.name,
                    avatar = target.avatar,
                    rejoinNonce = nonce,
                    rejoinProof = if (token != null && nonce != null) Rejoin.proof(token, nonce, target.roomCode) else null,
                    avatarColor = target.avatarColor,
                    avatarFrame = target.avatarFrame,
                    photo = target.photo,
                ),
            )
            val writer = launch { for (message in out) send(Frame.Text(Codec.encode(message))) }
            val pinger = launch {
                var nonce = 0L
                while (true) {
                    delay(config.pingInterval)
                    out.trySend(ClientMessage.Ping(nonce++))
                }
            }
            try {
                while (true) {
                    val frame = withTimeoutOrNull(config.pingTimeout) { incoming.receiveCatching().getOrNull() } ?: break
                    if (frame !is Frame.Text) continue
                    val message = try {
                        Codec.decodeServer(frame.readText())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        outcome = Outcome.Rejected("This phone couldn't understand the host. Install the same build on every phone.", JoinError.VERSION_MISMATCH)
                        break
                    }
                    val end = handle(message, out)
                    if (end != null) {
                        outcome = end
                        break
                    }
                }
            } finally {
                outbox = null
                session = null
                pinger.cancel()
                writer.cancel()
                out.close()
                result.complete(outcome)
            }
        }
    }

    /** Returns an outcome when this message ends the session. */
    private fun handle(message: ServerMessage, out: Channel<ClientMessage>): Outcome? {
        when (message) {
            is ServerMessage.Welcome -> {
                sessionToken = message.sessionToken
                outbox = out
                everConnected = true
                attempt = 0
                lostAt = null
                _status.value = ClientStatus.Connected
            }
            is ServerMessage.JoinRejected -> return Outcome.Rejected(message.message, message.error)
            is ServerMessage.Lobby -> {
                _lobby.value = message.view
                _game.value = null
            }
            is ServerMessage.Game -> {
                // The table indexes seats by these, so a broken view would take the app down with it.
                if (!message.view.isWellFormed()) {
                    return Outcome.Rejected("This phone couldn't understand the host. Install the same build on every phone.", JoinError.VERSION_MISMATCH)
                }
                _game.value = message.view
                _updates.tryEmit(message)
            }
            is ServerMessage.ActionRejected -> _notices.tryEmit(message.reason)
            is ServerMessage.Reaction -> _reactions.tryEmit(message)
            is ServerMessage.Photo -> if (message.data.length <= MAX_PHOTO_CHARS) _photos.update { current ->
                // Ids are content hashes, so a table that swaps avatars would grow this forever.
                if (message.id in current) {
                    current
                } else {
                    val next = current + (message.id to message.data)
                    if (next.size <= MAX_PHOTOS) next else next.entries.drop(next.size - MAX_PHOTOS).associate { it.key to it.value }
                }
            }
            is ServerMessage.Pong -> Unit
            is ServerMessage.Closed -> return Outcome.Ended(message.reason)
        }
        return null
    }

    private fun backoff(attempt: Int): Duration =
        (config.minBackoff * (1 shl (attempt - 1).coerceAtMost(6))).coerceAtMost(config.maxBackoff)

    private data class Target(
        val host: String,
        val port: Int,
        val roomCode: String,
        val name: String,
        val avatar: Int,
        val avatarColor: Int,
        val avatarFrame: Int,
        val photo: String?,
    )

    private sealed interface Outcome {
        data object Dropped : Outcome
        data class Rejected(val message: String, val error: JoinError?) : Outcome
        data class Ended(val reason: String) : Outcome
    }

    private companion object {
        /** Four times a full table, so nobody loses a photo that's still on screen. */
        const val MAX_PHOTOS = 24
        const val MAX_FRAME_BYTES = 256L * 1024
    }
}
