package dev.colorclash.app.ui.game

import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
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

/** Where [count] opponents sit on an arc across the top, as fractions of width and height. */
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

/** By color then face, or face first when [byNumber]. Wilds go last either way. */
fun sortHand(hand: List<Card>, byNumber: Boolean = false): List<Card> {
    val color = compareBy<Card> { it.color?.ordinal ?: Int.MAX_VALUE }
    val face = compareBy<Card> { it.face.ordinal }
    return hand.sortedWith(if (byNumber) face.then(color) else color.then(face))
}

data class RecentPlay(val seat: Int, val card: Card)

/** Who plays after the current player, if the round is still going. */
fun nextSeat(view: PlayerView): Int? {
    if (view.phase == PhaseKind.ROUND_OVER || view.phase == PhaseKind.GAME_OVER) return null
    return (view.current + view.direction.step).mod(view.seats.size)
}

enum class StampMark { SKIP, DRAW, CHECK, CROSS }

/** [good] stamps are green and bounce the seat; bad ones are red and knock it about. */
data class SeatStamp(val seat: Int, val mark: StampMark, val tag: String, val good: Boolean, val count: Int = 0)

/** The stamps an event puts on players' seats, worded for the player at [you]. */
fun seatStampsFor(event: EventView, you: Int): List<SeatStamp> = when (event) {
    is EventView.Skipped -> listOf(
        SeatStamp(event.player, StampMark.SKIP, if (event.player == you) "YOU'RE SKIPPED!" else "SKIPPED!", good = false),
    )
    // A +2 or +4 landing on you, or the penalty for finishing on a power card. A challenge's own
    // stamps already cover those cards, and an UNO penalty has its own flash.
    is EventView.Drew -> when {
        event.count <= 0 -> emptyList()
        event.reason == DrawReason.DRAW_CARD -> {
            val tag = if (event.player == you) "YOU DRAW ${event.count}!" else "DRAW ${event.count}!"
            listOf(SeatStamp(event.player, StampMark.DRAW, tag, good = false, count = event.count))
        }
        event.reason == DrawReason.POWER_FINISH -> {
            val tag = if (event.player == you) "NO POWER FINISH!" else "POWER FINISH!"
            listOf(SeatStamp(event.player, StampMark.DRAW, tag, good = false, count = event.count))
        }
        else -> emptyList()
    }
    is EventView.Challenged -> {
        val challengerYou = event.challenger == you
        val offenderYou = event.offender == you
        if (event.succeeded) {
            listOf(
                SeatStamp(event.challenger, StampMark.CHECK, if (challengerYou) "YOU CAUGHT THEM!" else "BLUFF CAUGHT!", good = true),
                SeatStamp(event.offender, StampMark.CROSS, if (offenderYou) "YOU GOT CAUGHT!" else "BLUFFED!", good = false),
            )
        } else {
            listOf(
                SeatStamp(event.offender, StampMark.CHECK, if (offenderYou) "IT WAS LEGIT!" else "LEGIT!", good = true),
                SeatStamp(event.challenger, StampMark.CROSS, if (challengerYou) "BAD CHALLENGE!" else "WRONG CALL!", good = false),
            )
        }
    }
    else -> emptyList()
}

/** A table-wide callout: an action card landing, a color picked, an UNO call. */
data class Announcement(val title: String, val detail: String, val color: CardColor?, val spoken: String)

fun announcementFor(event: EventView, view: PlayerView): Announcement? {
    fun who(seat: Int) = view.who(seat)
    return when (event) {
        is EventView.CardPlayed -> {
            val (title, spoken, name) = when (event.card.face) {
                Face.SKIP -> Triple("SKIP!", "Skip!", "a Skip")
                Face.REVERSE -> Triple("REVERSE!", "Reverse!", "a Reverse")
                Face.DRAW_TWO -> Triple("+2", "Draw two!", "a Draw Two")
                Face.WILD -> Triple("WILD!", "Wild!", "a Wild")
                Face.WILD_DRAW_FOUR -> Triple("+4", "Wild draw four!", "a Wild Draw Four")
                else -> return null
            }
            Announcement(title, "${who(event.player)} played $name", event.card.color, spoken)
        }
        is EventView.WildColorChosen -> {
            val name = event.color.name.lowercase()
            Announcement("${event.color.name}!", "${who(event.player)} picked $name", event.color, name.replaceFirstChar { it.uppercase() } + "!")
        }
        is EventView.LastCardCalled -> Announcement("UNO!", "${who(event.player)} called UNO", null, "UNO!")
        else -> null
    }
}

/** One line of table chatter for an event, or null for events not worth a line. */
fun describe(event: EventView, view: PlayerView): String? {
    fun who(seat: Int) = view.who(seat)
    return when (event) {
        is EventView.RoundStarted -> "Round ${event.round} — ${who(event.dealer)} dealt"
        is EventView.CardPlayed -> "${who(event.player)} ${if (event.jumpIn) "jumped in with" else "played"} ${event.card.label()}"
        is EventView.Drew -> when (event.reason) {
            DrawReason.TURN -> "${who(event.player)} drew ${plural(event.count, "card")}"
            DrawReason.DRAW_CARD -> "${who(event.player)} took ${plural(event.count, "card")}"
            DrawReason.MISSED_LAST_CARD -> null // covered by the penalty line
            DrawReason.CHALLENGE -> "${who(event.player)} took ${plural(event.count, "card")} from the challenge"
            DrawReason.POWER_FINISH -> "${who(event.player)} can't go out on a power card: +${event.count}"
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

private fun PlayerView.who(seat: Int) = if (seat == you) "You" else nameOf(seat)

private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"
