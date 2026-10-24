package dev.colorclash.net.host

import dev.colorclash.protocol.SeatInfo
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.rules.GameState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class HostConfig(
    /** How long a dropped player's seat is held before a bot takes over. */
    val reconnectGrace: Duration = 60.seconds,
    /** A connection that sends nothing (not even a ping) for this long is treated as dead. */
    val heartbeatTimeout: Duration = 6.seconds,
    val botTurnDelay: Duration = 900.milliseconds,
    val botCallDelay: Duration = 300.milliseconds,
    /** Gives the humans at the table a fair chance to catch a missed UNO before a bot does. */
    val botCatchDelay: Duration = 2.seconds,
    /** Start the next round on its own after this long. Null waits for the host to do it. */
    val autoNextRound: Duration? = null,
    /** Each player can react this often at most, so emoji stay fun rather than a flood. */
    val reactionCooldown: Duration = 700.milliseconds,
)

/** One player's link to the host. Implementations must not block. */
interface Connection {
    fun send(message: ServerMessage)
    fun close(reason: String)
}

enum class HostStage { LOBBY, PLAYING, ROUND_OVER, GAME_OVER, CLOSED }

/** Host-side snapshot for the host UI and tests. [game] is the full secret state, never send it anywhere. */
data class HostStatus(
    val stage: HostStage,
    val seats: List<SeatInfo>,
    val game: GameState? = null,
    val canStart: Boolean = false,
)
