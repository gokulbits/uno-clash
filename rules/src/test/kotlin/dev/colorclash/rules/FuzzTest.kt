package dev.colorclash.rules

import dev.colorclash.rules.Action.StartNextRound
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Plays thousands of random games and checks invariants after every action.
 * Run more with: ./gradlew :rules:test -PfuzzGames=20000
 */
class FuzzTest {
    private val games = System.getProperty("fuzz.games")?.toInt() ?: 2000

    @Test
    fun `random games keep every invariant and always finish`() {
        repeat(games) { game ->
            val random = Random(game)
            val players = random.nextInt(GameEngine.MIN_PLAYERS, GameEngine.MAX_PLAYERS + 1)
            val rules = RuleSet(
                targetScore = random.nextInt(100, 400),
                challengeDrawFour = random.nextBoolean(),
                stackDraws = random.nextBoolean(),
                jumpIn = random.nextBoolean(),
                sevenZero = random.nextBoolean(),
                drawUntilPlayable = random.nextBoolean(),
            )
            try {
                playOut(GameEngine.newGame(players, rules, seed = game.toLong()).state, random)
            } catch (e: AssertionError) {
                throw AssertionError("Game $game ($players players, $rules): ${e.message}", e)
            }
        }
    }

    @Test
    fun `same seeds replay the same game`() {
        fun finalState(seed: Int) = playOut(GameEngine.newGame(4, RuleSet(targetScore = 200), seed.toLong()).state, Random(seed))
        assertEquals(finalState(11), finalState(11))
    }

    private fun playOut(start: GameState, random: Random): GameState {
        val bot = SimpleBot(random)
        var state = start
        checkInvariants(state)
        var steps = 0
        while (state.phase !is Phase.GameOver) {
            if (++steps > MAX_STEPS) fail("Game didn't finish in $MAX_STEPS actions")
            val action = pickAction(state, bot, random)
            when (val result = GameEngine.reduce(state, action)) {
                is EngineResult.Ok -> state = result.state
                is EngineResult.Rejected -> fail("Legal action $action was rejected: ${result.reason}")
            }
            checkInvariants(state)
        }
        return state
    }

    /** Mostly sensible bot moves so games end, mixed with any legal action (jump-ins, catches, odd draws...). */
    private fun pickAction(state: GameState, bot: SimpleBot, random: Random): Action {
        if (state.phase is Phase.RoundOver) return StartNextRound
        if (random.nextDouble() < 0.75) {
            bot.chooseAction(state, state.current)?.let { return it }
        }
        val legal = GameEngine.legalActions(state)
        if (legal.isEmpty()) fail("No legal actions in $state")
        return legal.random(random)
    }

    private fun checkInvariants(s: GameState) {
        val ids = s.allCards().map { it.id }
        check(ids.size == Deck.SIZE) { "Card count is ${ids.size}" }
        check(ids.toSet() == (0 until Deck.SIZE).toSet()) { "Duplicate or missing cards" }
        check(s.discardPile.isNotEmpty()) { "Empty discard pile" }
        check(s.current in s.players.indices) { "Bad current seat ${s.current}" }
        check(s.pendingDraw >= 0) { "Negative pending draw" }
        check(s.players.all { it.score >= 0 })

        val phase = s.phase
        if (phase is Phase.RoundOver || phase is Phase.GameOver) {
            val winner = if (phase is Phase.RoundOver) phase.winner else (phase as Phase.GameOver).winner
            check(s.hand(winner).isEmpty()) { "Winner still holds cards" }
            check(s.pendingDraw == 0 && s.exposedPlayer == null)
            if (phase is Phase.GameOver) check(s.players[winner].score >= s.rules.targetScore)
            return
        }

        check(s.players.all { it.hand.isNotEmpty() }) { "Someone has no cards mid-round" }
        check(phase is Phase.PickColor || s.activeColor != null) { "No active color" }
        when (phase) {
            is Phase.PickColor -> check(s.topCard.isWild) { "Picking a color for a non-wild" }
            is Phase.DrewPlayable -> check(s.hand(s.current).any { it.id == phase.cardId }) { "Drawn card not in hand" }
            Phase.PickSwapTarget -> check(s.topCard.face == Face.SEVEN && s.rules.sevenZero)
            else -> Unit
        }
        if (s.pendingDraw > 0) {
            check(s.topCard.face == Face.DRAW_TWO || s.topCard.face == Face.WILD_DRAW_FOUR) { "Pending draw without a draw card" }
            check(phase == Phase.Turn || phase is Phase.PickColor) { "Pending draw in $phase" }
            check(s.rules.stackDraws || s.drawFourCheck != null || phase is Phase.PickColor) { "Pending draw nobody can respond to" }
        }
        s.drawFourCheck?.let {
            check(s.topCard.face == Face.WILD_DRAW_FOUR && s.pendingDraw == 4 && s.rules.challengeDrawFour) { "Stale draw four check" }
        }
        s.exposedPlayer?.let {
            check(s.hand(it).size == 1 && !s.players[it].calledLastCard) { "Exposed player isn't on one uncalled card" }
        }
    }

    private companion object {
        const val MAX_STEPS = 50_000
    }
}
