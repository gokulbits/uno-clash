package dev.colorclash.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DeckTest {

    @Test
    fun `standard deck has 108 uniquely numbered cards`() {
        val deck = Deck.standard()
        assertEquals(Deck.SIZE, deck.size)
        assertEquals((0 until Deck.SIZE).toSet(), deck.map { it.id }.toSet())
    }

    @Test
    fun `deck composition matches the official counts`() {
        val deck = Deck.standard()
        for (color in CardColor.light) {
            val ofColor = deck.filter { it.color == color }
            assertEquals(25, ofColor.size)
            assertEquals(1, ofColor.count { it.face == Face.ZERO })
            for (face in listOf(Face.ONE, Face.NINE, Face.SKIP, Face.REVERSE, Face.DRAW_TWO)) {
                assertEquals(2, ofColor.count { it.face == face }, "$color $face")
            }
        }
        assertEquals(4, deck.count { it.face == Face.WILD })
        assertEquals(4, deck.count { it.face == Face.WILD_DRAW_FOUR })
    }

    @Test
    fun `swap hands puts one extra wild in the classic deck and none in no mercy`() {
        val off = RuleSet()
        val on = RuleSet(swapHandsWild = true)
        assertEquals(Deck.SIZE, Deck.forRules(off, Rng(1)).size)
        assertEquals(0, Deck.forRules(off, Rng(1)).count { it.face == Face.WILD_SWAP_HANDS })
        assertEquals(Deck.SIZE + 1, Deck.forRules(on, Rng(1)).size)
        assertEquals(1, Deck.forRules(on, Rng(1)).count { it.face == Face.WILD_SWAP_HANDS })
        assertEquals((0..Deck.SIZE).toSet(), Deck.forRules(on, Rng(1)).map { it.id }.toSet())
        assertEquals(Deck.sizeFor(on), Deck.forRules(on, Rng(1)).size)
        // No Mercy comes with its own 168; the house rule doesn't reach it.
        val noMercy = RuleSet(mode = GameMode.NO_MERCY, swapHandsWild = true)
        assertEquals(Deck.NO_MERCY_SIZE, Deck.forRules(noMercy, Rng(1)).size)
        assertEquals(0, Deck.forRules(noMercy, Rng(1)).count { it.face == Face.WILD_SWAP_HANDS })
    }

    @Test
    fun `card points`() {
        assertEquals(40, Face.WILD_SWAP_HANDS.points)
        assertEquals(7, Face.SEVEN.points)
        assertEquals(0, Face.ZERO.points)
        assertEquals(20, Face.SKIP.points)
        assertEquals(20, Face.DRAW_TWO.points)
        assertEquals(50, Face.WILD.points)
        assertEquals(50, Face.WILD_DRAW_FOUR.points)
        assertEquals(1240, Deck.standard().points())
    }

    @Test
    fun `wilds can't have a color and colored cards need one`() {
        assertFailsWith<IllegalArgumentException> { Card(0, Face.WILD, CardColor.RED) }
        assertFailsWith<IllegalArgumentException> { Card(0, Face.FIVE, null) }
    }

    @Test
    fun `rng is reproducible`() {
        val a = Deck.standard().toMutableList().also { Rng(42).shuffle(it) }
        val b = Deck.standard().toMutableList().also { Rng(42).shuffle(it) }
        val c = Deck.standard().toMutableList().also { Rng(43).shuffle(it) }
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
