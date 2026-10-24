package dev.colorclash.protocol

import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Direction
import dev.colorclash.rules.RuleSet
import kotlinx.serialization.Serializable

const val DEFAULT_TURN_SECONDS = 15

@Serializable
data class GameSettings(
    val rules: RuleSet = RuleSet(),
    /** Null means no turn timer. */
    val turnTimerSeconds: Int? = DEFAULT_TURN_SECONDS,
)

@Serializable
data class SeatInfo(
    val seat: Int,
    val name: String,
    val avatar: Int,
    val isBot: Boolean = false,
    val isHost: Boolean = false,
    val ready: Boolean = false,
    val connected: Boolean = true,
    /** A dropped human whose seat a bot is playing for now. */
    val botControlled: Boolean = false,
)

@Serializable
data class LobbyView(
    val roomCode: String,
    val you: Int,
    val seats: List<SeatInfo>,
    val settings: GameSettings,
    val canStart: Boolean,
)

@Serializable
data class SeatView(
    val info: SeatInfo,
    val cardCount: Int,
    val score: Int,
    val calledLastCard: Boolean,
)

enum class PhaseKind { TURN, PICK_COLOR, DREW_PLAYABLE, PICK_SWAP_TARGET, ROUND_OVER, GAME_OVER }

/** Everything one player is allowed to see: their own hand plus public table state. */
@Serializable
data class PlayerView(
    val you: Int,
    val hand: List<Card>,
    val playable: Set<Int>,
    val seats: List<SeatView>,
    val topCard: Card,
    val activeColor: CardColor?,
    val direction: Direction,
    val current: Int,
    val phase: PhaseKind,
    /** Only set for the player who drew it. */
    val drawnCardId: Int? = null,
    val winner: Int? = null,
    val roundPoints: Int? = null,
    val pendingDraw: Int = 0,
    val exposedPlayer: Int? = null,
    val drawPileCount: Int,
    val round: Int,
    val dealer: Int,
    val settings: GameSettings,
    val canDraw: Boolean = false,
    val canPass: Boolean = false,
    val canCallLastCard: Boolean = false,
    val canChallenge: Boolean = false,
    val turnMillisLeft: Long? = null,
) {
    val isYourTurn: Boolean get() = current == you
}
