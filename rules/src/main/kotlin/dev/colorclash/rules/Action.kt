package dev.colorclash.rules

sealed interface Action {
    data class PlayCard(override val player: Int, val cardId: Int) : PlayerAction
    data class ChooseColor(override val player: Int, val color: CardColor) : PlayerAction
    data class Draw(override val player: Int) : PlayerAction

    /** Keep a card you just drew instead of playing it. */
    data class Pass(override val player: Int) : PlayerAction
    data class CallLastCard(override val player: Int) : PlayerAction
    data class CatchLastCard(override val player: Int, val target: Int) : PlayerAction
    data class ChallengeDrawFour(override val player: Int) : PlayerAction
    data class SwapWith(override val player: Int, val target: Int) : PlayerAction

    /** Host-only. */
    data object StartNextRound : Action
}

sealed interface PlayerAction : Action {
    val player: Int
}
