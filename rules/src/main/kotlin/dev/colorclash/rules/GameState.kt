package dev.colorclash.rules

enum class Direction(val step: Int) {
    CLOCKWISE(1), COUNTER_CLOCKWISE(-1);

    fun reversed() = if (this == CLOCKWISE) COUNTER_CLOCKWISE else CLOCKWISE
}

data class Player(
    val hand: List<Card>,
    val score: Int = 0,
    val calledLastCard: Boolean = false,
    /** No Mercy: hit the mercy limit and you're out until the next round. */
    val knockedOut: Boolean = false,
)

/** Recorded when a Wild Draw 4 is played, so a challenge can be judged later. */
data class DrawFourCheck(val offender: Int, val guilty: Boolean)

sealed interface Phase {
    /** Current player plays or draws. With a pending draw they must stack, accept it or challenge. */
    data object Turn : Phase

    /**
     * Current player picks a color for the wild on top. [keepTurn] is for a wild turned over at
     * round start; [roulette] means they then reveal cards until that color turns up; [afterFlip]
     * is a wild left on top by a Flip, where naming the color is all that happens, and [bounced]
     * means that Flip threw a stack onto the next player, who then misses their turn.
     */
    data class PickColor(
        val keepTurn: Boolean = false,
        val roulette: Boolean = false,
        val afterFlip: Boolean = false,
        val bounced: Boolean = false,
    ) : Phase

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
    /** Which side of a Flip deck is in play. Always [Side.LIGHT] in the other modes. */
    val side: Side = Side.LIGHT,
    val direction: Direction,
    val current: Int,
    val phase: Phase,
    val pendingDraw: Int = 0,
    /** Value of the last draw card played, which a stacked card has to match or beat. */
    val pendingDrawValue: Int = 0,
    val drawFourCheck: DrawFourCheck? = null,
    /** Player who's down to one card without calling it. Catchable until the next turn starts. */
    val exposedPlayer: Int? = null,
    /** Knocked-out hands, back in the draw pile at the next reshuffle. */
    val setAside: List<Card> = emptyList(),
    /** Players knocked out this round, for the round winner's bonus. */
    val knockouts: Int = 0,
    val dealer: Int = 0,
    val round: Int = 1,
    val rngState: Long = 0,
) {
    val playerCount: Int get() = players.size
    val topCard: Card get() = discardPile.last()

    /** Seats still in the round. */
    val activeSeats: List<Int> get() = players.indices.filter { !players[it].knockedOut }

    fun hand(seat: Int): List<Card> = players[seat].hand

    /** Whether a draw card waits for the next player to answer it, which in Flip depends on the side. */
    val stacking: Boolean get() = rules.stackingOn(side)

    /** Steps round the table [steps] times, passing over anyone knocked out. */
    fun seatAfter(seat: Int, steps: Int = 1): Int =
        nextSeat(seat, steps, direction, playerCount) { players[it].knockedOut }
}

/** Shared by [GameState] and the engine's scratch copy, which both have to skip knocked-out seats. */
internal inline fun nextSeat(seat: Int, steps: Int, direction: Direction, count: Int, isOut: (Int) -> Boolean): Int {
    var at = seat
    repeat(steps) {
        var next = (at + direction.step).mod(count)
        // Lands back on the seat we started from if everyone else is out; the round ends then anyway.
        var hops = 0
        while (isOut(next) && hops < count) {
            next = (next + direction.step).mod(count)
            hops++
        }
        at = next
    }
    return at
}
