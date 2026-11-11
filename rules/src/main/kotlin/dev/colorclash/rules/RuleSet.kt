package dev.colorclash.rules

import kotlinx.serialization.Serializable

@Serializable
enum class BotLevel { EASY, NORMAL }

@Serializable
enum class GameMode { CLASSIC, NO_MERCY, FLIP }

@Serializable
data class RuleSet(
    /** Which deck and which rules. No Mercy forces most of the toggles below. */
    val mode: GameMode = GameMode.CLASSIC,
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
    /** Adds the printed Wild Swap Hands card: pick a color, then take someone's hand. */
    val swapHandsWild: Boolean = false,
    val drawUntilPlayable: Boolean = false,
    /**
     * Cards drawn for playing a Skip, Reverse, +2 or wild as your last card. The card still
     * works, but you don't go out. 0 turns the rule off.
     */
    val powerFinishPenalty: Int = 0,
    /** No Mercy: holding this many cards knocks you out of the round. */
    val mercyLimit: Int = 25,
    /** No Mercy: what the round winner scores for each player knocked out that round. */
    val knockoutBonus: Int = 250,
) {
    val noMercy: Boolean get() = mode == GameMode.NO_MERCY

    val flip: Boolean get() = mode == GameMode.FLIP

    /** No Mercy stacks any draw card of equal or higher value, so it's always on. Flip has no stacking at all. */
    val stacking: Boolean get() = !flip && (noMercy || stackDraws)

    /** The 7 swap and the 0 pass-along are compulsory in No Mercy. A Flip deck has no zeros to pass on. */
    val swaps: Boolean get() = !flip && (noMercy || sevenZero)

    /** The other modes bring their own decks, so the extra wild only goes in a Classic one. */
    val swapHands: Boolean get() = mode == GameMode.CLASSIC && swapHandsWild

    val drawsUntilPlayable: Boolean get() = noMercy || (!flip && drawUntilPlayable)

    /** No Mercy has no Wild +4, so there's nothing to challenge. Flip challenges its own two wilds. */
    val canChallenge: Boolean get() = !noMercy && challengeDrawFour

    /** No Mercy lets you win on any card. */
    val powerFinish: Int get() = if (noMercy) 0 else powerFinishPenalty

    init {
        require(handSize in 1..15) { "Hand size must be 1-15" }
        require(targetScore >= 0) { "Target score can't be negative" }
        require(lastCardPenalty >= 0) { "Penalty can't be negative" }
        require(powerFinishPenalty >= 0) { "Penalty can't be negative" }
        require(mercyLimit > handSize) { "The mercy limit has to be above the hand size" }
        require(knockoutBonus >= 0) { "Bonus can't be negative" }
    }
}
