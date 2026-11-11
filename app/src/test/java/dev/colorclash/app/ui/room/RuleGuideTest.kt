package dev.colorclash.app.ui.room

import dev.colorclash.protocol.GameSettings
import dev.colorclash.rules.Face
import dev.colorclash.rules.GameMode
import dev.colorclash.rules.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleGuideTest {
    private fun RuleGuide.faces() = cards.map { it.card.face }
    private fun RuleGuide.all() = (basics + special + winning).joinToString(" ") { "${it.title} ${it.text}" } +
        cards.joinToString(" ") { "${it.name} ${it.text}" }

    @Test
    fun `plain classic explains the five power cards and nothing it isn't using`() {
        val guide = ruleGuide(GameSettings(RuleSet(challengeDrawFour = false), turnTimerSeconds = null))
        assertEquals("Classic", guide.mode)
        assertEquals(listOf(Face.SKIP, Face.REVERSE, Face.DRAW_TWO, Face.WILD, Face.WILD_DRAW_FOUR), guide.faces())
        assertTrue(guide.onRules.isEmpty())
        assertTrue(guide.special.isEmpty())
        assertEquals("Can't play? Draw one", guide.basics[1].title)
        assertFalse("stack" in guide.all().lowercase())
        assertFalse("timer" in guide.all().lowercase())
    }

    @Test
    fun `house rules show up as tags and get explained`() {
        val rules = RuleSet(stackDraws = true, jumpIn = true, sevenZero = true, drawUntilPlayable = true, powerFinishPenalty = 4, targetScore = 250)
        val guide = ruleGuide(GameSettings(rules, turnTimerSeconds = 30))
        assertEquals(listOf("Stack draws", "Jump in", "7-0", "Draw till playable", "Challenge +4", "No power finish", "30s turns"), guide.onRules)
        assertEquals(listOf("Stack draws", "Jump in", "Challenge a +4", "No power finish"), guide.special.map { it.title })
        assertTrue(Face.SEVEN in guide.faces() && Face.ZERO in guide.faces())
        assertEquals("Can't play? Keep drawing", guide.basics[1].title)
        assertTrue("draw 4 and play on" in guide.special.last().text)
        assertTrue("250 points" in guide.all())
        assertTrue("30 seconds" in guide.winning.last().text)
        assertTrue("unless they stack" in guide.cards.first { it.card.face == Face.DRAW_TWO }.text)
    }

    @Test
    fun `no mercy brings its own deck and forced rules`() {
        val guide = ruleGuide(GameSettings(RuleSet(mode = GameMode.NO_MERCY, targetScore = 1000)))
        assertEquals("No Mercy", guide.mode)
        assertEquals(
            listOf(
                Face.SKIP, Face.REVERSE, Face.DRAW_TWO, Face.DRAW_FOUR, Face.SKIP_EVERYONE, Face.DISCARD_ALL, Face.WILD,
                Face.WILD_REVERSE_DRAW_FOUR, Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN, Face.WILD_COLOR_ROULETTE, Face.SEVEN, Face.ZERO,
            ),
            guide.faces(),
        )
        // No Wild +4 in this deck, so no challenge, and you can win on anything.
        assertFalse("Challenge +4" in guide.onRules)
        assertFalse("No power finish" in guide.onRules)
        assertEquals(listOf("Stacking", "Mercy rule"), guide.special.map { it.title })
        assertTrue("25 cards" in guide.special.last().text)
        assertTrue("250 for every player knocked out" in guide.winning.first().text)
        assertTrue("have to play it" in guide.basics[1].text)
    }

    @Test
    fun `flip explains the two sides and only the cards its deck has`() {
        val guide = ruleGuide(GameSettings(RuleSet(mode = GameMode.FLIP), turnTimerSeconds = null))
        assertEquals("Flip", guide.mode)
        assertEquals(
            listOf(
                Face.SKIP, Face.REVERSE, Face.DRAW_ONE, Face.DRAW_FIVE, Face.SKIP_EVERYONE, Face.FLIP,
                Face.WILD, Face.WILD_DRAW_TWO, Face.WILD_DRAW_COLOR,
            ),
            guide.faces(),
        )
        assertEquals("Every card has two sides", guide.basics[0].title)
        assertEquals(listOf("Flipping over", "Reading the table", "Challenge a wild draw"), guide.special.map { it.title })
        assertTrue("Challenge wild" in guide.onRules)
        assertTrue("Wild Draw Color 60" in guide.winning.first().text)
        // Nothing from the other decks: no +2, no stacking, no mercy rule.
        assertFalse("stack" in guide.all().lowercase())
        assertFalse("mercy" in guide.all().lowercase())
    }

    @Test
    fun `a single round says so`() {
        assertEquals("Just one round: whoever goes out first wins.", ruleGuide(GameSettings(RuleSet(targetScore = 0))).winning[1].text)
    }
}
