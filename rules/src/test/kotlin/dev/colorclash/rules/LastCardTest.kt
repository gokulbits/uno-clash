package dev.colorclash.rules

import dev.colorclash.rules.Action.CallLastCard
import dev.colorclash.rules.Action.CatchLastCard
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastCardTest {
    private val t = Table()
    private val r5 = t.red(FIVE)
    private val others = { listOf(t.blue(ONE), t.blue(TWO), t.green(TWO)) }
    private val twoCards = t.state(listOf(r5, t.blue(ONE)), others(), others(), top = t.red(NINE))

    @Test
    fun `going down to one card without calling exposes you`() {
        val (after, _) = twoCards.act(PlayCard(0, r5.id))
        assertEquals(0, after.exposedPlayer)
    }

    @Test
    fun `catching an exposed player adds the penalty`() {
        val (exposed, _) = twoCards.act(PlayCard(0, r5.id))
        val (after, events) = exposed.act(CatchLastCard(2, 0))
        assertEquals(3, after.hand(0).size)
        assertNull(after.exposedPlayer)
        assertEquals(GameEvent.PenaltyApplied(0, 2, 2), events.first())
        assertTrue(events.any { it is GameEvent.Drew && it.reason == DrawReason.MISSED_LAST_CARD })
        // Turn order is untouched by a catch.
        assertEquals(1, after.current)
    }

    @Test
    fun `penalty size follows the rule set`() {
        val s = twoCards.copy(rules = RuleSet(lastCardPenalty = 4))
        val (after, _) = s.act(PlayCard(0, r5.id)).state.act(CatchLastCard(1, 0))
        assertEquals(5, after.hand(0).size)
    }

    @Test
    fun `calling before playing protects you`() {
        val (called, events) = twoCards.act(CallLastCard(0))
        assertEquals(listOf<GameEvent>(GameEvent.LastCardCalled(0)), events)
        val (after, _) = called.act(PlayCard(0, r5.id))
        assertNull(after.exposedPlayer)
        after.rejects(CatchLastCard(1, 0))
    }

    @Test
    fun `calling late still saves you if nobody caught you yet`() {
        val (exposed, _) = twoCards.act(PlayCard(0, r5.id))
        val (after, _) = exposed.act(CallLastCard(0))
        assertNull(after.exposedPlayer)
        after.rejects(CatchLastCard(1, 0))
    }

    @Test
    fun `window closes once the next player acts`() {
        val (exposed, _) = twoCards.act(PlayCard(0, r5.id))
        val (after, _) = exposed.act(Draw(1))
        assertNull(after.exposedPlayer)
        after.rejects(CatchLastCard(2, 0))
    }

    @Test
    fun `window stays open while you pick a wild color`() {
        val wild = t.wild()
        val s = t.state(listOf(wild, t.blue(ONE)), others(), top = t.red(NINE))
        val (picking, _) = s.act(PlayCard(0, wild.id))
        assertEquals(0, picking.exposedPlayer)
        val (picked, _) = picking.act(ChooseColor(0, CardColor.BLUE))
        assertEquals(0, picked.exposedPlayer)
        picked.act(CatchLastCard(1, 0))
    }

    @Test
    fun `false catches are rejected`() {
        twoCards.rejects(CatchLastCard(1, 0))
        val (exposed, _) = twoCards.act(PlayCard(0, r5.id))
        exposed.rejects(CatchLastCard(0, 0))
        exposed.rejects(CatchLastCard(1, 2))
    }

    @Test
    fun `can't call with three or more cards or call twice`() {
        t.state(others(), others(), top = t.red(NINE)).rejects(CallLastCard(0))
        twoCards.act(CallLastCard(0)).state.rejects(CallLastCard(0))
    }

    @Test
    fun `drawing clears an earlier call`() {
        val (called, _) = twoCards.act(CallLastCard(0))
        val (after, _) = called.act(Draw(0))
        assertFalse(after.players[0].calledLastCard)
    }
}
