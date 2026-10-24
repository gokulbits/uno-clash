package dev.colorclash.rules

import dev.colorclash.rules.Action.CallLastCard
import dev.colorclash.rules.Action.CatchLastCard
import dev.colorclash.rules.Action.ChallengeDrawFour
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Action.SwapWith
import kotlin.random.Random

/**
 * Attacks when an opponent is close to going out and picks the color it holds most.
 * [BotLevel.EASY] plays any legal card, never challenges and forgets UNO about half the time.
 */
class SimpleBot(
    private val random: Random = Random.Default,
    private val challengeChance: Double = 0.25,
    val level: BotLevel = BotLevel.NORMAL,
) {

    /** Null when the bot has nothing to do right now (not its turn, round over, etc.). */
    fun chooseAction(state: GameState, seat: Int): PlayerAction? {
        val legal = GameEngine.legalActions(state, seat)
        if (legal.isEmpty()) return null
        legal.firstOrNull { it is CatchLastCard }?.let { return it }
        legal.firstOrNull { it is CallLastCard }?.let { if (!forgetsUno(state, seat)) return it }
        return chooseTurnAction(state, seat, legal)
    }

    /** A timed-out turn: draw and keep it, picking a color or swap target if needed. Never plays a card. */
    fun chooseTimeoutAction(state: GameState, seat: Int): PlayerAction? {
        if (seat != state.current) return null
        return when (state.phase) {
            Phase.Turn -> Draw(seat)
            is Phase.DrewPlayable -> Action.Pass(seat)
            is Phase.PickColor -> ChooseColor(seat, favoriteColor(state.hand(seat)))
            Phase.PickSwapTarget -> SwapWith(seat, state.players.indices.filter { it != seat }.minBy { state.hand(it).size })
            else -> null
        }
    }

    /** An easy bot's UNO lapse. Fixed for a given table, so asking twice gives the same answer. */
    private fun forgetsUno(state: GameState, seat: Int): Boolean =
        level == BotLevel.EASY && (state.round * 31 + seat * 7 + state.discardPile.size) % 2 == 0

    /** A move that advances the seat's own turn; no calls or catches. */
    fun chooseTurnAction(state: GameState, seat: Int): PlayerAction? =
        chooseTurnAction(state, seat, GameEngine.legalActions(state, seat))

    private fun chooseTurnAction(state: GameState, seat: Int, legal: List<PlayerAction>): PlayerAction? {
        if (seat != state.current || legal.isEmpty()) return null
        val hand = state.hand(seat)
        return when (state.phase) {
            is Phase.PickColor -> ChooseColor(seat, if (level == BotLevel.EASY) randomColor() else favoriteColor(hand))
            Phase.PickSwapTarget -> {
                val target = state.players.indices.filter { it != seat }.minBy { state.hand(it).size }
                SwapWith(seat, target)
            }
            is Phase.DrewPlayable -> legal.first { it is PlayCard }
            Phase.Turn -> chooseTurn(state, seat, legal)
            else -> null
        }
    }

    private fun chooseTurn(state: GameState, seat: Int, legal: List<PlayerAction>): PlayerAction {
        val hand = state.hand(seat)
        val plays = legal.filterIsInstance<PlayCard>().map { play -> hand.first { it.id == play.cardId } }
        if (plays.isEmpty()) {
            val challenge = legal.firstOrNull { it is ChallengeDrawFour }.takeIf { level != BotLevel.EASY }
            return if (challenge != null && random.nextDouble() < challengeChance) challenge else Draw(seat)
        }
        if (level == BotLevel.EASY) return PlayCard(seat, plays[random.nextInt(plays.size)].id)
        // Drawing one beats a penalty for finishing on a power card.
        if (state.rules.powerFinishPenalty > 0 && hand.size == 1 && plays.all { it.face.number == null }) return Draw(seat)

        // Skip a +4 we'd lose a challenge on, unless it's the only move or we're stacking.
        val honest = if (state.pendingDraw > 0) plays else plays.filter {
            it.face != Face.WILD_DRAW_FOUR || hand.none { c -> c.color == state.activeColor }
        }
        val options = honest.ifEmpty { plays }
        val colorCounts = hand.mapNotNull { it.color }.groupingBy { it }.eachCount()
        val threatened = state.players.indices.any { it != seat && state.hand(it).size <= 2 }

        val pick = if (threatened) {
            options.maxWith(compareBy<Card>({ attackValue(it.face) }, { colorCounts[it.color] ?: 0 }))
        } else {
            options.minWith(compareBy<Card>({ keepValue(it.face) }, { -(colorCounts[it.color] ?: 0) }))
        }
        return PlayCard(seat, pick.id)
    }

    private fun attackValue(face: Face) = when (face) {
        Face.WILD_DRAW_FOUR -> 4
        Face.DRAW_TWO -> 3
        Face.SKIP -> 2
        Face.REVERSE -> 1
        else -> 0
    }

    // Lower plays first: dump numbers, hold actions, hold wilds longest.
    private fun keepValue(face: Face) = when {
        face.isWild -> 2
        face.number == null -> 1
        else -> 0
    }

    private fun favoriteColor(hand: List<Card>): CardColor {
        val counts = hand.mapNotNull { it.color }.groupingBy { it }.eachCount()
        return counts.maxByOrNull { it.value }?.key ?: randomColor()
    }

    private fun randomColor() = CardColor.entries[random.nextInt(CardColor.entries.size)]
}
