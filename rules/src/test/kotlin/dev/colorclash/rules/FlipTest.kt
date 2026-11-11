package dev.colorclash.rules

import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.DRAW_FIVE
import dev.colorclash.rules.Face.DRAW_ONE
import dev.colorclash.rules.Face.FLIP
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.SKIP_EVERYONE
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import dev.colorclash.rules.Face.WILD
import dev.colorclash.rules.Face.WILD_DRAW_COLOR
import dev.colorclash.rules.Face.WILD_DRAW_TWO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FlipTest {
    private val t = Table()
    private val rules = RuleSet(mode = GameMode.FLIP)

    /** A light card with a known dark side on the back of it. */
    private fun two(light: Card, dark: Card) = light.copy(back = CardBack(dark.face, dark.color))

    private fun dark(face: Face, color: CardColor? = null) = Card(9000 + face.ordinal, face, color)

    @Test
    fun `the flip deck is 112 cards, both sides, no zeros`() {
        val deck = Deck.flip(Rng(1))
        assertEquals(Deck.FLIP_SIZE, deck.size)
        assertEquals((0 until Deck.FLIP_SIZE).toSet(), deck.map { it.id }.toSet())
        assertTrue(deck.all { it.back != null })
        assertEquals(0, deck.count { it.face == Face.ZERO })
        assertEquals(0, deck.count { it.back?.face == Face.ZERO })

        // Light side: 18 numbers a colour, two each of the four actions, four of each wild.
        for (color in CardColor.light) {
            val ofColor = deck.filter { it.color == color }
            assertEquals(26, ofColor.size, "$color")
            assertEquals(18, ofColor.count { it.face.number != null }, "$color numbers")
            for (face in listOf(DRAW_ONE, Face.REVERSE, Face.SKIP, FLIP)) {
                assertEquals(2, ofColor.count { it.face == face }, "$color $face")
            }
        }
        assertEquals(4, deck.count { it.face == WILD })
        assertEquals(4, deck.count { it.face == WILD_DRAW_TWO })

        // Dark side: the same shape in the other four colours.
        for (color in CardColor.dark) {
            val ofColor = deck.filter { it.back?.color == color }
            assertEquals(26, ofColor.size, "$color")
            for (face in listOf(DRAW_FIVE, Face.REVERSE, SKIP_EVERYONE, FLIP)) {
                assertEquals(2, ofColor.count { it.back?.face == face }, "$color $face")
            }
        }
        assertEquals(4, deck.count { it.back?.face == WILD })
        assertEquals(4, deck.count { it.back?.face == WILD_DRAW_COLOR })
    }

    @Test
    fun `the pairing is shuffled, so a dark side doesn't give the light one away`() {
        val a = Deck.flip(Rng(1)).map { it.back }
        val b = Deck.flip(Rng(2)).map { it.back }
        assertNotEquals(a, b)
    }

    @Test
    fun `a round deals the flip deck and opens on the light side`() {
        repeat(10) { seed ->
            val s = GameEngine.newGame(4, rules, seed.toLong()).state
            assertEquals(Deck.FLIP_SIZE, s.allCards().size)
            assertEquals(Side.LIGHT, s.side)
            assertTrue(s.hand(0).all { it.color?.side == Side.LIGHT || it.isWild })
            assertNotEquals(WILD_DRAW_TWO, s.topCard.face, "a Wild Draw Two can't start a round")
        }
    }

    @Test
    fun `playing a flip turns the game over and the card underneath is what you match`() {
        val flip = two(t.red(FLIP), dark(Face.SKIP_EVERYONE, CardColor.PINK))
        val under = two(t.red(NINE), dark(TWO, CardColor.TEAL))
        val mine = two(t.blue(ONE), dark(THREE, CardColor.PURPLE))
        val s = t.state(
            listOf(flip, mine), listOf(two(t.green(TWO), dark(ONE, CardColor.ORANGE))),
            top = under, rules = rules, drawPile = List(5) { two(t.blue(THREE), dark(NINE, CardColor.TEAL)) },
        )
        val (after, events) = s.act(PlayCard(0, flip.id))

        assertEquals(Side.DARK, after.side)
        // The flip card itself went to the bottom; what was under it is now on top, dark side up.
        assertEquals(TWO, after.topCard.face)
        assertEquals(CardColor.TEAL, after.topCard.color)
        assertEquals(CardColor.TEAL, after.activeColor)
        assertEquals(SKIP_EVERYONE, after.discardPile.first().face, "the flip card is at the bottom now")
        // Hands turned over with it.
        assertEquals(THREE, after.hand(0).single().face)
        assertEquals(CardColor.PURPLE, after.hand(0).single().color)
        assertTrue(after.drawPile.all { it.color?.side == Side.DARK })
        assertTrue(events.any { it is GameEvent.SideFlipped })
        // Its action doesn't fire: play just moves on.
        assertEquals(1, after.current)
    }

    @Test
    fun `a wild left on top by a flip is colored by whoever flipped`() {
        val flip = two(t.red(FLIP), dark(TWO, CardColor.PINK))
        val under = two(t.red(NINE), dark(WILD))
        val s = t.state(
            listOf(flip, two(t.blue(ONE), dark(THREE, CardColor.PURPLE))), listOf(two(t.green(TWO), dark(ONE, CardColor.ORANGE))),
            top = under, rules = rules, drawPile = List(5) { two(t.blue(THREE), dark(NINE, CardColor.TEAL)) },
        )
        val (flipped, _) = s.act(PlayCard(0, flip.id))
        assertEquals(Phase.PickColor(afterFlip = true), flipped.phase)
        assertEquals(0, flipped.current, "the player who flipped names it")
        flipped.rejects(ChooseColor(0, CardColor.RED))

        val (after, _) = flipped.act(ChooseColor(0, CardColor.TEAL))
        assertEquals(CardColor.TEAL, after.activeColor)
        assertEquals(1, after.current, "naming the color is all that happens")
    }

    @Test
    fun `wild draw color makes the next player draw until your color turns up`() {
        val card = two(t.card(WILD_DRAW_COLOR), dark(WILD))
        val pile = listOf(
            two(t.blue(ONE), dark(THREE, CardColor.PURPLE)),
            two(t.blue(TWO), dark(ONE, CardColor.ORANGE)),
            two(t.blue(THREE), dark(NINE, CardColor.TEAL)),
            two(t.green(ONE), dark(TWO, CardColor.PINK)),
        )
        val s = t.state(
            listOf(card, two(t.blue(ONE), dark(THREE, CardColor.PURPLE))),
            listOf(two(t.green(TWO), dark(ONE, CardColor.ORANGE))),
            top = two(t.red(NINE), dark(TWO, CardColor.TEAL)), rules = rules, drawPile = pile,
        )
        val (picking, _) = s.act(PlayCard(0, card.id))
        assertIs<Phase.PickColor>(picking.phase)

        // Blue, blue, blue, then green: the victim keeps everything they turned over.
        val (after, _) = picking.act(ChooseColor(0, CardColor.GREEN))
        assertEquals(1 + 4, after.hand(1).size)
        assertEquals(0, after.current, "and they lose their turn")
    }

    @Test
    fun `flip scores the side the round ended on`() {
        // Draw One is 10 on the light side; Skip Everyone is 30 on the dark.
        assertEquals(10, DRAW_ONE.pointsIn(GameMode.FLIP))
        assertEquals(30, SKIP_EVERYONE.pointsIn(GameMode.FLIP))
        assertEquals(40, WILD.pointsIn(GameMode.FLIP))
        assertEquals(50, WILD_DRAW_TWO.pointsIn(GameMode.FLIP))
        assertEquals(60, WILD_DRAW_COLOR.pointsIn(GameMode.FLIP))
        assertEquals(20, FLIP.pointsIn(GameMode.FLIP))
        // The other modes are untouched by any of that.
        assertEquals(50, WILD.pointsIn(GameMode.CLASSIC))
        assertEquals(20, SKIP_EVERYONE.pointsIn(GameMode.NO_MERCY))
    }

    @Test
    fun `flip forces its own rules on`() {
        val house = RuleSet(mode = GameMode.FLIP, stackDraws = true, sevenZero = true, swapHandsWild = true)
        assertFalse(house.stacking, "no stacking in Flip")
        assertFalse(house.swaps, "no zeros to pass along")
        assertFalse(house.swapHands, "Flip brings its own 112")
        assertEquals(Deck.FLIP_SIZE, Deck.sizeFor(house))
    }
}
