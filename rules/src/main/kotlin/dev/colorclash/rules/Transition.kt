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
import dev.colorclash.rules.GameEvent.Challenged
import dev.colorclash.rules.GameEvent.Drew
import dev.colorclash.rules.GameEvent.Skipped

/**
 * Mutable scratch copy of a [GameState] used to apply one already-validated action.
 * Keeps the reducer readable; nothing here escapes as mutable.
 */
internal class Transition(state: GameState) {
    private val rules = state.rules
    private val hands = state.players.map { it.hand.toMutableList() }.toMutableList()
    private val scores = state.players.map { it.score }.toMutableList()
    private val called = state.players.map { it.calledLastCard }.toMutableList()
    private val drawPile = state.drawPile.toMutableList()
    private val discard = state.discardPile.toMutableList()
    private var activeColor = state.activeColor
    private var direction = state.direction
    private var current = state.current
    private var phase = state.phase
    private var pendingDraw = state.pendingDraw
    private var drawFourCheck = state.drawFourCheck
    private var exposed = state.exposedPlayer
    private var dealer = state.dealer
    private var round = state.round
    private val rng = Rng(state.rngState)
    private val events = mutableListOf<GameEvent>()

    private val playerCount get() = hands.size
    private val top get() = discard.last()

    fun result() = EngineResult.Ok(
        GameState(
            rules = rules,
            players = hands.indices.map { Player(hands[it].toList(), scores[it], called[it]) },
            drawPile = drawPile.toList(),
            discardPile = discard.toList(),
            activeColor = activeColor,
            direction = direction,
            current = current,
            phase = phase,
            pendingDraw = pendingDraw,
            drawFourCheck = drawFourCheck,
            exposedPlayer = exposed,
            dealer = dealer,
            round = round,
            rngState = rng.state,
        ),
        events.toList(),
    )

    fun perform(action: Action) {
        // Once someone else takes a turn, the window to catch a missed UNO call closes.
        val startsTurn = action is PlayCard || action is Draw || action is Pass || action is ChallengeDrawFour
        if (startsTurn && exposed != action.player) exposed = null

        when (action) {
            is PlayCard -> play(action.player, action.cardId)
            is ChooseColor -> chooseColor(action.player, action.color)
            is Draw -> draw(action.player)
            is Pass -> advance()
            is CallLastCard -> callLastCard(action.player)
            is CatchLastCard -> catchLastCard(action.player, action.target)
            is ChallengeDrawFour -> challenge(action.player)
            is SwapWith -> swapHands(action.player, action.target)
            StartNextRound -> startRound(round + 1, (dealer + 1) % playerCount)
        }
    }

    fun startRound(round: Int, dealer: Int) {
        this.round = round
        this.dealer = dealer
        direction = Direction.CLOCKWISE
        pendingDraw = 0
        drawFourCheck = null
        exposed = null
        hands.forEach { it.clear() }
        called.fill(false)
        discard.clear()
        drawPile.clear()
        drawPile += Deck.standard()
        rng.shuffle(drawPile)
        events += GameEvent.RoundStarted(round, dealer)

        // Dealt like at a real table: one card at a time, round and round, starting left of the dealer.
        val first = seatAfter(dealer)
        repeat(rules.handSize) {
            for (i in 0 until playerCount) hands[seatAfter(first, i)] += drawPile.removeAt(0)
        }
        for (i in 0 until playerCount) {
            val seat = seatAfter(first, i)
            events += GameEvent.Dealt(seat, hands[seat].toList())
        }

        // A Wild Draw 4 can't start a round; bury it and flip again.
        var starter = drawPile.removeAt(0)
        while (starter.face == Face.WILD_DRAW_FOUR) {
            drawPile += starter
            rng.shuffle(drawPile)
            starter = drawPile.removeAt(0)
        }
        discard += starter
        activeColor = starter.color
        events += GameEvent.StarterFlipped(starter)

        current = first
        phase = Phase.Turn
        when (starter.face) {
            Face.SKIP -> {
                events += Skipped(first)
                current = seatAfter(first)
            }
            Face.REVERSE -> {
                if (playerCount == 2) {
                    events += Skipped(first)
                } else {
                    direction = direction.reversed()
                    events += GameEvent.Reversed(direction)
                }
                current = dealer
            }
            Face.DRAW_TWO -> {
                drawCards(first, 2, DrawReason.DRAW_CARD)
                current = seatAfter(first)
            }
            Face.WILD -> phase = Phase.PickColor(keepTurn = true)
            else -> Unit
        }
    }

