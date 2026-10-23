package dev.colorclash.rules

import dev.colorclash.rules.Action.ChallengeDrawFour
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Action.SwapWith
import dev.colorclash.rules.Face.DRAW_TWO
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.SEVEN
import dev.colorclash.rules.Face.SIX
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import dev.colorclash.rules.Face.ZERO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HouseRulesTest {
    private val t = Table()
    private fun spare() = listOf(t.blue(ONE), t.blue(THREE))

    // Stacking

    private val stacking = RuleSet(stackDraws = true)

    @Test
    fun `plus two stacks on plus two and the next player takes the lot`() {
        val red2 = t.red(DRAW_TWO)
        val blue2 = t.blue(DRAW_TWO)
        val s = t.state(spare() + red2, spare() + blue2, spare(), top = t.red(NINE), rules = stacking)
        val (pending, _) = s.act(PlayCard(0, red2.id))
        assertEquals(1, pending.current)
        assertEquals(2, pending.pendingDraw)
        assertEquals(3, pending.hand(1).size)

        val (stacked, _) = pending.act(PlayCard(1, blue2.id))
        assertEquals(2, stacked.current)
        assertEquals(4, stacked.pendingDraw)

        val (after, _) = stacked.act(Draw(2))
        assertEquals(6, after.hand(2).size)
        assertEquals(0, after.current)
        assertEquals(0, after.pendingDraw)
    }

    @Test
    fun `plus four stacks on plus two`() {
        val red2 = t.red(DRAW_TWO)
        val w4 = t.wild4()
        val s = t.state(spare() + red2, spare() + w4, spare(), top = t.red(NINE), rules = stacking)
        val (picking, _) = s.act(PlayCard(0, red2.id)).state.act(PlayCard(1, w4.id))
        assertEquals(6, picking.pendingDraw)
        val (after, _) = picking.act(ChooseColor(1, CardColor.GREEN))
        assertEquals(2, after.current)
        // A stacked +4 can't be challenged.
        after.rejects(ChallengeDrawFour(2))
        assertEquals(8, after.act(Draw(2)).state.hand(2).size)
    }

    @Test
    fun `plus two can't go on a plus four`() {
        val w4 = t.wild4()
        val blue2 = t.blue(DRAW_TWO)
        val s = t.state(spare() + w4, spare() + blue2, spare(), top = t.red(NINE), rules = stacking)
        val (after, _) = s.act(PlayCard(0, w4.id)).state.act(ChooseColor(0, CardColor.BLUE))
        assertEquals(1, after.current)
        after.rejects(PlayCard(1, blue2.id))
    }

    @Test
    fun `plain cards can't be played on a pending draw`() {
        val red2 = t.red(DRAW_TWO)
        val red5 = t.red(FIVE)
        val s = t.state(spare() + red2, spare() + red5, top = t.red(NINE), rules = stacking)
        s.act(PlayCard(0, red2.id)).state.rejects(PlayCard(1, red5.id))
    }

    @Test
    fun `without stacking a plus two is applied immediately`() {
        val red2 = t.red(DRAW_TWO)
        val blue2 = t.blue(DRAW_TWO)
        val s = t.state(spare() + red2, spare() + blue2, spare(), top = t.red(NINE))
        val (after, _) = s.act(PlayCard(0, red2.id))
        assertEquals(5, after.hand(1).size)
        assertEquals(2, after.current)
    }

    // Jump-in

    private val jumpIn = RuleSet(jumpIn = true)

    @Test
    fun `identical card can be played out of turn`() {
        val top = t.red(FIVE)
        val twin = t.red(FIVE)
        val s = t.state(spare(), spare(), spare() + twin, spare(), top = top, rules = jumpIn)
        val (after, events) = s.act(PlayCard(2, twin.id))
        assertEquals(GameEvent.CardPlayed(2, twin, jumpIn = true), events.first())
        assertEquals(3, after.current)
    }

    @Test
    fun `jump in needs an exact match`() {
        val s = t.state(spare(), spare() + t.red(SIX) + t.blue(FIVE), top = t.red(FIVE), rules = jumpIn)
        s.hand(1).forEach { s.rejects(PlayCard(1, it.id)) }
    }

    @Test
    fun `jump in is off by default`() {
        val twin = t.red(FIVE)
        t.state(spare(), spare() + twin, top = t.red(FIVE)).rejects(PlayCard(1, twin.id))
    }

    @Test
    fun `jump in with a skip skips the player after the jumper`() {
        val twin = t.red(Face.SKIP)
        val s = t.state(spare(), spare() + twin, spare(), spare(), top = t.red(Face.SKIP), rules = jumpIn)
        val (after, events) = s.act(PlayCard(1, twin.id))
        assertTrue(GameEvent.Skipped(2) in events)
        assertEquals(3, after.current)
    }

    // 7-0

    private val sevenZero = RuleSet(sevenZero = true)

    @Test
    fun `seven swaps hands with the chosen player`() {
        val seven = t.red(SEVEN)
        val mine = spare()
        val theirs = listOf(t.green(TWO))
        val s = t.state(mine + seven, spare(), theirs, top = t.red(NINE), rules = sevenZero)
        val (picking, _) = s.act(PlayCard(0, seven.id))
        assertEquals(Phase.PickSwapTarget, picking.phase)
        picking.rejects(SwapWith(0, 0))
        picking.rejects(SwapWith(1, 2))

        val (after, events) = picking.act(SwapWith(0, 2))
        assertEquals(theirs, after.hand(0))
        assertEquals(mine, after.hand(2))
        assertEquals(1, after.current)
        assertTrue(GameEvent.HandsSwapped(0, 2) in events)
    }

    @Test
    fun `zero passes every hand along the direction of play`() {
        val zero = t.red(ZERO)
        val a = spare()
        val b = listOf(t.green(TWO))
        val c = listOf(t.yellow(ONE), t.yellow(TWO), t.yellow(THREE))
        val s = t.state(a + zero, b, c, top = t.red(NINE), rules = sevenZero)
        val (after, events) = s.act(PlayCard(0, zero.id))
        assertEquals(c, after.hand(0))
        assertEquals(a, after.hand(1))
        assertEquals(b, after.hand(2))
        assertEquals(1, after.current)
        assertTrue(GameEvent.HandsRotated(Direction.CLOCKWISE) in events)
    }

    @Test
    fun `seven and zero are plain numbers when the rule is off`() {
        val seven = t.red(SEVEN)
        val (after, _) = t.state(spare() + seven, spare(), top = t.red(NINE)).act(PlayCard(0, seven.id))
        assertEquals(Phase.Turn, after.phase)
        assertEquals(1, after.current)
    }
}
