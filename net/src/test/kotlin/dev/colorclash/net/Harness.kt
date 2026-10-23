package dev.colorclash.net

import dev.colorclash.net.client.ClientConfig
import dev.colorclash.net.client.ClientStatus
import dev.colorclash.net.client.GameClient
import dev.colorclash.net.host.GameHost
import dev.colorclash.net.host.HostConfig
import dev.colorclash.net.host.HostServer
import dev.colorclash.net.host.HostStatus
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerIntent
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.CardColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val fastHost = HostConfig(
    reconnectGrace = 2.seconds,
    heartbeatTimeout = 1.seconds,
    botTurnDelay = Duration.ZERO,
    botCallDelay = Duration.ZERO,
    botCatchDelay = 50.milliseconds,
    autoNextRound = 20.milliseconds,
)

val fastClient = ClientConfig(
    pingInterval = 200.milliseconds,
    pingTimeout = 1.seconds,
    connectTimeout = 1.seconds,
    firstConnectFor = 2.seconds,
    reconnectFor = 10.seconds,
    minBackoff = 150.milliseconds,
    maxBackoff = 300.milliseconds,
)

const val ROOM = "TEST"

/** A real host on a real socket plus any number of clients, all in-process. */
class Harness(hostConfig: HostConfig = fastHost, seed: Long = 42) : AutoCloseable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val host = GameHost(scope, hostConfig, roomCode = ROOM, seed = { seed }, botRandom = Random(seed))
    private val server = HostServer(host, port = 0)
    private val clients = mutableListOf<GameClient>()
    var port = 0
        private set

    suspend fun start(): Harness = apply { port = server.start() }

    fun client(
        name: String,
        token: String? = null,
        room: String = ROOM,
        config: ClientConfig = fastClient,
        viaPort: Int = port,
    ): GameClient =
        GameClient(scope, config).also {
            clients += it
            it.connect("127.0.0.1", viaPort, room, name, sessionToken = token)
        }

    suspend fun awaitHost(timeout: Duration = 10.seconds, predicate: (HostStatus) -> Boolean): HostStatus =
        withTimeout(timeout) { host.status.first(predicate) }

    override fun close() {
        clients.forEach { it.close() }
        host.close()
        server.stop()
        scope.cancel()
    }
}

suspend fun GameClient.awaitStatus(timeout: Duration = 5.seconds, predicate: (ClientStatus) -> Boolean): ClientStatus =
    withTimeout(timeout) { status.first(predicate) }

suspend fun GameClient.awaitConnected() = awaitStatus { it == ClientStatus.Connected }

/**
 * Plays for [client] whenever it has something to do. Pauses while [running] is false so a
 * test can freeze the table and poke at it. Like a person tapping again when nothing happened,
 * it retries now and then in case a move was lost with a dropped connection.
 */
fun CoroutineScope.autoPlay(client: GameClient, running: StateFlow<Boolean> = MutableStateFlow(true)): Job = launch {
    val retry = flow {
        var tick = 0
        while (true) {
            emit(tick++)
            delay(RETRY_EVERY)
        }
    }
    combine(client.game, running, retry) { view, go, _ -> view.takeIf { go } }.collectLatest { view ->
        view?.let(::decide)?.let(client::send)
    }
}

private val RETRY_EVERY = 400.milliseconds

/** A simple player that only looks at its own view, like a real phone would. */
fun decide(view: PlayerView): PlayerIntent? {
    if (view.phase == PhaseKind.ROUND_OVER || view.phase == PhaseKind.GAME_OVER) return null
    view.exposedPlayer?.let { if (it != view.you) return PlayerIntent.Catch(it) }
    if (view.canCallLastCard) return PlayerIntent.CallLastCard
    if (!view.isYourTurn) return null
    return when (view.phase) {
        PhaseKind.PICK_COLOR -> {
            val color = view.hand.mapNotNull { it.color }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            PlayerIntent.ChooseColor(color ?: CardColor.RED)
        }
        PhaseKind.PICK_SWAP_TARGET -> PlayerIntent.SwapWith(view.seats.first { it.info.seat != view.you }.info.seat)
        PhaseKind.DREW_PLAYABLE -> PlayerIntent.Play(checkNotNull(view.drawnCardId))
        else -> view.playable.firstOrNull()?.let { PlayerIntent.Play(it) }
            ?: if (view.canChallenge) PlayerIntent.Challenge else PlayerIntent.Draw
    }
}
