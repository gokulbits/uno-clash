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
                mode = GameMode.entries[random.nextInt(GameMode.entries.size)],
                targetScore = random.nextInt(100, 400),
                challengeDrawFour = random.nextBoolean(),
                stackDraws = random.nextBoolean(),
                jumpIn = random.nextBoolean(),
                sevenZero = random.nextBoolean(),
                swapHandsWild = random.nextBoolean(),
                drawUntilPlayable = random.nextBoolean(),
                powerFinishPenalty = listOf(0, 2, 4).random(random),
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
        val deckSize = Deck.sizeFor(s.rules)
        val ids = s.allCards().map { it.id }
        check(ids.size == deckSize) { "Card count is ${ids.size}" }
        check(ids.toSet() == (0 until deckSize).toSet()) { "Duplicate or missing cards" }
        check(s.discardPile.isNotEmpty()) { "Empty discard pile" }
        check(s.current in s.players.indices) { "Bad current seat ${s.current}" }
        check(s.pendingDraw >= 0) { "Negative pending draw" }
        check(s.players.all { it.score >= 0 })
        check(s.players.all { !it.knockedOut || it.hand.isEmpty() }) { "A knocked-out seat still holds cards" }
        check(s.rules.noMercy || (s.players.none { it.knockedOut } && s.setAside.isEmpty())) { "Knockouts outside No Mercy" }
        check(s.activeSeats.isNotEmpty()) { "Everyone is out" }

        val phase = s.phase
        if (phase is Phase.RoundOver || phase is Phase.GameOver) {
            val winner = if (phase is Phase.RoundOver) phase.winner else (phase as Phase.GameOver).winner
            // Either they went out, or the mercy rule left them the last one standing.
            check(s.hand(winner).isEmpty() || s.activeSeats == listOf(winner)) { "Winner still holds cards" }
            check(!s.players[winner].knockedOut) { "A knocked-out seat won" }
            check(s.pendingDraw == 0 && s.exposedPlayer == null)
            if (phase is Phase.GameOver) check(s.players[winner].score >= s.rules.targetScore)
            return
        }

        check(s.activeSeats.size >= 2) { "Round still going with one seat left" }
        check(!s.players[s.current].knockedOut) { "Turn sits on a knocked-out seat" }
        check(s.activeSeats.all { s.hand(it).isNotEmpty() }) { "Someone has no cards mid-round" }
        check(s.activeSeats.all { s.hand(it).size < s.rules.mercyLimit || !s.rules.noMercy }) { "Past the mercy limit" }
        check(phase is Phase.PickColor || s.activeColor != null) { "No active color" }
        when (phase) {
            is Phase.PickColor -> check(s.topCard.isWild) { "Picking a color for a non-wild" }
            is Phase.DrewPlayable -> check(s.hand(s.current).any { it.id == phase.cardId }) { "Drawn card not in hand" }
            Phase.PickSwapTarget -> check(
                (s.topCard.face == Face.SEVEN && s.rules.swaps) ||
                    (s.topCard.face == Face.WILD_SWAP_HANDS && s.rules.swapHands),
            ) { "Swapping on ${s.topCard}" }
            else -> Unit
        }
        if (s.pendingDraw > 0) {
            check(s.topCard.face.isDraw) { "Pending draw without a draw card" }
            check(phase == Phase.Turn || phase is Phase.PickColor) { "Pending draw in $phase" }
            check(s.rules.stacking || s.drawFourCheck != null || phase is Phase.PickColor) { "Pending draw nobody can respond to" }
            check(!s.rules.noMercy || s.pendingDrawValue > 0) { "Pending draw with no value to beat" }
        }
        s.drawFourCheck?.let {
            val card = s.topCard.face
            val expected = if (card == Face.WILD_DRAW_TWO) 2 else 4
            check(
                card in setOf(Face.WILD_DRAW_FOUR, Face.WILD_DRAW_TWO) &&
                    s.pendingDraw == expected && s.rules.challengeDrawFour,
            ) { "Stale draw four check on $card with ${s.pendingDraw} pending" }
        }
        s.exposedPlayer?.let {
            check(s.hand(it).size == 1 && !s.players[it].calledLastCard) { "Exposed player isn't on one uncalled card" }
        }
    }

    private companion object {
        const val MAX_STEPS = 50_000
    }
}
