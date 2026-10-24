package dev.colorclash.rules

import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Action.StartNextRound
import dev.colorclash.rules.Face.DRAW_TWO
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.SEVEN
import dev.colorclash.rules.Face.SKIP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringTest {
    private val t = Table()

    @Test
    fun `a one round game ends with the first round, even on zero points`() {
        val last = t.red(FIVE)
        val s = t.state(listOf(last), listOf(t.blue(Face.ZERO)), top = t.red(NINE), rules = RuleSet(targetScore = 0))
        val (after, _) = s.act(PlayCard(0, last.id))
        assertEquals(Phase.GameOver(winner = 0), after.phase)
    }

    @Test
    fun `round winner collects points left in other hands`() {
        val last = t.red(FIVE)
        val s = t.state(listOf(last), listOf(t.wild(), t.red(SKIP)), listOf(t.blue(SEVEN)), top = t.red(NINE))
        val (after, events) = s.act(PlayCard(0, last.id))
        assertEquals(Phase.RoundOver(winner = 0, points = 77), after.phase)
        assertEquals(77, after.players[0].score)
        assertEquals(GameEvent.RoundWon(0, 77, 77), events.last())
    }

    @Test
    fun `last card can be a wild with no color pick`() {
        val wild = t.wild()
        val (after, _) = t.state(listOf(wild), listOf(t.blue(ONE)), top = t.red(NINE)).act(PlayCard(0, wild.id))
        assertEquals(Phase.RoundOver(0, 1), after.phase)
    }

    @Test
    fun `draw two as the last card still hits the next player and counts`() {
        val d2 = t.red(DRAW_TWO)
        val pile = listOf(t.blue(NINE), t.blue(FIVE))
        val s = t.state(listOf(d2), listOf(t.blue(ONE)), top = t.red(NINE), drawPile = pile)
        val (after, events) = s.act(PlayCard(0, d2.id))
        assertEquals(15, after.players[0].score)
        assertTrue(GameEvent.Drew(1, pile, DrawReason.DRAW_CARD) in events)
    }

    @Test
    fun `reaching the target ends the game`() {
        val last = t.red(FIVE)
        val s = t.state(listOf(last), listOf(t.wild()), top = t.red(NINE), scores = listOf(460, 300))
        val (after, events) = s.act(PlayCard(0, last.id))
        assertEquals(Phase.GameOver(0), after.phase)
        assertEquals(510, after.players[0].score)
        assertEquals(GameEvent.GameWon(0), events.last())
        after.rejects(StartNextRound)
        after.rejects(Draw(1))
    }

    @Test
    fun `next round deals fresh hands, keeps scores and moves the dealer`() {
        val (game, _) = GameEngine.newGame(3, RuleSet(targetScore = 10_000), seed = 5)
        game.rejects(StartNextRound)

        val roundOver = game.copy(phase = Phase.RoundOver(0, 40), players = game.players.map { it.copy(score = 40) })
        roundOver.rejects(Draw(roundOver.current))
        val (next, events) = roundOver.act(StartNextRound)
        assertEquals(2, next.round)
        assertEquals((game.dealer + 1) % 3, next.dealer)
        assertTrue(next.players.all { it.score == 40 })
        assertEquals(Deck.SIZE, next.allCards().map { it.id }.toSet().size)
        assertEquals(GameEvent.RoundStarted(2, next.dealer), events.first())
    }
}
