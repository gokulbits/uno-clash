package dev.colorclash.rules

import kotlinx.serialization.Serializable

@Serializable
data class RuleSet(
    val handSize: Int = 7,
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
) {
    init {
        require(handSize in 1..15) { "Hand size must be 1-15" }
        require(targetScore > 0) { "Target score must be positive" }
        require(lastCardPenalty >= 0) { "Penalty can't be negative" }
    }
}
