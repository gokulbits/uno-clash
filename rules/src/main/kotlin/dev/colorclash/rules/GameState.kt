package dev.colorclash.rules

enum class Direction(val step: Int) {
    CLOCKWISE(1), COUNTER_CLOCKWISE(-1);

    fun reversed() = if (this == CLOCKWISE) COUNTER_CLOCKWISE else CLOCKWISE
}

data class Player(
    val hand: List<Card>,
    val score: Int = 0,
    val calledLastCard: Boolean = false,
)

/** Recorded when a Wild Draw 4 is played, so a challenge can be judged later. */
data class DrawFourCheck(val offender: Int, val guilty: Boolean)

sealed interface Phase {
    /** Current player plays or draws. With a pending draw they must stack, accept it or challenge. */
    data object Turn : Phase

    /** Current player picks a color for the wild on top. [keepTurn] is for a wild turned over at round start. */
    data class PickColor(val keepTurn: Boolean = false) : Phase

    /** Current player just drew [cardId] and it's playable. They play it or pass. */
    data class DrewPlayable(val cardId: Int) : Phase

    /** Current player played a 7 and picks who to swap hands with. */
    data object PickSwapTarget : Phase

    data class RoundOver(val winner: Int, val points: Int) : Phase

    data class GameOver(val winner: Int) : Phase
}

/**
 * Full authoritative state. Seats are indexes into [players].
 * [drawPile] index 0 is the top. [discardPile] last element is the top.
 */
data class GameState(
    val rules: RuleSet,
    val players: List<Player>,
    val drawPile: List<Card>,
    val discardPile: List<Card>,
    /** Null only while a wild turned over at round start waits for a color. */
    val activeColor: CardColor?,
    val direction: Direction,
    val current: Int,
    val phase: Phase,
    val pendingDraw: Int = 0,
    val drawFourCheck: DrawFourCheck? = null,
    /** Player who's down to one card without calling it. Catchable until the next turn starts. */
    val exposedPlayer: Int? = null,
    val dealer: Int = 0,
    val round: Int = 1,
    val rngState: Long = 0,
) {
    val playerCount: Int get() = players.size
    val topCard: Card get() = discardPile.last()

    fun hand(seat: Int): List<Card> = players[seat].hand

    fun seatAfter(seat: Int, steps: Int = 1): Int = (seat + direction.step * steps).mod(players.size)
}
