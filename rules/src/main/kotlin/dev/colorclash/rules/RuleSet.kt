package dev.colorclash.rules

import kotlinx.serialization.Serializable

@Serializable
enum class BotLevel { EASY, NORMAL }

@Serializable
data class RuleSet(
    val handSize: Int = 7,
    /** First to this many points wins the game. 0 means a single round. */
    val targetScore: Int = 500,
    val lastCardPenalty: Int = 2,
    val challengeDrawFour: Boolean = true,
    /** +2 on +2, +4 on +4 and +4 on +2. A +2 can't go on a +4. */
    val stackDraws: Boolean = false,
    /** Play an identical card (same color and face) out of turn. Not for wilds. */
    val jumpIn: Boolean = false,
    /** 7 swaps hands with a chosen player, 0 passes every hand along the direction of play. */
    val sevenZero: Boolean = false,
    val drawUntilPlayable: Boolean = false,
    /**
     * Cards drawn for playing a Skip, Reverse, +2 or wild as your last card. The card still
     * works, but you don't go out. 0 turns the rule off.
     */
    val powerFinishPenalty: Int = 0,
) {
    init {
        require(handSize in 1..15) { "Hand size must be 1-15" }
        require(targetScore >= 0) { "Target score can't be negative" }
        require(lastCardPenalty >= 0) { "Penalty can't be negative" }
        require(powerFinishPenalty >= 0) { "Penalty can't be negative" }
    }
}