    private fun play(player: Int, cardId: Int) {
        val hand = hands[player]
        val card = hand.removeAt(hand.indexOfFirst { it.id == cardId })
        val colorBefore = activeColor
        discard += card
        if (!card.isWild) activeColor = card.color
        events += GameEvent.CardPlayed(player, card, jumpIn = player != current)
        current = player
        phase = Phase.Turn
        drawFourCheck = null

        if (hand.isEmpty() && rules.powerFinishPenalty > 0 && card.face.number == null) {
            // This table doesn't let you go out on a power card: it still works, but you draw instead of winning.
            drawCards(player, rules.powerFinishPenalty, DrawReason.POWER_FINISH)
        }
        if (hand.isEmpty()) return goOut(player, card)
        if (hand.size == 1 && !called[player]) exposed = player

        when (card.face) {
            Face.SKIP -> skipNext()
            Face.REVERSE -> if (playerCount == 2) {
                skipNext()
            } else {
                direction = direction.reversed()
                events += GameEvent.Reversed(direction)
                advance()
            }
            Face.DRAW_TWO -> {
                pendingDraw += 2
                passDrawPenalty()
            }
            Face.WILD -> phase = Phase.PickColor()
            Face.WILD_DRAW_FOUR -> {
                // Only a fresh +4 can be challenged, not one stacked on another draw.
                if (rules.challengeDrawFour && pendingDraw == 0) {
                    drawFourCheck = DrawFourCheck(player, guilty = hand.any { it.color == colorBefore })
                }
                pendingDraw += 4
                phase = Phase.PickColor()
            }
            Face.SEVEN -> if (rules.sevenZero) phase = Phase.PickSwapTarget else advance()
            Face.ZERO -> {
                if (rules.sevenZero) rotateHands()
                advance()
            }
            else -> advance()
        }
    }

    private fun chooseColor(player: Int, color: CardColor) {
        val keepTurn = (phase as Phase.PickColor).keepTurn
        activeColor = color
        events += GameEvent.WildColorChosen(player, color)
        phase = Phase.Turn
        when {
            keepTurn -> Unit
            top.face == Face.WILD_DRAW_FOUR -> passDrawPenalty()
            else -> advance()
        }
    }

    /** Hands the pending draw to the next player, or applies it right away if they can't respond to it. */
    private fun passDrawPenalty() {
        val victim = seatAfter(current)
        if (rules.stackDraws || drawFourCheck != null) {
            current = victim
            phase = Phase.Turn
            return
        }
        drawCards(victim, pendingDraw, DrawReason.DRAW_CARD)
        pendingDraw = 0
        advance(2)
    }

    private fun draw(player: Int) {
        if (pendingDraw > 0) {
            drawCards(player, pendingDraw, DrawReason.DRAW_CARD)
            pendingDraw = 0
            drawFourCheck = null
            advance()
            return
        }
        val limit = if (rules.drawUntilPlayable) Int.MAX_VALUE else 1
        val drawn = mutableListOf<Card>()
        while (drawn.size < limit) {
            val card = drawOne() ?: break
            drawn += card
            if (matchesTop(card, top, activeColor)) break
        }
        receive(player, drawn, DrawReason.TURN)
        val last = drawn.lastOrNull()
        if (last != null && matchesTop(last, top, activeColor)) phase = Phase.DrewPlayable(last.id) else advance()
    }

