package dev.colorclash.rules

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The shuffle and deal can't favour anyone. Seeds are fixed so these never flake; the bounds are
 * chi-square critical values at the 0.1% level, which a fair shuffle clears easily.
 */
class FairnessTest {

    @Test
    fun `nextInt has no favourite`() {
        val rng = Rng(1)
        val counts = IntArray(7)
        repeat(70_000) { counts[rng.nextInt(7)]++ }
        assertTrue(chiSquare(counts) < 22.46, counts.toList().toString())
    }

    @Test
    fun `shuffle makes every ordering equally likely`() {
        val rng = Rng(2)
        val seen = HashMap<List<Int>, Int>()
        repeat(48_000) {
            val list = mutableListOf(0, 1, 2, 3)
            rng.shuffle(list)
            seen.merge(list, 1, Int::plus)
        }
        assertEquals(24, seen.size)
        assertTrue(chiSquare(seen.values.toIntArray()) < 49.73, seen.toString())
    }

    @Test
    fun `any card can end up anywhere in the deck`() {
        val rng = Rng(3)
        val positions = IntArray(Deck.SIZE)
        repeat(30_000) {
            val deck = Deck.standard().toMutableList()
            rng.shuffle(deck)
            positions[deck.indexOfFirst { it.id == 0 }]++
        }
        assertTrue(chiSquare(positions) < 158.0)
    }

    @Test
    fun `dealing goes round the table one card at a time, starting left of the dealer`() {
        val seed = 11L
        val players = 4
        // Replays newGame's draws: the dealer, then the shuffle.
        val rng = Rng(seed)
        val dealer = rng.nextInt(players)
        val deck = Deck.standard().toMutableList().also { rng.shuffle(it) }

        val dealt = GameEngine.newGame(players, seed = seed).events.filterIsInstance<GameEvent.Dealt>()
        assertEquals(List(players) { (dealer + 1 + it) % players }, dealt.map { it.player })
        dealt.forEachIndexed { i, d ->
            assertEquals(List(7) { round -> deck[round * players + i] }, d.cards, "seat ${d.player}")
        }
    }

    @Test
    fun `no seat is favoured in the deal`() {
        val players = 4
        val games = 8_000
        val wilds = IntArray(players)
        val points = LongArray(players)
        val firstToPlay = IntArray(players)
        repeat(games) { game ->
            val (state, events) = GameEngine.newGame(players, seed = 10_000L + game)
            for (d in events.filterIsInstance<GameEvent.Dealt>()) {
                wilds[d.player] += d.cards.count { it.isWild }
                points[d.player] += d.cards.points().toLong()
            }
            firstToPlay[(state.dealer + 1) % players]++
        }
        // Every seat should see about the same share of wilds and of points.
        for (seat in 0 until players) {
            assertTrue(abs(wilds[seat] - wilds.average()) / wilds.average() < 0.06, "wilds ${wilds.toList()}")
            assertTrue(abs(points[seat] - points.average()) / points.average() < 0.03, "points ${points.toList()}")
        }
        assertTrue(chiSquare(firstToPlay) < 16.27, "first to play ${firstToPlay.toList()}")
    }

    @Test
    fun `each round is shuffled from fresh randomness`() {
        val t = Table()
        val winning = t.red(Face.FIVE)
        val start = t.state(listOf(winning), listOf(t.blue(Face.ONE)), top = t.red(Face.TWO))
        val over = assertIs<EngineResult.Ok>(GameEngine.reduce(start, Action.PlayCard(0, winning.id))).state
        assertIs<Phase.RoundOver>(over.phase)

        fun nextHands(entropy: Long) =
            assertIs<EngineResult.Ok>(GameEngine.reduce(GameEngine.reseed(over, entropy), Action.StartNextRound)).state.players.map { it.hand }

        assertEquals(nextHands(1), nextHands(1))
        assertNotEquals(nextHands(1), nextHands(2))
    }

    private fun chiSquare(counts: IntArray): Double {
        val expected = counts.sum().toDouble() / counts.size
        return counts.sumOf { (it - expected) * (it - expected) / expected }
    }
}
