package dev.colorclash.protocol

import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Direction
import dev.colorclash.rules.Side
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.GameEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Engine events as one player sees them. Cards dealt or drawn are only included for their owner. */
@Serializable
sealed interface EventView {
    @Serializable @SerialName("roundStarted")
    data class RoundStarted(val round: Int, val dealer: Int) : EventView

    @Serializable @SerialName("dealt")
    data class Dealt(val player: Int, val count: Int, val cards: List<Card>? = null) : EventView

    @Serializable @SerialName("starter")
    data class StarterFlipped(val card: Card) : EventView

    @Serializable @SerialName("played")
    data class CardPlayed(val player: Int, val card: Card, val jumpIn: Boolean = false) : EventView

    @Serializable @SerialName("drew")
    data class Drew(val player: Int, val count: Int, val reason: DrawReason, val cards: List<Card>? = null) : EventView

    @Serializable @SerialName("skipped")
    data class Skipped(val player: Int) : EventView

    @Serializable @SerialName("skippedAll")
    data class EveryoneSkipped(val player: Int) : EventView

    @Serializable @SerialName("reversed")
    data class Reversed(val direction: Direction) : EventView

    @Serializable @SerialName("discardedAll")
    data class DiscardedAll(val player: Int, val color: CardColor, val cards: List<Card>) : EventView

    @Serializable @SerialName("roulette")
    data class ColorRoulette(val player: Int, val color: CardColor, val cards: Int) : EventView

    @Serializable @SerialName("knockedOut")
    data class KnockedOut(val player: Int, val cards: Int) : EventView

    @Serializable @SerialName("colorChosen")
    data class WildColorChosen(val player: Int, val color: CardColor) : EventView

    @Serializable @SerialName("lastCard")
    data class LastCardCalled(val player: Int) : EventView

    @Serializable @SerialName("penalty")
    data class PenaltyApplied(val player: Int, val caughtBy: Int, val cards: Int) : EventView

    @Serializable @SerialName("challenged")
    data class Challenged(val challenger: Int, val offender: Int, val succeeded: Boolean) : EventView

    @Serializable @SerialName("swapped")
    data class HandsSwapped(val player: Int, val target: Int) : EventView

    @Serializable @SerialName("rotated")
    data class HandsRotated(val direction: Direction) : EventView

    @Serializable @SerialName("reshuffled")
    data class DeckReshuffled(val cards: Int) : EventView

    @Serializable @SerialName("roundWon")
    data class RoundWon(val player: Int, val points: Int, val totalScore: Int) : EventView

    @Serializable @SerialName("gameWon")
    data class GameWon(val player: Int) : EventView

    /** Flip: the table turned over, and [top] is what everyone has to match now. [bounced] is a stack it threw on. */
    @Serializable @SerialName("sideFlipped")
    data class SideFlipped(val side: Side, val top: Card, val bounced: Int = 0) : EventView
}

fun GameEvent.toView(viewer: Int): EventView = when (this) {
    is GameEvent.RoundStarted -> EventView.RoundStarted(round, dealer)
    is GameEvent.Dealt -> EventView.Dealt(player, cards.size, cards.shown().takeIf { player == viewer })
    is GameEvent.StarterFlipped -> EventView.StarterFlipped(card.shown())
    is GameEvent.SideFlipped -> EventView.SideFlipped(side, top.shown(), bounced)
    is GameEvent.CardPlayed -> EventView.CardPlayed(player, card.shown(), jumpIn)
    is GameEvent.Drew -> EventView.Drew(player, cards.size, reason, cards.shown().takeIf { player == viewer })
    is GameEvent.Skipped -> EventView.Skipped(player)
    is GameEvent.EveryoneSkipped -> EventView.EveryoneSkipped(player)
    is GameEvent.Reversed -> EventView.Reversed(direction)
    is GameEvent.DiscardedAll -> EventView.DiscardedAll(player, color, cards.shown())
    is GameEvent.ColorRoulette -> EventView.ColorRoulette(player, color, cards)
    is GameEvent.KnockedOut -> EventView.KnockedOut(player, cards)
    is GameEvent.WildColorChosen -> EventView.WildColorChosen(player, color)
    is GameEvent.LastCardCalled -> EventView.LastCardCalled(player)
    is GameEvent.PenaltyApplied -> EventView.PenaltyApplied(player, caughtBy, cards)
    is GameEvent.Challenged -> EventView.Challenged(challenger, offender, succeeded)
    is GameEvent.HandsSwapped -> EventView.HandsSwapped(player, target)
    is GameEvent.HandsRotated -> EventView.HandsRotated(direction)
    is GameEvent.DeckReshuffled -> EventView.DeckReshuffled(cards)
    is GameEvent.RoundWon -> EventView.RoundWon(player, points, totalScore)
    is GameEvent.GameWon -> EventView.GameWon(player)
}
