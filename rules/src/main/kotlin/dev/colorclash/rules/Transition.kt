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

/** Mutable scratch copy of a [GameState] for applying one validated action. */
internal class Transition(state: GameState) {
    private val rules = state.rules
    private val hands = state.players.map { it.hand.toMutableList() }.toMutableList()
    private val scores = state.players.map { it.score }.toMutableList()
    private val called = state.players.map { it.calledLastCard }.toMutableList()
    private val knockedOut = state.players.map { it.knockedOut }.toMutableList()
    private val drawPile = state.drawPile.toMutableList()
    private val discard = state.discardPile.toMutableList()
    private val setAside = state.setAside.toMutableList()
    private var activeColor = state.activeColor
    private var side = state.side
    private var direction = state.direction
    private var current = state.current
    private var phase = state.phase
    private var pendingDraw = state.pendingDraw
    private var pendingDrawValue = state.pendingDrawValue
    private var drawFourCheck = state.drawFourCheck
    private var exposed = state.exposedPlayer
    private var knockouts = state.knockouts
    private var dealer = state.dealer
    private var round = state.round
    private val rng = Rng(state.rngState)
    private val events = mutableListOf<GameEvent>()

    private val playerCount get() = hands.size
    private val top get() = discard.last()
    private val finished get() = phase is Phase.RoundOver || phase is Phase.GameOver

    private fun activeSeats() = hands.indices.filter { !knockedOut[it] }

