package dev.colorclash.rules

import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.Pass
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.SEVEN
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawTest {
    private val t = Table()

    @Test
    fun `drawing an unplayable card ends the turn`() {
        val g8 = t.green(Face.EIGHT)
        val b1 = t.blue(ONE)
        val s = t.state(listOf(b1), listOf(t.blue(TWO)), top = t.red(NINE), drawPile = listOf(g8))
        val (after, events) = s.act(Draw(0))
        assertEquals(listOf(b1, g8), after.hand(0))
        assertEquals(1, after.current)
        assertEquals(listOf<GameEvent>(GameEvent.Drew(0, listOf(g8), DrawReason.TURN)), events)
    }

    @Test
    fun `drawing a playable card lets you play it or keep it`() {
        val r5 = t.red(FIVE)
        val other = t.red(ONE)
        val s = t.state(listOf(t.blue(ONE), other), listOf(t.blue(TWO)), top = t.red(NINE), drawPile = listOf(r5))
        val (drew, _) = s.act(Draw(0))
        assertEquals(Phase.DrewPlayable(r5.id), drew.phase)
        assertEquals(0, drew.current)
        drew.rejects(PlayCard(0, other.id))
        drew.rejects(Draw(0))

        val (played, _) = drew.act(PlayCard(0, r5.id))
        assertEquals(r5, played.topCard)
        assertEquals(1, played.current)

        val (passed, _) = drew.act(Pass(0))
        assertEquals(1, passed.current)
        assertTrue(r5 in passed.hand(0))
    }

    @Test
    fun `you may draw even when holding a playable card`() {
        val s = t.state(listOf(t.red(ONE)), listOf(t.blue(TWO)), top = t.red(NINE))
        s.act(Draw(0))
    }

    @Test
    fun `draw until playable keeps drawing`() {
        val pile = listOf(t.blue(ONE), t.green(TWO), t.red(SEVEN), t.blue(THREE))
        val s = t.state(
            listOf(t.blue(FIVE)), listOf(t.blue(TWO)),
            top = t.red(NINE), drawPile = pile, rules = RuleSet(drawUntilPlayable = true),
        )
        val (after, events) = s.act(Draw(0))
        assertEquals(4, after.hand(0).size)
        assertEquals(Phase.DrewPlayable(pile[2].id), after.phase)
        assertEquals(listOf<GameEvent>(GameEvent.Drew(0, pile.take(3), DrawReason.TURN)), events)
    }

    @Test
    fun `empty draw pile reshuffles the discard pile but keeps the top card`() {
        val below = listOf(t.green(ONE), t.green(TWO))
        val top = t.red(NINE)
        val s = t.state(listOf(t.blue(ONE)), listOf(t.blue(TWO)), top = top, drawPile = emptyList(), discardBelow = below)
        val (after, events) = s.act(Draw(0))
        assertEquals(listOf(top), after.discardPile)
        assertEquals(1, after.drawPile.size)
        assertEquals(2, after.hand(0).size)
        assertEquals(GameEvent.DeckReshuffled(2), events.first())
        assertEquals(s.allCards().toSet(), after.allCards().toSet())
    }

    @Test
    fun `nothing left to draw just passes the turn`() {
        val s = t.state(listOf(t.blue(ONE)), listOf(t.blue(TWO)), top = t.red(NINE), drawPile = emptyList())
        val (after, _) = s.act(Draw(0))
        assertEquals(1, after.hand(0).size)
        assertEquals(1, after.current)
    }
}
