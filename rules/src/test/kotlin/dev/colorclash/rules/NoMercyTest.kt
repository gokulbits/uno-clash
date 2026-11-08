package dev.colorclash.rules

import dev.colorclash.rules.Action.ChallengeDrawFour
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.Pass
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Action.SwapWith
import dev.colorclash.rules.Face.DISCARD_ALL
import dev.colorclash.rules.Face.DRAW_FOUR
import dev.colorclash.rules.Face.DRAW_TWO
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.REVERSE
import dev.colorclash.rules.Face.SEVEN
import dev.colorclash.rules.Face.SKIP
import dev.colorclash.rules.Face.SKIP_EVERYONE
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import dev.colorclash.rules.Face.WILD
import dev.colorclash.rules.Face.WILD_COLOR_ROULETTE
import dev.colorclash.rules.Face.WILD_DRAW_FOUR
import dev.colorclash.rules.Face.WILD_DRAW_SIX
import dev.colorclash.rules.Face.WILD_DRAW_TEN
import dev.colorclash.rules.Face.WILD_REVERSE_DRAW_FOUR
import dev.colorclash.rules.Face.ZERO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoMercyTest {
    private val t = Table()
    private val rules = RuleSet(mode = GameMode.NO_MERCY, targetScore = 1000)

    /** Two blue cards that never match a red top card, so the other seats sit still. */
    private fun spare() = listOf(t.blue(ONE), t.blue(THREE))

    // The deck

    @Test
    fun `no mercy deck has 168 cards in the official proportions`() {
        val deck = Deck.noMercy()
        assertEquals(Deck.NO_MERCY_SIZE, deck.size)
        assertEquals((0 until Deck.NO_MERCY_SIZE).toSet(), deck.map { it.id }.toSet())
        for (color in CardColor.entries) {
            val ofColor = deck.filter { it.color == color }
            assertEquals(37, ofColor.size, "$color")
            assertEquals(1, ofColor.count { it.face == ZERO })
            assertEquals(2, ofColor.count { it.face == NINE })
            for (face in listOf(SKIP, REVERSE, DRAW_TWO, DRAW_FOUR, SKIP_EVERYONE, DISCARD_ALL)) {
                assertEquals(3, ofColor.count { it.face == face }, "$color $face")
            }
        }
        for (face in listOf(WILD, WILD_REVERSE_DRAW_FOUR, WILD_DRAW_SIX, WILD_DRAW_TEN, WILD_COLOR_ROULETTE)) {
            assertEquals(4, deck.count { it.face == face }, "$face")
        }
        // The Wild +4 is replaced by the colored +4 and the Wild Reverse +4.
        assertEquals(0, deck.count { it.face == WILD_DRAW_FOUR })
    }

    @Test
    fun `a no mercy round deals the big deck and opens on a number`() {
        repeat(20) { seed ->
            val s = GameEngine.newGame(4, rules, seed.toLong()).state
            assertEquals(Deck.NO_MERCY_SIZE, s.allCards().size)
            assertFalse(s.topCard.face.isAction, "opened on ${s.topCard}")
            assertEquals(Phase.Turn, s.phase)
        }
    }

    // The new cards

    @Test
    fun `a colored plus four hands the next player four cards`() {
        val four = t.red(DRAW_FOUR)
        val mid = t.state(listOf(four) + spare(), spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, four.id)).state
        assertEquals(4, mid.pendingDraw)
        assertEquals(4, mid.pendingDrawValue)
        assertEquals(1, mid.current)

        val after = mid.act(Draw(1)).state
        assertEquals(6, after.hand(1).size)
        assertEquals(0, after.pendingDraw)
        assertEquals(2, after.current)
    }

    @Test
    fun `a wild plus six picks a color and then bites`() {
        val six = t.card(WILD_DRAW_SIX)
        val picked = t.state(listOf(six) + spare(), spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, six.id)).state
            .act(ChooseColor(0, CardColor.BLUE)).state
        assertEquals(6, picked.pendingDraw)
        assertEquals(CardColor.BLUE, picked.activeColor)
        assertEquals(1, picked.current)
        assertEquals(8, picked.act(Draw(1)).state.hand(1).size)
    }

    @Test
    fun `a stack has to match or beat the last draw card`() {
        val four = t.red(DRAW_FOUR)
        val two = t.blue(DRAW_TWO)
        val ten = t.card(WILD_DRAW_TEN)
        val yellowFour = t.yellow(DRAW_FOUR)
        val after = t.state(listOf(four) + spare(), listOf(two, ten, yellowFour), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, four.id)).state

        after.rejects(PlayCard(1, two.id))
        assertNull(GameEngine.validate(after, PlayCard(1, yellowFour.id)))

        val stacked = after.act(PlayCard(1, ten.id)).state
        assertEquals(14, stacked.pendingDraw)
        assertEquals(10, stacked.pendingDrawValue)
    }

    @Test
    fun `skip everyone comes straight back round to you`() {
        val skipAll = t.red(SKIP_EVERYONE)
        val (after, events) = t.state(listOf(skipAll) + spare(), spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, skipAll.id))
        assertEquals(0, after.current)
        assertEquals(Phase.Turn, after.phase)
        assertTrue(events.any { it is GameEvent.EveryoneSkipped && it.player == 0 })
    }

    @Test
    fun `discard all sheds the rest of that color under it`() {
        val discardAll = t.red(DISCARD_ALL)
        val hand = listOf(discardAll, t.red(TWO), t.red(SKIP), t.blue(ONE), t.card(WILD))
        val (after, events) = t.state(hand, spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, discardAll.id))
        assertEquals(2, after.hand(0).size)
        assertTrue(after.hand(0).none { it.color == CardColor.RED })
        assertEquals(discardAll, after.topCard)
        assertTrue(events.any { it is GameEvent.DiscardedAll && it.player == 0 && it.cards.size == 2 })
        assertEquals(1, after.current)
    }

    @Test
    fun `discard all can win the round on its own`() {
        val discardAll = t.red(DISCARD_ALL)
        val after = t.state(listOf(discardAll, t.red(TWO), t.red(FIVE)), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, discardAll.id)).state
        assertIs<Phase.RoundOver>(after.phase)
    }

    @Test
    fun `a wild reverse plus four turns the table round before it bites`() {
        val card = t.card(WILD_REVERSE_DRAW_FOUR)
        val after = t.state(listOf(card) + spare(), spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, card.id)).state
        assertEquals(Direction.COUNTER_CLOCKWISE, after.direction)

        val picked = after.act(ChooseColor(0, CardColor.GREEN)).state
        assertEquals(4, picked.pendingDraw)
        assertEquals(2, picked.current)
        assertEquals(6, picked.act(Draw(2)).state.hand(2).size)
    }

    @Test
    fun `at a table of two a wild reverse plus four comes back on you`() {
        val card = t.card(WILD_REVERSE_DRAW_FOUR)
        val picked = t.state(listOf(card) + spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, card.id)).state
            .act(ChooseColor(0, CardColor.GREEN)).state
        assertEquals(0, picked.current)
        assertEquals(4, picked.pendingDraw)

        val after = picked.act(Draw(0)).state
        assertEquals(6, after.hand(0).size)
        assertEquals(1, after.current)
    }

    @Test
    fun `color roulette makes the next player name a color and eat the pile`() {
        val card = t.card(WILD_COLOR_ROULETTE)
        val mid = t.state(
            listOf(card) + spare(), spare(), spare(),
            top = t.red(NINE), rules = rules, drawPile = t.filler(2) + t.blue(THREE) + t.filler(4),
        ).act(PlayCard(0, card.id)).state

        assertEquals(1, mid.current)
        assertEquals(Phase.PickColor(roulette = true), mid.phase)
        mid.rejects(ChooseColor(0, CardColor.BLUE))

        val (after, events) = mid.act(ChooseColor(1, CardColor.BLUE))
        assertEquals(5, after.hand(1).size)
        assertEquals(CardColor.BLUE, after.activeColor)
        assertEquals(2, after.current)
        assertTrue(events.any { it is GameEvent.ColorRoulette && it.player == 1 && it.cards == 3 })
    }

    // The mercy rule

    @Test
    fun `twenty five cards knocks you out of the round`() {
        val two = t.red(DRAW_TWO)
        val mid = t.state(
            listOf(two) + spare(), t.filler(24), spare(),
            top = t.red(NINE), rules = rules, drawPile = t.filler(30),
        ).act(PlayCard(0, two.id)).state

        val (after, events) = mid.act(Draw(1))
        assertTrue(after.players[1].knockedOut)
        assertTrue(after.hand(1).isEmpty())
        assertEquals(26, after.setAside.size)
        assertEquals(1, after.knockouts)
        assertTrue(events.any { it is GameEvent.KnockedOut && it.player == 1 && it.cards == 26 })
        // Play steps over the empty seat from here on.
        assertEquals(2, after.current)
        assertEquals(0, after.seatAfter(2))
        assertEquals(listOf(0, 2), after.activeSeats)
        after.rejects(Draw(1))
    }

    @Test
    fun `knocking out the last opponent wins the round with the bonus`() {
        val two = t.red(DRAW_TWO)
        val after = t.state(
            listOf(two) + spare(), t.filler(24),
            top = t.red(NINE), rules = rules, drawPile = t.filler(30),
        ).act(PlayCard(0, two.id)).state.act(Draw(1)).state

        val phase = after.phase
        assertIs<Phase.RoundOver>(phase)
        assertEquals(0, phase.winner)
        assertEquals(250, phase.points)
        assertEquals(250, after.players[0].score)
    }

    @Test
    fun `a knocked out hand goes back in at the reshuffle`() {
        val s = t.state(
            spare(), spare(),
            top = t.red(NINE), rules = rules, drawPile = emptyList(), setAside = t.filler(4) + t.red(TWO),
        )
        val (after, events) = s.act(Draw(0))
        assertTrue(events.any { it is GameEvent.DeckReshuffled })
        assertTrue(after.setAside.isEmpty())
        assertEquals(s.allCards().size, after.allCards().size)
        assertTrue(after.hand(0).size > 2)
    }

    @Test
    fun `the seven swap is compulsory and never picks someone who is out`() {
        val seven = t.red(SEVEN)
        val mid = t.state(
            listOf(seven) + spare(), spare(), spare(),
            top = t.red(NINE), rules = rules, knockedOut = listOf(false, true, false),
        ).act(PlayCard(0, seven.id)).state

        assertEquals(Phase.PickSwapTarget, mid.phase)
        mid.rejects(SwapWith(0, 1))
        assertEquals(2, mid.act(SwapWith(0, 2)).state.hand(0).size)
    }

    // What No Mercy turns off

    @Test
    fun `no mercy makes you draw till playable and then play it`() {
        val after = t.state(
            spare(), spare(),
            top = t.red(NINE), rules = rules, drawPile = t.filler(2) + t.red(TWO) + t.filler(5),
        ).act(Draw(0)).state

        val phase = after.phase
        assertIs<Phase.DrewPlayable>(phase)
        assertEquals(5, after.hand(0).size)
        after.rejects(Pass(0))
        assertEquals(1, after.act(PlayCard(0, phase.cardId)).state.current)
    }

    @Test
    fun `no mercy has nothing to challenge`() {
        val six = t.card(WILD_DRAW_SIX)
        val after = t.state(listOf(six, t.red(ONE)), spare(), spare(), top = t.red(NINE), rules = rules)
            .act(PlayCard(0, six.id)).state
            .act(ChooseColor(0, CardColor.BLUE)).state
        assertNull(after.drawFourCheck)
        after.rejects(ChallengeDrawFour(1))
    }

    @Test
    fun `classic games keep the small deck and nobody is ever knocked out`() {
        assertEquals(Deck.SIZE, GameEngine.newGame(3, RuleSet(), seed = 7).state.allCards().size)

        val two = t.red(DRAW_TWO)
        val after = t.state(listOf(two) + spare(), t.filler(24), spare(), top = t.red(NINE), drawPile = t.filler(30))
            .act(PlayCard(0, two.id)).state
        assertEquals(26, after.hand(1).size)
        assertFalse(after.players[1].knockedOut)
    }
}