    fun result() = EngineResult.Ok(
        GameState(
            rules = rules,
            players = hands.indices.map { Player(hands[it].toList(), scores[it], called[it], knockedOut[it]) },
            drawPile = drawPile.toList(),
            discardPile = discard.toList(),
            activeColor = activeColor,
            side = side,
            direction = direction,
            current = current,
            phase = phase,
            pendingDraw = pendingDraw,
            pendingDrawValue = pendingDrawValue,
            drawFourCheck = drawFourCheck,
            exposedPlayer = exposed,
            setAside = setAside.toList(),
            knockouts = knockouts,
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

        // Mercy rule: knock everyone else out and the round is yours.
        val left = activeSeats()
        if (!finished && left.size == 1) finishRound(left.single())
    }

    fun startRound(round: Int, dealer: Int) {
        this.round = round
        this.dealer = dealer
        direction = Direction.CLOCKWISE
        pendingDraw = 0
        pendingDrawValue = 0
        drawFourCheck = null
        exposed = null
        hands.forEach { it.clear() }
        called.fill(false)
        knockedOut.fill(false)
        // Every round opens on the light side, whatever side the last one ended on.
        side = Side.LIGHT
        knockouts = 0
        setAside.clear()
        discard.clear()
        drawPile.clear()
        drawPile += Deck.forRules(rules, rng)
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

        // No Mercy opens on a number card; classic only refuses a Wild Draw 4.
        var starter = drawPile.removeAt(0)
        while (
            if (rules.noMercy) starter.face.isAction else starter.face in setOf(Face.WILD_DRAW_FOUR, Face.WILD_DRAW_TWO)
        ) {
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
            Face.DRAW_TWO, Face.DRAW_ONE -> {
                drawCards(first, starter.face.drawCount, DrawReason.DRAW_CARD)
                current = seatAfter(first)
            }
            // Turned up first, a Flip turns the game over and the first player matches what it became.
            Face.FLIP -> {
                turnEverythingOver()
                activeColor = top.color
                events += GameEvent.SideFlipped(side, top)
                // Its other side can be a wild, and then the first player names the color.
                if (top.isWild) phase = Phase.PickColor(keepTurn = true)
            }
            Face.WILD, Face.WILD_SWAP_HANDS -> phase = Phase.PickColor(keepTurn = true)
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

        // Dumps the rest of its color before anything else, so it can win the round on its own.
        if (card.face == Face.DISCARD_ALL) discardAll(player, checkNotNull(card.color))

        if (hand.isEmpty() && rules.powerFinish > 0 && card.face.isAction) {
            drawCards(player, rules.powerFinish, DrawReason.POWER_FINISH)
        }
        if (hand.isEmpty()) return goOut(player, card)
        // A call covers the play it was made for. Call early off a Discard All, play something else
        // instead, and it lapses: otherwise one call would cover the rest of the round.
        if (hand.size > 1) called[player] = false
        if (hand.size == 1 && !called[player]) exposed = player

        if (card.face.isDraw) pendingDrawValue = card.face.drawCount

        when (card.face) {
            Face.SKIP -> skipNext()
            Face.SKIP_EVERYONE -> events += GameEvent.EveryoneSkipped(player) // Still their turn.
            Face.REVERSE -> if (activeSeats().size == 2) {
                skipNext()
            } else {
                direction = direction.reversed()
                events += GameEvent.Reversed(direction)
                advance()
            }
            Face.DRAW_TWO, Face.DRAW_FOUR, Face.DRAW_ONE, Face.DRAW_FIVE -> {
                pendingDraw += card.face.drawCount
                passDrawPenalty()
            }
            Face.FLIP -> flipEverything(player)
            Face.WILD_DRAW_TWO -> {
                if (rules.canChallenge && pendingDraw == 0) {
                    drawFourCheck = DrawFourCheck(player, guilty = hand.any { it.color == colorBefore })
                }
                pendingDraw += 2
                phase = Phase.PickColor()
            }
            // You name the color; the next player draws until it turns up, however long that takes.
            Face.WILD_DRAW_COLOR -> phase = Phase.PickColor()
            Face.WILD, Face.WILD_SWAP_HANDS -> phase = Phase.PickColor()
            Face.WILD_DRAW_FOUR -> {
                // Only a fresh +4 can be challenged, not one stacked on another draw.
                if (rules.canChallenge && pendingDraw == 0) {
                    drawFourCheck = DrawFourCheck(player, guilty = hand.any { it.color == colorBefore })
                }
                pendingDraw += 4
                phase = Phase.PickColor()
            }
            Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN -> {
                pendingDraw += card.face.drawCount
                phase = Phase.PickColor()
            }
            Face.WILD_REVERSE_DRAW_FOUR -> {
                direction = direction.reversed()
                events += GameEvent.Reversed(direction)
                pendingDraw += 4
                phase = Phase.PickColor()
            }
            Face.WILD_COLOR_ROULETTE -> {
                // The player who gets hit names the color, so the turn passes before the pick.
                current = seatAfter(player)
                phase = Phase.PickColor(roulette = true)
            }
            Face.SEVEN -> if (rules.swaps) phase = Phase.PickSwapTarget else advance()
            Face.ZERO -> {
                if (rules.swaps) rotateHands()
                advance()
            }
            else -> advance()
        }
    }

    private fun chooseColor(player: Int, color: CardColor) {
        val pick = phase as Phase.PickColor
        activeColor = color
        events += GameEvent.WildColorChosen(player, color)
        phase = Phase.Turn
        when {
            pick.roulette -> spinRoulette(player, color)
            pick.keepTurn -> Unit
            // A wild the flip left on top: naming the color is the whole of it.
            pick.afterFlip -> advance(if (pick.bounced) 2 else 1)
            // Flip's Wild Draw Color: the next player keeps drawing until your color shows up.
            top.face == Face.WILD_DRAW_COLOR -> {
                current = seatAfter(player)
                spinRoulette(current, color)
            }
            // The swap comes after the color, and whoever played it chooses who to take from.
            top.face == Face.WILD_SWAP_HANDS -> phase = Phase.PickSwapTarget
            top.face.isDraw -> passDrawPenalty()
            else -> advance()
        }
    }

    /** Turns the pile over one card at a time until [color] shows up; the lot goes to [player]. */
    private fun spinRoulette(player: Int, color: CardColor) {
        val revealed = mutableListOf<Card>()
        while (true) {
            val card = drawOne() ?: break
            revealed += card
            if (card.color == color) break
        }
        events += GameEvent.ColorRoulette(player, color, revealed.size)
        receive(player, revealed, DrawReason.ROULETTE)
        advance()
    }

    /**
     * Turns the whole game over. The discard pile is flipped as a stack, so it comes out in reverse
     * order and the card just played ends up at the bottom — whatever was underneath is now the one
     * to match. Its action doesn't fire; a wild there is colored by whoever flipped.
     *
     * Played against a stack, the Flip throws it on: the next player takes the lot, on the new side,
     * and misses their turn.
     */
    private fun flipEverything(player: Int) {
        val bounced = pendingDraw
        turnEverythingOver()
        events += GameEvent.SideFlipped(side, top, bounced)
        activeColor = top.color
        if (bounced > 0) {
            pendingDraw = 0
            pendingDrawValue = 0
            drawCards(seatAfter(player), bounced, DrawReason.DRAW_CARD)
        }
        if (top.isWild) {
            // Nothing to match until the player who flipped names a color.
            phase = Phase.PickColor(afterFlip = true, bounced = bounced > 0)
            current = player
        } else {
            advance(if (bounced > 0) 2 else 1)
        }
    }

    /** The cards themselves: hands, both piles and the discard stack, which comes out reversed. */
    private fun turnEverythingOver() {
        side = if (side == Side.DARK) Side.LIGHT else Side.DARK
        for (hand in hands) {
            val turned = hand.map { it.flipped() }
            hand.clear()
            hand += turned
        }
        val turnedPile = drawPile.map { it.flipped() }
        drawPile.clear()
        drawPile += turnedPile
        val turnedAside = setAside.map { it.flipped() }
        setAside.clear()
        setAside += turnedAside
        val turnedDiscard = discard.map { it.flipped() }.reversed()
        discard.clear()
        discard += turnedDiscard
    }

    /** Everything else of that color goes under the Discard All card. */
    private fun discardAll(player: Int, color: CardColor) {
        val hand = hands[player]
        val extras = hand.filter { it.color == color }
        if (extras.isEmpty()) return
        hand.removeAll(extras)
        discard.addAll(discard.lastIndex, extras)
        events += GameEvent.DiscardedAll(player, color, extras)
    }

    /** Hands the pending draw to the next player, or applies it right away if they can't respond to it. */
    private fun passDrawPenalty() {
        // A Wild Reverse +4 at a table of two turns the draw back on whoever played it.
        val victim = if (top.face == Face.WILD_REVERSE_DRAW_FOUR && activeSeats().size == 2) {
            current
        } else {
            seatAfter(current)
        }
        if (rules.stackingOn(side) || drawFourCheck != null) {
            current = victim
            phase = Phase.Turn
            return
        }
        drawCards(victim, pendingDraw, DrawReason.DRAW_CARD)
        pendingDraw = 0
        pendingDrawValue = 0
        current = seatAfter(victim)
        phase = Phase.Turn
    }

    private fun draw(player: Int) {
        if (pendingDraw > 0) {
            drawCards(player, pendingDraw, DrawReason.DRAW_CARD)
            pendingDraw = 0
            pendingDrawValue = 0
            drawFourCheck = null
            advance()
            return
        }
        val limit = if (rules.drawsUntilPlayable) Int.MAX_VALUE else 1
        val drawn = mutableListOf<Card>()
        while (drawn.size < limit) {
            val card = drawOne() ?: break
            drawn += card
            if (matchesTop(card, top, activeColor)) break
        }
        receive(player, drawn, DrawReason.TURN)
        val last = drawn.lastOrNull()
        if (last != null && !knockedOut[player] && matchesTop(last, top, activeColor)) {
            phase = Phase.DrewPlayable(last.id)
        } else {
            advance()
        }
    }

    private fun challenge(player: Int) {
        val check = checkNotNull(drawFourCheck)
        drawFourCheck = null
        events += Challenged(player, check.offender, succeeded = check.guilty)
        if (check.guilty) {
            // Offender takes the cards, challenger carries on with a normal turn.
            drawCards(check.offender, pendingDraw, DrawReason.CHALLENGE)
            pendingDraw = 0
            pendingDrawValue = 0
            phase = Phase.Turn
        } else {
            drawCards(player, pendingDraw + 2, DrawReason.CHALLENGE)
            pendingDraw = 0
            pendingDrawValue = 0
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
        checkMercy(player, target)
        advance()
    }

    private fun rotateHands() {
        val seats = activeSeats()
        if (seats.size < 2) return
        val before = seats.map { hands[it] }
        seats.forEachIndexed { i, seat -> hands[seatAfter(seat)] = before[i] }
        called.fill(false)
        exposed = null
        events += GameEvent.HandsRotated(direction)
        checkMercy(*seats.toIntArray())
    }

    private fun goOut(player: Int, card: Card) {
        exposed = null
        // A draw card as the last card still hits the next player, and those cards count toward points.
        val finalDraw = if (card.face.isDraw) pendingDraw + card.face.drawCount else 0
        pendingDraw = 0
        pendingDrawValue = 0
        drawFourCheck = null
        if (finalDraw > 0) drawCards(seatAfter(player), finalDraw, DrawReason.DRAW_CARD)
        finishRound(player)
    }

    private fun finishRound(winner: Int) {
        exposed = null
        pendingDraw = 0
        pendingDrawValue = 0
        drawFourCheck = null
        // Flip scores the side the round ended on, which is the side every hand is showing.
        val inHands = hands.indices.filter { it != winner && !knockedOut[it] }.sumOf { hands[it].pointsIn(rules.mode) }
        val bonus = if (rules.noMercy) knockouts * rules.knockoutBonus else 0
        val points = inHands + bonus
        scores[winner] += points
        events += GameEvent.RoundWon(winner, points, scores[winner])
        phase = if (scores[winner] >= rules.targetScore) {
            events += GameEvent.GameWon(winner)
            Phase.GameOver(winner)
        } else {
            Phase.RoundOver(winner, points)
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
        checkMercy(player)
    }

    private fun checkMercy(vararg seats: Int) {
        if (!rules.noMercy) return
        for (seat in seats) if (!knockedOut[seat] && hands[seat].size >= rules.mercyLimit) knockOut(seat)
    }

    /** The mercy rule. Their hand waits in [setAside] until the pile needs refilling. */
    private fun knockOut(player: Int) {
        // Somebody has to be left to take the round.
        if (activeSeats().size <= 1) return
        val hand = hands[player]
        events += GameEvent.KnockedOut(player, hand.size)
        setAside += hand
        hand.clear()
        knockedOut[player] = true
        called[player] = false
        knockouts++
        if (exposed == player) exposed = null
    }

    /**
     * Takes the top card, refilling from the discard pile (minus its top) and any knocked-out hands
     * first if needed. Null if every card is in a hand.
     */
    private fun drawOne(): Card? {
        if (drawPile.isEmpty() && (discard.size > 1 || setAside.isNotEmpty())) {
            if (discard.size > 1) {
                val keep = discard.removeAt(discard.lastIndex)
                drawPile += discard
                discard.clear()
                discard += keep
            }
            drawPile += setAside
            setAside.clear()
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

    private fun seatAfter(seat: Int, steps: Int = 1) =
        nextSeat(seat, steps, direction, playerCount) { knockedOut[it] }
}
