package dev.colorclash.app.ui.game

import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.Direction
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.Face
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What the table should offer right now. Pure, so it's tested without a device. */
data class TableControls(
    val headline: String,
    val canDraw: Boolean,
    val drawLabel: String,
    val canKeep: Boolean,
    val canChallenge: Boolean,
    val canCallLastCard: Boolean,
    /** Pulse the button: you're about to be down to one card. */
    val urgeLastCard: Boolean,
    val catchTarget: Int?,
    val pickingColor: Boolean,
    val pickingSwapTarget: Boolean,
)

fun tableControls(view: PlayerView): TableControls {
    val yourTurn = view.isYourTurn
    val current = view.nameOf(view.current)
    val headline = when (view.phase) {
        PhaseKind.GAME_OVER -> view.winner?.let { if (it == view.you) "You win the game!" else "${view.nameOf(it)} wins the game!" } ?: "Game over"
        PhaseKind.ROUND_OVER -> view.winner?.let { if (it == view.you) "You won the round!" else "${view.nameOf(it)} won the round" } ?: "Round over"
        PhaseKind.PICK_COLOR -> if (yourTurn) "Pick a color" else "$current is picking a color"
        PhaseKind.PICK_SWAP_TARGET -> if (yourTurn) "Pick someone to swap hands with" else "$current is choosing who to swap with"
        PhaseKind.DREW_PLAYABLE -> if (yourTurn) "Play the card you drew, or keep it" else "$current's turn"
        PhaseKind.TURN -> when {
            yourTurn && view.canChallenge -> "Wild +4 on you! Challenge it or take ${view.pendingDraw}"
            yourTurn && view.pendingDraw > 0 -> "Stack a draw card or take ${view.pendingDraw}"
            yourTurn -> "Your turn"
            else -> "$current's turn"
        }
    }
    return TableControls(
        headline = headline,
        canDraw = view.canDraw,
        drawLabel = if (view.pendingDraw > 0) "Take ${view.pendingDraw}" else "Draw",
        canKeep = view.canPass,
        canChallenge = view.canChallenge,
        canCallLastCard = view.canCallLastCard,
        urgeLastCard = view.canCallLastCard && view.hand.size <= 2,
        catchTarget = view.exposedPlayer?.takeIf { it != view.you },
        pickingColor = yourTurn && view.phase == PhaseKind.PICK_COLOR,
        pickingSwapTarget = yourTurn && view.phase == PhaseKind.PICK_SWAP_TARGET,
    )
}

/** Opponents left to right as they sit around the table, starting with whoever plays after you clockwise. */
fun opponentOrder(view: PlayerView): List<Int> {
    val n = view.seats.size
    return (1 until n).map { (view.you + it) % n }
}

/**
 * Where to put [count] opponents on an arc across the top of the table, as fractions of the
 * available width and height (x left to right, y top to bottom).
 */
fun arcPositions(count: Int): List<Pair<Float, Float>> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(0.5f to 0.15f)
    val start = 160.0
    val end = 20.0
    return List(count) { i ->
        val degrees = start - i * (start - end) / (count - 1)
        val radians = degrees * PI / 180
        val x = 0.5 + 0.39 * cos(radians)
        val y = 0.95 - 0.8 * sin(radians)
        x.toFloat() to y.toFloat()
    }
}

/** Groups the hand by color, then by face, so it's easy to scan. */
fun sortHand(hand: List<Card>): List<Card> =
    hand.sortedWith(compareBy<Card>({ it.color?.ordinal ?: Int.MAX_VALUE }, { it.face.ordinal }))

/** One line of table chatter for an event, or null for events not worth a line. */
fun describe(event: EventView, view: PlayerView): String? {
    fun who(seat: Int) = if (seat == view.you) "You" else view.nameOf(seat)
    return when (event) {
        is EventView.RoundStarted -> "Round ${event.round} — ${who(event.dealer)} dealt"
        is EventView.CardPlayed -> "${who(event.player)} ${if (event.jumpIn) "jumped in with" else "played"} ${event.card.label()}"
        is EventView.Drew -> when (event.reason) {
            DrawReason.TURN -> "${who(event.player)} drew ${plural(event.count, "card")}"
            DrawReason.DRAW_CARD -> "${who(event.player)} took ${plural(event.count, "card")}"
            DrawReason.MISSED_LAST_CARD -> null // covered by the penalty line
            DrawReason.CHALLENGE -> "${who(event.player)} took ${plural(event.count, "card")} from the challenge"
        }
        is EventView.Skipped -> "${who(event.player)} ${if (event.player == view.you) "are" else "is"} skipped"
        is EventView.Reversed -> if (event.direction == Direction.CLOCKWISE) "Play goes clockwise" else "Play goes counter-clockwise"
        is EventView.WildColorChosen -> "${who(event.player)} picked ${event.color.name.lowercase()}"
        is EventView.LastCardCalled -> "${who(event.player)} called UNO!"
        is EventView.PenaltyApplied -> "${who(event.caughtBy)} caught ${who(event.player).let { if (it == "You") "you" else it }}! +${event.cards}"
        is EventView.Challenged -> if (event.succeeded) {
            "${who(event.challenger)} caught ${who(event.offender)} bluffing a +4"
        } else {
            "${who(event.challenger)} challenged and lost"
        }
        is EventView.HandsSwapped -> "${who(event.player)} swapped hands with ${who(event.target)}"
        is EventView.HandsRotated -> "Everyone passed their hand along"
        is EventView.DeckReshuffled -> "Discard pile shuffled into the deck"
        is EventView.RoundWon -> "${who(event.player)} won the round for ${event.points} points"
        is EventView.GameWon -> "${who(event.player)} won the game!"
        is EventView.Dealt, is EventView.StarterFlipped -> null
    }
}

fun Card.label(): String {
    val face = when (face) {
        Face.SKIP -> "Skip"
        Face.REVERSE -> "Reverse"
        Face.DRAW_TWO -> "+2"
        Face.WILD -> "Wild"
        Face.WILD_DRAW_FOUR -> "Wild +4"
        else -> face.number.toString()
    }
    return color?.let { "${it.name.lowercase()} $face" } ?: face
}

fun PlayerView.nameOf(seat: Int): String = seats.getOrNull(seat)?.info?.name ?: "Someone"

private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"
