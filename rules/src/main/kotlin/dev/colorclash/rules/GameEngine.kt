package dev.colorclash.rules

import dev.colorclash.rules.Action.CallLastCard
import dev.colorclash.rules.Action.CatchLastCard
import dev.colorclash.rules.Action.ChallengeDrawFour
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.Pass
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Action.StartNextRound
import dev.colorclash.rules.Action.SwapWith

object GameEngine {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6

    fun newGame(playerCount: Int, rules: RuleSet = RuleSet(), seed: Long): EngineResult.Ok {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) { "Need $MIN_PLAYERS-$MAX_PLAYERS players" }
        val rng = Rng(seed)
        val dealer = rng.nextInt(playerCount)
        val blank = GameState(
            rules = rules,
            players = List(playerCount) { Player(hand = emptyList()) },
            drawPile = emptyList(),
            discardPile = emptyList(),
            activeColor = null,
            direction = Direction.CLOCKWISE,
            current = 0,
            phase = Phase.Turn,
            dealer = dealer,
            round = 0,
            rngState = rng.state,
        )
        return Transition(blank).also { it.startRound(round = 1, dealer = dealer) }.result()
    }

    /**
     * Mixes fresh randomness into the shuffles still to come. The host does this before every
     * round, so no single seed decides a whole game, and the engine itself stays deterministic.
     */
    fun reseed(state: GameState, entropy: Long): GameState = state.copy(rngState = state.rngState xor entropy)

    fun reduce(state: GameState, action: Action): EngineResult {
        validate(state, action)?.let { return EngineResult.Rejected(it) }
        return Transition(state).also { it.perform(action) }.result()
    }

    /** Null when [action] is legal, otherwise a short reason. */
    fun validate(state: GameState, action: Action): String? {
        val phase = state.phase
        if (phase is Phase.GameOver) return "Game is over"
        if (action is StartNextRound) return if (phase is Phase.RoundOver) null else "Round still in progress"
        if (phase is Phase.RoundOver) return "Round is over"

        action as PlayerAction
        val p = action.player
        if (p !in state.players.indices) return "No such player"
        val isCurrent = p == state.current

        return when (action) {
            is PlayCard -> validatePlay(state, p, action.cardId)
            is ChooseColor -> when {
                phase !is Phase.PickColor -> "Not picking a color"
                !isCurrent -> "Not your pick"
                else -> null
            }
            is Draw -> when {
                phase != Phase.Turn -> "Can't draw now"
                !isCurrent -> "Not your turn"
                else -> null
            }
            is Pass -> when {
                phase !is Phase.DrewPlayable -> "Nothing to pass on"
                !isCurrent -> "Not your turn"
                else -> null
            }
            is CallLastCard -> when {
                // Like at a real table: you call it as you play your second-to-last card, not whenever.
                !isCurrent -> "Call UNO on your turn"
                state.hand(p).size > 2 -> "Too many cards to call"
                state.players[p].calledLastCard -> "Already called"
                else -> null
            }
            is CatchLastCard -> when {
                action.target == p -> "Can't catch yourself"
                action.target != state.exposedPlayer -> "Nobody to catch"
                else -> null
            }
            is ChallengeDrawFour -> when {
                phase != Phase.Turn || !isCurrent -> "Not your turn"
                state.drawFourCheck == null -> "Nothing to challenge"
                else -> null
            }
            is SwapWith -> when {
                phase != Phase.PickSwapTarget || !isCurrent -> "Not swapping"
                action.target == p || action.target !in state.players.indices -> "Pick another player"
                else -> null
            }
        }
    }

    fun legalActions(state: GameState): List<Action> =
        candidates(state, state.players.indices).filter { validate(state, it) == null }

    fun legalActions(state: GameState, player: Int): List<PlayerAction> =
        candidates(state, player..player).filterIsInstance<PlayerAction>().filter { validate(state, it) == null }

    fun isPlayable(state: GameState, player: Int, card: Card): Boolean =
        validate(state, PlayCard(player, card.id)) == null

    private fun validatePlay(state: GameState, p: Int, cardId: Int): String? {
        val card = state.hand(p).find { it.id == cardId } ?: return "You don't have that card"
        when (val phase = state.phase) {
            is Phase.DrewPlayable ->
                return if (p == state.current && cardId == phase.cardId) null else "Only the drawn card can be played"
            Phase.Turn -> Unit
            else -> return "Can't play now"
        }
        if (p != state.current) return if (canJumpIn(state, card)) null else "Not your turn"
        return if (canPlayOnTurn(state, card)) null else "Card doesn't match"
    }

    private fun canPlayOnTurn(state: GameState, card: Card): Boolean {
        if (state.pendingDraw > 0) {
            if (!state.rules.stackDraws) return false
            return card.face == Face.WILD_DRAW_FOUR ||
                (card.face == Face.DRAW_TWO && state.topCard.face == Face.DRAW_TWO)
        }
        return matchesTop(card, state.topCard, state.activeColor)
    }

    private fun canJumpIn(state: GameState, card: Card): Boolean =
        state.rules.jumpIn && state.pendingDraw == 0 && !card.isWild && card.isIdenticalTo(state.topCard)

    private fun candidates(state: GameState, seats: IntRange): List<Action> = buildList {
        add(StartNextRound)
        for (p in seats) {
            state.hand(p).forEach { add(PlayCard(p, it.id)) }
            CardColor.entries.forEach { add(ChooseColor(p, it)) }
            add(Draw(p))
            add(Pass(p))
            add(CallLastCard(p))
            add(ChallengeDrawFour(p))
            for (t in state.players.indices) {
                if (t == p) continue
                add(CatchLastCard(p, t))
                add(SwapWith(p, t))
            }
        }
    }
}

internal fun matchesTop(card: Card, top: Card, activeColor: CardColor?): Boolean = when {
    card.isWild -> true
    card.color == activeColor -> true
    else -> !top.isWild && card.face == top.face
}
