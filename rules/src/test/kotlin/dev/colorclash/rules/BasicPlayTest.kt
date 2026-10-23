package dev.colorclash.rules

import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.Pass
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasicPlayTest {
    private val t = Table()

    @Test
    fun `matching color is playable and passes the turn`() {
        val r5 = t.red(FIVE)
        val s = t.state(listOf(r5, t.blue(ONE), t.blue(TWO)), listOf(t.green(TWO)), listOf(t.green(THREE)), top = t.red(NINE))
        val (after, events) = s.act(PlayCard(0, r5.id))
        assertEquals(r5, after.topCard)
        assertEquals(1, after.current)
        assertEquals(listOf<GameEvent>(GameEvent.CardPlayed(0, r5)), events)
    }

    @Test
    fun `matching number is playable and changes the color`() {
        val b9 = t.blue(NINE)
        val s = t.state(listOf(b9, t.blue(ONE), t.blue(TWO)), listOf(t.green(TWO)), top = t.red(NINE))
        val (after, _) = s.act(PlayCard(0, b9.id))
        assertEquals(CardColor.BLUE, after.activeColor)
    }

    @Test
    fun `mismatched card is rejected`() {
        val b5 = t.blue(FIVE)
        t.state(listOf(b5, t.blue(ONE)), listOf(t.green(TWO)), top = t.red(NINE)).rejects(PlayCard(0, b5.id))
    }

    @Test
    fun `only the current player may play`() {
        val r5 = t.red(FIVE)
        t.state(listOf(t.blue(ONE)), listOf(r5, t.green(TWO)), top = t.red(NINE)).rejects(PlayCard(1, r5.id))
    }

    @Test
    fun `can't play a card from someone else's hand`() {
        val r5 = t.red(FIVE)
        t.state(listOf(t.blue(ONE)), listOf(r5, t.green(TWO)), top = t.red(NINE)).rejects(PlayCard(0, r5.id))
    }

    @Test
    fun `wild is always playable and waits for a color`() {
        val wild = t.wild()
        val s = t.state(listOf(wild, t.blue(ONE), t.blue(TWO)), listOf(t.green(TWO)), listOf(t.green(THREE)), top = t.red(NINE))
        val (picking, _) = s.act(PlayCard(0, wild.id))
        assertEquals(Phase.PickColor(), picking.phase)
        assertEquals(0, picking.current)
        picking.rejects(ChooseColor(1, CardColor.BLUE))
        picking.rejects(Draw(0))

        val (after, events) = picking.act(ChooseColor(0, CardColor.BLUE))
        assertEquals(CardColor.BLUE, after.activeColor)
        assertEquals(1, after.current)
        assertEquals(listOf<GameEvent>(GameEvent.WildColorChosen(0, CardColor.BLUE)), events)
    }

    @Test
    fun `after a wild only the chosen color or a matching face plays`() {
        val wild = t.wild()
        val g2 = t.green(TWO)
        val r2 = t.red(TWO)
        val s = t.state(listOf(wild, t.blue(ONE), t.blue(TWO)), listOf(g2, r2), top = t.red(NINE))
        val (after, _) = s.act(PlayCard(0, wild.id)).state.act(ChooseColor(0, CardColor.GREEN))
        assertTrue(GameEngine.isPlayable(after, 1, g2))
        assertFalse(GameEngine.isPlayable(after, 1, r2))
    }

    @Test
    fun `pass is only allowed right after drawing a playable card`() {
        t.state(listOf(t.blue(ONE)), listOf(t.green(TWO)), top = t.red(NINE)).rejects(Pass(0))
    }

    @Test
    fun `legal actions all pass validation`() {
        val (state, _) = GameEngine.newGame(4, seed = 7)
        val legal = GameEngine.legalActions(state)
        assertTrue(legal.isNotEmpty())
        legal.forEach { state.act(it) }
    }
}
