package dev.colorclash.rules

import dev.colorclash.rules.Action.ChooseColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SetupTest {

    @Test
    fun `new game deals seven each and flips one card`() {
        val (state, events) = GameEngine.newGame(4, seed = 1)
        assertEquals(1, state.round)
        assertEquals(1, state.discardPile.size)
        assertEquals(Deck.SIZE, state.allCards().map { it.id }.toSet().size)
        assertEquals(4, events.count { it is GameEvent.Dealt })
        assertTrue(events.first() is GameEvent.RoundStarted)
        // Only a +2 starter changes a hand size.
        if (state.topCard.face != Face.DRAW_TWO) assertTrue(state.players.all { it.hand.size == 7 })
    }

    @Test
    fun `same seed gives the same game`() {
        assertEquals(GameEngine.newGame(5, seed = 99), GameEngine.newGame(5, seed = 99))
        assertNotEquals(GameEngine.newGame(5, seed = 99).state, GameEngine.newGame(5, seed = 100).state)
    }

    @Test
    fun `player count must be 2 to 6`() {
        assertFailsWith<IllegalArgumentException> { GameEngine.newGame(1, seed = 0) }
        assertFailsWith<IllegalArgumentException> { GameEngine.newGame(7, seed = 0) }
    }

    @Test
    fun `wild draw four never starts a round`() {
        repeat(2000) { seed ->
            assertNotEquals(Face.WILD_DRAW_FOUR, GameEngine.newGame(3, seed = seed.toLong()).state.topCard.face)
        }
    }

    @Test
    fun `skip starter skips the first player`() {
        val s = gameStartingWith(Face.SKIP, players = 3)
        val first = (s.dealer + 1) % 3
        assertEquals((first + 1) % 3, s.current)
    }

    @Test
    fun `reverse starter lets the dealer go first the other way`() {
        val s = gameStartingWith(Face.REVERSE, players = 4)
        assertEquals(Direction.COUNTER_CLOCKWISE, s.direction)
        assertEquals(s.dealer, s.current)
    }

    @Test
    fun `reverse starter with two players acts as skip`() {
        val s = gameStartingWith(Face.REVERSE, players = 2)
        assertEquals(Direction.CLOCKWISE, s.direction)
        assertEquals(s.dealer, s.current)
    }

    @Test
    fun `draw two starter hits the first player`() {
        val s = gameStartingWith(Face.DRAW_TWO, players = 3)
        val first = (s.dealer + 1) % 3
        assertEquals(9, s.hand(first).size)
        assertEquals((first + 1) % 3, s.current)
    }

    @Test
    fun `wild starter lets the first player pick a color and still play`() {
        val s = gameStartingWith(Face.WILD, players = 3)
        val first = (s.dealer + 1) % 3
        assertEquals(Phase.PickColor(keepTurn = true), s.phase)
        assertEquals(null, s.activeColor)
        val (after, _) = s.act(ChooseColor(first, CardColor.GREEN))
        assertEquals(first, after.current)
        assertEquals(Phase.Turn, after.phase)
        assertEquals(CardColor.GREEN, after.activeColor)
    }

    private fun gameStartingWith(face: Face, players: Int): GameState =
        (0L..20_000L).asSequence()
            .map { GameEngine.newGame(players, seed = it).state }
            .first { it.topCard.face == face }
}