    private fun challenge(player: Int) {
        val check = checkNotNull(drawFourCheck)
        drawFourCheck = null
        events += Challenged(player, check.offender, succeeded = check.guilty)
        if (check.guilty) {
            // Offender takes the cards, challenger carries on with a normal turn.
            drawCards(check.offender, pendingDraw, DrawReason.CHALLENGE)
            pendingDraw = 0
            phase = Phase.Turn
        } else {
            drawCards(player, pendingDraw + 2, DrawReason.CHALLENGE)
            pendingDraw = 0
            advance()
        }
    }

    private fun callLastCard(player: Int) {
        called[player] = true
        if (exposed == player) exposed = null
        events += GameEvent.LastCardCalled(player)
    }

    private fun catchLastCard(by: Int, target: Int) {
        exposed = null
        events += GameEvent.PenaltyApplied(target, by, rules.lastCardPenalty)
        drawCards(target, rules.lastCardPenalty, DrawReason.MISSED_LAST_CARD)
    }

    private fun swapHands(player: Int, target: Int) {
        val mine = hands[player]
        hands[player] = hands[target]
        hands[target] = mine
        called[player] = false
        called[target] = false
        if (exposed == player || exposed == target) exposed = null
        events += GameEvent.HandsSwapped(player, target)
        advance()
    }

    private fun rotateHands() {
        val before = hands.toList()
        for (seat in before.indices) hands[seatAfter(seat)] = before[seat]
        called.fill(false)
        exposed = null
        events += GameEvent.HandsRotated(direction)
    }

    private fun goOut(player: Int, card: Card) {
        exposed = null
        // A draw card as the last card still hits the next player, and those cards count toward points.
        val finalDraw = when (card.face) {
            Face.DRAW_TWO -> pendingDraw + 2
            Face.WILD_DRAW_FOUR -> pendingDraw + 4
            else -> 0
        }
        pendingDraw = 0
        drawFourCheck = null
        if (finalDraw > 0) drawCards(seatAfter(player), finalDraw, DrawReason.DRAW_CARD)

        val points = hands.indices.filter { it != player }.sumOf { hands[it].points() }
        scores[player] += points
        events += GameEvent.RoundWon(player, points, scores[player])
        phase = if (scores[player] >= rules.targetScore) {
            events += GameEvent.GameWon(player)
            Phase.GameOver(player)
        } else {
            Phase.RoundOver(player, points)
        }
    }

    private fun drawCards(player: Int, count: Int, reason: DrawReason) {
        val drawn = mutableListOf<Card>()
        repeat(count) { drawOne()?.let { drawn += it } }
        receive(player, drawn, reason)
    }

    private fun receive(player: Int, cards: List<Card>, reason: DrawReason) {
        hands[player] += cards
        if (cards.isNotEmpty()) called[player] = false
        if (exposed == player && hands[player].size != 1) exposed = null
        events += Drew(player, cards, reason)
    }

    /** Takes the top card, reshuffling the discard pile (minus its top) first if needed. Null if every card is in a hand. */
    private fun drawOne(): Card? {
        if (drawPile.isEmpty() && discard.size > 1) {
            val keep = discard.removeAt(discard.lastIndex)
            drawPile += discard
            discard.clear()
            discard += keep
            rng.shuffle(drawPile)
            events += GameEvent.DeckReshuffled(drawPile.size)
        }
        return if (drawPile.isEmpty()) null else drawPile.removeAt(0)
    }

    private fun skipNext() {
        events += Skipped(seatAfter(current))
        advance(2)
    }

    private fun advance(steps: Int = 1) {
        current = seatAfter(current, steps)
        phase = Phase.Turn
    }

    private fun seatAfter(seat: Int, steps: Int = 1) = (seat + direction.step * steps).mod(playerCount)
}
