package dev.colorclash.net.host

import dev.colorclash.protocol.ClientMessage
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.JoinError
import dev.colorclash.protocol.LobbyView
import dev.colorclash.protocol.PROTOCOL_VERSION
import dev.colorclash.protocol.SeatInfo
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.protocol.toAction
import dev.colorclash.protocol.toView
import dev.colorclash.protocol.viewFor
import dev.colorclash.rules.Action
import dev.colorclash.rules.EngineResult
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameEvent
import dev.colorclash.rules.GameState
import dev.colorclash.rules.Phase
import dev.colorclash.rules.SimpleBot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The authoritative room: seats, lobby, the engine state, bots and timers.
 * Everything runs on one actor coroutine, so none of the state below needs locking.
 * Transport-agnostic; [HostServer] plugs WebSockets into it.
 */
class GameHost(
    private val scope: CoroutineScope,
    val config: HostConfig = HostConfig(),
    val roomCode: String = RoomCode.generate(),
    private val seed: () -> Long = { SecureRandom().nextLong() },
    botRandom: Random = Random.Default,
) {
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val seats = mutableListOf<Seat>()
    private val seatByConnection = mutableMapOf<Connection, Seat>()
    private val bot = SimpleBot(botRandom)
    private var settings = GameSettings()
    private var game: GameState? = null
    private var version = 0L
    private var automation: Job? = null
    private var turnTimer: Job? = null
    private var turnClock: TurnClock? = null

    private val _status = MutableStateFlow(HostStatus(HostStage.LOBBY, emptyList()))
    val status: StateFlow<HostStatus> = _status.asStateFlow()

    init {
        scope.launch {
            for (command in commands) {
                try {
                    handle(command)
                } catch (e: Exception) {
                    // Keep the room alive; one bad command shouldn't end everyone's game.
                    System.err.println("GameHost: failed handling $command: $e")
                    e.printStackTrace()
                }
            }
        }
    }

    fun onMessage(connection: Connection, message: ClientMessage) = post(Command.Received(connection, message))
    fun onDisconnect(connection: Connection) = post(Command.Disconnected(connection))

    /** Creates the host player's own seat. Its client joins with the returned token. */
    fun reserveHostSeat(name: String, avatar: Int = 0): String {
        val token = RoomCode.newSessionToken()
        post(Command.ReserveHostSeat(token, name, avatar))
        return token
    }

    fun updateSettings(settings: GameSettings) = post(Command.UpdateSettings(settings))
    fun addBot() = post(Command.AddBot)
    fun removeBot(seat: Int) = post(Command.RemoveBot(seat))
    fun startNextRound() = post(Command.NextRound)
    fun returnToLobby() = post(Command.ReturnToLobby)
    fun close() = post(Command.Close)

    /** Null on success, otherwise why the game can't start yet. */
    suspend fun startGame(): String? {
        val reply = CompletableDeferred<String?>()
        post(Command.StartGame(reply))
        return reply.await()
    }

    private fun post(command: Command) {
        commands.trySend(command)
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Received -> receive(command.connection, command.message)
            is Command.Disconnected -> disconnected(command.connection)
            is Command.ReserveHostSeat -> if (game == null && seats.size < GameEngine.MAX_PLAYERS) {
                seats += Seat(command.token, cleanName(command.name), command.avatar, isBot = false, isHost = true)
                lobbyChanged()
            }
            is Command.UpdateSettings -> if (game == null) {
                settings = command.settings
                lobbyChanged()
            }
            Command.AddBot -> if (game == null && seats.size < GameEngine.MAX_PLAYERS) {
                seats += Seat(RoomCode.newSessionToken(), nextBotName(), seats.size, isBot = true, isHost = false)
                lobbyChanged()
            }
            is Command.RemoveBot -> if (game == null && seats.getOrNull(command.seat)?.isBot == true) {
                seats.removeAt(command.seat)
                lobbyChanged()
            }
            is Command.StartGame -> command.reply.complete(startGame(seed()))
            Command.NextRound -> if (game?.phase is Phase.RoundOver) apply(Action.StartNextRound)
            Command.ReturnToLobby -> backToLobby()
            is Command.GraceExpired -> {
                val seat = command.seat
                if (game != null && seat in seats && seat.connection == null) {
                    seat.botControlled = true
                    gameChanged(emptyList())
                }
            }
            is Command.Automate -> if (command.version == version) apply(command.action)
            is Command.TurnTimeout -> if (command.version == version) playForTimedOutSeat()
            Command.Close -> shutDown()
        }
    }

    // Connections

    private fun receive(connection: Connection, message: ClientMessage) {
        if (message is ClientMessage.Ping) return connection.send(ServerMessage.Pong(message.nonce))
        if (message is ClientMessage.Hello) return hello(connection, message)
        val seat = seatByConnection[connection] ?: return
        when (message) {
            is ClientMessage.SetReady -> if (game == null) {
                seat.ready = message.ready
                lobbyChanged()
            }
            is ClientMessage.Intent -> {
                val error = apply(message.intent.toAction(seats.indexOf(seat)))
                if (error != null) connection.send(ServerMessage.ActionRejected(error))
            }
            ClientMessage.Leave -> {
                seatByConnection.remove(connection)
                seat.connection = null
                connection.close("Left the room")
                seatLost(seat, immediately = true)
            }
            else -> Unit
        }
    }

    private fun hello(connection: Connection, hello: ClientMessage.Hello) {
        if (connection in seatByConnection) return
        if (hello.protocolVersion != PROTOCOL_VERSION) {
            return reject(
                connection, JoinError.VERSION_MISMATCH,
                "This phone has game version ${hello.protocolVersion} but the host has $PROTOCOL_VERSION. " +
                    "Install the same build on every phone.",
            )
        }
        if (!hello.roomCode.equals(roomCode, ignoreCase = true)) {
            return reject(connection, JoinError.WRONG_ROOM, "That room code doesn't match this host.")
        }
        val returning = hello.sessionToken?.let { token -> seats.firstOrNull { !it.isBot && it.token == token } }
        if (returning != null) return attach(returning, connection)
        if (game != null) return reject(connection, JoinError.GAME_IN_PROGRESS, "A game is already running in this room.")
        if (seats.size >= GameEngine.MAX_PLAYERS) return reject(connection, JoinError.ROOM_FULL, "The room is full.")

        val seat = Seat(RoomCode.newSessionToken(), cleanName(hello.name), hello.avatar, isBot = false, isHost = false)
        seats += seat
        attach(seat, connection)
    }

    private fun attach(seat: Seat, connection: Connection) {
        seat.connection?.takeIf { it != connection }?.let { old ->
            seatByConnection.remove(old)
            old.close("Replaced by a newer connection")
        }
        seat.connection = connection
        seat.graceJob?.cancel()
        seat.graceJob = null
        seat.botControlled = false
        seatByConnection[connection] = seat
        connection.send(ServerMessage.Welcome(seats.indexOf(seat), seat.token, roomCode))
        if (game == null) lobbyChanged() else gameChanged(emptyList())
    }

    private fun reject(connection: Connection, error: JoinError, message: String) {
        connection.send(ServerMessage.JoinRejected(error, message))
        connection.close(message)
    }

    private fun disconnected(connection: Connection) {
        val seat = seatByConnection.remove(connection) ?: return
        if (seat.connection != connection) return
        seat.connection = null
        seatLost(seat, immediately = false)
    }

    /** In the lobby the seat just goes away. Mid-game it's held for a while, then a bot plays it. */
    private fun seatLost(seat: Seat, immediately: Boolean) {
        if (game == null) {
            if (!seat.isHost) seats.remove(seat)
            lobbyChanged()
            return
        }
        if (immediately) {
            seat.botControlled = true
        } else {
            seat.graceJob = scope.launch {
                delay(config.reconnectGrace)
                post(Command.GraceExpired(seat))
            }
        }
        gameChanged(emptyList())
    }

    // Game flow

    private fun startGame(seed: Long): String? {
        if (game != null) return "A game is already running"
        if (seats.size < GameEngine.MIN_PLAYERS) return "Need at least ${GameEngine.MIN_PLAYERS} players"
        if (!seats.all { it.isReady }) return "Waiting for everyone to be ready"
        val (state, events) = GameEngine.newGame(seats.size, settings.rules, seed)
        game = state
        gameChanged(events)
        return null
    }

    /** Runs [action] through the engine. Returns the rejection reason, or null if it was applied. */
    private fun apply(action: Action): String? {
        val current = game ?: return "No game is running"
        return when (val result = GameEngine.reduce(current, action)) {
            is EngineResult.Rejected -> result.reason
            is EngineResult.Ok -> {
                game = result.state
                gameChanged(result.events)
                null
            }
        }
    }

    private fun gameChanged(events: List<GameEvent>) {
        val state = game ?: return
        version++
        updateTurnTimer(state)
        val infos = seatInfos()
        val millisLeft = turnClock?.millisLeft()
        seats.forEachIndexed { i, seat ->
            seat.connection?.send(
                ServerMessage.Game(state.viewFor(i, infos, settings, millisLeft), events.map { it.toView(i) }),
            )
        }
        publishStatus()
        scheduleAutomation(state)
    }

    private fun lobbyChanged() {
        val infos = seatInfos()
        val canStart = canStart()
        seats.forEachIndexed { i, seat ->
            seat.connection?.send(ServerMessage.Lobby(LobbyView(roomCode, i, infos, settings, canStart)))
        }
        publishStatus()
    }

    private fun backToLobby() {
        if (game == null) return
        game = null
        cancelTimers()
        // Anyone who dropped out during the game doesn't come back to the lobby.
        seats.removeAll { !it.isBot && !it.isHost && it.connection == null }
        seats.forEach {
            it.botControlled = false
            it.graceJob?.cancel()
            if (!it.isBot && !it.isHost) it.ready = false
        }
        lobbyChanged()
    }

    private fun shutDown() {
        cancelTimers()
        seats.forEach { seat ->
            seat.graceJob?.cancel()
            seat.connection?.let {
                it.send(ServerMessage.Closed("The host ended the game"))
                it.close("Host closed the room")
            }
            seat.connection = null
        }
        seatByConnection.clear()
        _status.value = _status.value.copy(stage = HostStage.CLOSED)
        commands.close()
    }

    // Bots and timers

    private fun isAutomated(seat: Int) = seats[seat].isBot || seats[seat].botControlled

    private fun scheduleAutomation(state: GameState) {
        automation?.cancel()
        automation = null
        when (state.phase) {
            is Phase.RoundOver -> {
                config.autoNextRound?.let { schedule(it, Action.StartNextRound) }
                return
            }
            is Phase.GameOver -> return
            else -> Unit
        }
        val moves = seats.indices.filter(::isAutomated).mapNotNull { bot.chooseAction(state, it) }
        val move = moves.firstOrNull { it is Action.CatchLastCard }
            ?: moves.firstOrNull { it is Action.CallLastCard }
            ?: moves.firstOrNull()
            ?: return
        val wait = when (move) {
            is Action.CatchLastCard -> config.botCatchDelay
            is Action.CallLastCard -> config.botCallDelay
            else -> config.botTurnDelay
        }
        schedule(wait, move)
    }

    private fun schedule(wait: Duration, action: Action) {
        val expected = version
        automation = scope.launch {
            delay(wait)
            post(Command.Automate(expected, action))
        }
    }

    private fun updateTurnTimer(state: GameState) {
        turnTimer?.cancel()
        val seconds = settings.turnTimerSeconds
        val active = state.phase !is Phase.RoundOver && state.phase !is Phase.GameOver
        if (seconds == null || !active || isAutomated(state.current)) {
            turnClock = null
            return
        }
        // Keep the deadline while it's still the same decision, e.g. someone else calling "last card".
        val clock = turnClock?.takeIf { it.seat == state.current && it.phase == state.phase && it.round == state.round }
            ?: TurnClock(state.current, state.phase, state.round, TimeSource.Monotonic.markNow() + seconds.seconds)
        turnClock = clock
        val expected = version
        turnTimer = scope.launch {
            delay(clock.remaining())
            post(Command.TurnTimeout(expected))
        }
    }

    /** The current player ran out of time: let the bot finish their turn. */
    private fun playForTimedOutSeat() {
        val seat = game?.current ?: return
        repeat(4) {
            val state = game ?: return
            if (state.current != seat) return
            val action = bot.chooseTurnAction(state, seat) ?: return
            if (apply(action) != null) return
        }
    }

    private fun cancelTimers() {
        automation?.cancel()
        turnTimer?.cancel()
        turnClock = null
    }

    // Helpers

    private fun canStart() = game == null && seats.size >= GameEngine.MIN_PLAYERS && seats.all { it.isReady }

    private fun seatInfos() = seats.mapIndexed { i, s ->
        SeatInfo(
            seat = i,
            name = s.name,
            avatar = s.avatar,
            isBot = s.isBot,
            isHost = s.isHost,
            ready = s.isReady,
            connected = s.isBot || s.connection != null,
            botControlled = s.botControlled,
        )
    }

    private fun publishStatus() {
        val state = game
        val stage = when (state?.phase) {
            null -> HostStage.LOBBY
            is Phase.RoundOver -> HostStage.ROUND_OVER
            is Phase.GameOver -> HostStage.GAME_OVER
            else -> HostStage.PLAYING
        }
        _status.value = HostStatus(stage, seatInfos(), state, canStart())
    }

    private fun cleanName(raw: String): String =
        raw.filter { !it.isISOControl() }.trim().take(MAX_NAME_LENGTH).ifBlank { "Player ${seats.size + 1}" }

    private fun nextBotName(): String =
        BOT_NAMES.firstOrNull { name -> seats.none { it.name == name } } ?: "Bot ${seats.size + 1}"

    private class Seat(
        val token: String,
        val name: String,
        val avatar: Int,
        val isBot: Boolean,
        val isHost: Boolean,
    ) {
        var connection: Connection? = null
        var ready = false
        var botControlled = false
        var graceJob: Job? = null
        val isReady get() = ready || isBot || isHost
    }

    private data class TurnClock(val seat: Int, val phase: Phase, val round: Int, val deadline: TimeMark) {
        fun remaining(): Duration = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
        fun millisLeft(): Long = remaining().inWholeMilliseconds
    }

    private sealed interface Command {
        class Received(val connection: Connection, val message: ClientMessage) : Command
        class Disconnected(val connection: Connection) : Command
        class ReserveHostSeat(val token: String, val name: String, val avatar: Int) : Command
        class UpdateSettings(val settings: GameSettings) : Command
        data object AddBot : Command
        class RemoveBot(val seat: Int) : Command
        class StartGame(val reply: CompletableDeferred<String?>) : Command
        data object NextRound : Command
        data object ReturnToLobby : Command
        class GraceExpired(val seat: Seat) : Command
        class Automate(val version: Long, val action: Action) : Command
        class TurnTimeout(val version: Long) : Command
        data object Close : Command
    }

    private companion object {
        const val MAX_NAME_LENGTH = 16
        val BOT_NAMES = listOf("Robo", "Chip", "Pixel", "Bolt", "Sprocket", "Widget")
    }
}
