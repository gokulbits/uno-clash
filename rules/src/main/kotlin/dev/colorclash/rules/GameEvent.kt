package dev.colorclash.rules

enum class DrawReason { TURN, DRAW_CARD, MISSED_LAST_CARD, CHALLENGE, POWER_FINISH, ROULETTE }

/**
 * Emitted by the engine in the order things happened. Some carry cards only the owner should see
 * ([Dealt], [Drew]); the host must redact those before sending them to anyone else.
 */
sealed interface GameEvent {
    data class RoundStarted(val round: Int, val dealer: Int) : GameEvent
    data class Dealt(val player: Int, val cards: List<Card>) : GameEvent
    data class StarterFlipped(val card: Card) : GameEvent
    data class CardPlayed(val player: Int, val card: Card, val jumpIn: Boolean = false) : GameEvent
    data class Drew(val player: Int, val cards: List<Card>, val reason: DrawReason) : GameEvent
    data class Skipped(val player: Int) : GameEvent

    /** A Skip Everyone: [player] goes again. */
    data class EveryoneSkipped(val player: Int) : GameEvent
    data class Reversed(val direction: Direction) : GameEvent

    /** A Discard All: [cards] went onto the pile under it. */
    data class DiscardedAll(val player: Int, val color: CardColor, val cards: List<Card>) : GameEvent

    /** A Color Roulette: [player] named [color] and is about to be dealt everything turned over. */
    data class ColorRoulette(val player: Int, val color: CardColor, val cards: Int) : GameEvent

    /** The mercy rule: [player] is out of the round holding [cards] cards. */
    data class KnockedOut(val player: Int, val cards: Int) : GameEvent
    data class WildColorChosen(val player: Int, val color: CardColor) : GameEvent
    data class LastCardCalled(val player: Int) : GameEvent
    data class PenaltyApplied(val player: Int, val caughtBy: Int, val cards: Int) : GameEvent
    data class Challenged(val challenger: Int, val offender: Int, val succeeded: Boolean) : GameEvent
    data class HandsSwapped(val player: Int, val target: Int) : GameEvent
    data class HandsRotated(val direction: Direction) : GameEvent
    data class DeckReshuffled(val cards: Int) : GameEvent
    data class RoundWon(val player: Int, val points: Int, val totalScore: Int) : GameEvent
    data class GameWon(val player: Int) : GameEvent
}

sealed interface EngineResult {
    data class Ok(val state: GameState, val events: List<GameEvent>) : EngineResult
    data class Rejected(val reason: String) : EngineResult
}
