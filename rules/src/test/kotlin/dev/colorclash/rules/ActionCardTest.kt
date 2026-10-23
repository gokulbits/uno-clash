package dev.colorclash.rules

import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.DRAW_TWO
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.REVERSE
import dev.colorclash.rules.Face.SKIP
import dev.colorclash.rules.Face.TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionCardTest {
    private val t = Table()
    private fun spare() = listOf(t.blue(ONE), t.blue(TWO))

    @Test
    fun `skip jumps over the next player`() {
        val skip = t.red(SKIP)
        val s = t.state(spare() + skip, spare(), spare(), top = t.red(NINE))
        val (after, events) = s.act(PlayCard(0, skip.id))
        assertEquals(2, after.current)
        assertTrue(GameEvent.Skipped(1) in events)
    }

    @Test
    fun `skip matches another skip of any color`() {
        val skip = t.blue(SKIP)
        t.state(spare() + skip, spare(), top = t.red(SKIP)).act(PlayCard(0, skip.id))
    }

    @Test
    fun `reverse flips direction with three or more players`() {
        val rev = t.red(REVERSE)
        val s = t.state(spare(), spare() + rev, spare(), spare(), top = t.red(NINE), current = 1)
        val (after, events) = s.act(PlayCard(1, rev.id))
        assertEquals(Direction.COUNTER_CLOCKWISE, after.direction)
        assertEquals(0, after.current)
        assertTrue(GameEvent.Reversed(Direction.COUNTER_CLOCKWISE) in events)
    }

    @Test
    fun `reverse acts as skip with two players`() {
        val rev = t.red(REVERSE)
        val s = t.state(spare() + rev, spare(), top = t.red(NINE))
        val (after, events) = s.act(PlayCard(0, rev.id))
        assertEquals(0, after.current)
        assertEquals(Direction.CLOCKWISE, after.direction)
        assertTrue(GameEvent.Skipped(1) in events)
    }

    @Test
    fun `skip with two players gives the same player another turn`() {
        val skip = t.red(SKIP)
        val (after, _) = t.state(spare() + skip, spare(), top = t.red(NINE)).act(PlayCard(0, skip.id))
        assertEquals(0, after.current)
    }

    @Test
    fun `draw two makes the next player draw and lose their turn`() {
        val d2 = t.red(DRAW_TWO)
        val pile = t.filler(5)
        val s = t.state(spare() + d2, spare(), spare(), top = t.red(NINE), drawPile = pile)
        val (after, events) = s.act(PlayCard(0, d2.id))
        assertEquals(4, after.hand(1).size)
        assertEquals(2, after.current)
        assertEquals(0, after.pendingDraw)
        assertTrue(GameEvent.Drew(1, pile.take(2), DrawReason.DRAW_CARD) in events)
    }

    @Test
    fun `wild draw four without challenge rule hits the next player after the color pick`() {
        val w4 = t.wild4()
        val s = t.state(spare() + w4, spare(), spare(), top = t.red(NINE), rules = RuleSet(challengeDrawFour = false))
        val (picking, _) = s.act(PlayCard(0, w4.id))
        assertEquals(Phase.PickColor(), picking.phase)
        val (after, _) = picking.act(ChooseColor(0, CardColor.YELLOW))
        assertEquals(6, after.hand(1).size)
        assertEquals(2, after.current)
        assertEquals(CardColor.YELLOW, after.activeColor)
    }

    @Test
    fun `counter clockwise skip wraps around`() {
        val skip = t.red(SKIP)
        val s = t.state(spare() + skip, spare(), spare(), spare(), top = t.red(NINE), direction = Direction.COUNTER_CLOCKWISE)
        val (after, events) = s.act(PlayCard(0, skip.id))
        assertEquals(2, after.current)
        assertTrue(GameEvent.Skipped(3) in events)
    }
}
