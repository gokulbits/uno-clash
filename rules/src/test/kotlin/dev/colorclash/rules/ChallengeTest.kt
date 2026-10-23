package dev.colorclash.rules

import dev.colorclash.rules.Action.ChallengeDrawFour
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeTest {
    private val t = Table()
    private val w4 = t.wild4()
    private fun others() = listOf(t.blue(THREE), t.green(TWO), t.yellow(ONE))

    /** Player 0 plays the +4 and picks green; player 1 is now on the hook. */
    private fun afterDrawFour(offenderHand: List<Card>): GameState {
        val s = t.state(offenderHand + w4, others(), others(), top = t.red(NINE))
        return s.act(PlayCard(0, w4.id)).state.act(ChooseColor(0, CardColor.GREEN)).state
    }

    @Test
    fun `a challengeable plus four waits for the victim to respond`() {
        val s = afterDrawFour(listOf(t.blue(ONE), t.blue(TWO)))
        assertEquals(1, s.current)
        assertEquals(4, s.pendingDraw)
        assertEquals(Phase.Turn, s.phase)
        // Nothing but drawing or challenging is allowed.
        s.rejects(PlayCard(1, s.hand(1).first { it.color == CardColor.GREEN }.id))
    }

    @Test
    fun `successful challenge makes the offender draw four and the challenger plays on`() {
        val s = afterDrawFour(listOf(t.red(FIVE), t.blue(TWO)))
        val (after, events) = s.act(ChallengeDrawFour(1))
        assertEquals(GameEvent.Challenged(1, 0, succeeded = true), events.first())
        assertEquals(6, after.hand(0).size)
        assertEquals(3, after.hand(1).size)
        assertEquals(1, after.current)
        assertEquals(0, after.pendingDraw)
        assertNull(after.drawFourCheck)
        assertEquals(CardColor.GREEN, after.activeColor)
    }

    @Test
    fun `failed challenge costs the challenger six cards and their turn`() {
        val s = afterDrawFour(listOf(t.blue(ONE), t.blue(TWO)))
        val (after, events) = s.act(ChallengeDrawFour(1))
        assertEquals(GameEvent.Challenged(1, 0, succeeded = false), events.first())
        assertEquals(9, after.hand(1).size)
        assertEquals(2, after.current)
    }

    @Test
    fun `matching number but not color is still a legal plus four`() {
        val s = afterDrawFour(listOf(t.blue(NINE), t.blue(TWO)))
        val (_, events) = s.act(ChallengeDrawFour(1))
        assertTrue(GameEvent.Challenged(1, 0, succeeded = false) in events)
    }

    @Test
    fun `accepting the plus four draws four and skips`() {
        val s = afterDrawFour(listOf(t.red(FIVE), t.blue(TWO)))
        val (after, _) = s.act(Draw(1))
        assertEquals(7, after.hand(1).size)
        assertEquals(2, after.current)
        assertNull(after.drawFourCheck)
    }

    @Test
    fun `only the victim can challenge`() {
        val s = afterDrawFour(listOf(t.red(FIVE), t.blue(TWO)))
        s.rejects(ChallengeDrawFour(2))
        s.rejects(ChallengeDrawFour(0))
    }

    @Test
    fun `nothing to challenge after a normal card`() {
        t.state(others(), others(), top = t.red(NINE)).rejects(ChallengeDrawFour(0))
    }
}
