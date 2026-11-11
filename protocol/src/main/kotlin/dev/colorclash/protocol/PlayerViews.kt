package dev.colorclash.protocol

import dev.colorclash.rules.Action
import dev.colorclash.rules.Card
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameState
import dev.colorclash.rules.Phase

/**
 * A card as it goes over the wire. In Flip the side a card isn't showing belongs to whoever it faces,
 * so it's stripped out: you learn your own cards' other side by watching what you're facing people with.
 */
internal fun Card.shown(): Card = if (back == null) this else copy(back = null)

internal fun List<Card>.shown(): List<Card> = if (none { it.back != null }) this else map { it.shown() }

/** Whether a view from the network can be drawn at all: every seat it points at exists. */
fun PlayerView.isWellFormed(): Boolean {
    val seated = seats.indices
    return you in seated && current in seated && (exposedPlayer == null || exposedPlayer in seated)
}

/**
 * Builds what [you] may see of [this] state. The only cards included are the viewer's own hand
 * and the top of the discard pile.
 */
fun GameState.viewFor(
    you: Int,
    seats: List<SeatInfo>,
    settings: GameSettings,
    turnMillisLeft: Long? = null,
): PlayerView {
    require(seats.size == playerCount) { "Seat info doesn't match the game" }
    val hand = hand(you)
    fun allowed(action: Action) = GameEngine.validate(this, action) == null

    return PlayerView(
        you = you,
        hand = hand.shown(),
        playable = hand.filter { GameEngine.isPlayable(this, you, it) }.map { it.id }.toSet(),
        seats = players.mapIndexed { i, p ->
            SeatView(
                seats[i], p.hand.size, p.score, p.calledLastCard, p.knockedOut,
                // Flip turns every hand outward: the side facing the table is public, the ids aren't.
                facing = if (rules.flip && i != you) p.hand.mapNotNull { it.back } else emptyList(),
            )
        },
        topCard = topCard.shown(),
        activeColor = activeColor,
        side = side,
        direction = direction,
        current = current,
        phase = when (phase) {
            Phase.Turn -> PhaseKind.TURN
            is Phase.PickColor -> PhaseKind.PICK_COLOR
            is Phase.DrewPlayable -> PhaseKind.DREW_PLAYABLE
            Phase.PickSwapTarget -> PhaseKind.PICK_SWAP_TARGET
            is Phase.RoundOver -> PhaseKind.ROUND_OVER
            is Phase.GameOver -> PhaseKind.GAME_OVER
        },
        drawnCardId = (phase as? Phase.DrewPlayable)?.cardId?.takeIf { current == you },
        winner = when (val p = phase) {
            is Phase.RoundOver -> p.winner
            is Phase.GameOver -> p.winner
            else -> null
        },
        roundPoints = (phase as? Phase.RoundOver)?.points,
        pendingDraw = pendingDraw,
        pendingDrawValue = pendingDrawValue,
        roulette = (phase as? Phase.PickColor)?.roulette == true,
        exposedPlayer = exposedPlayer,
        drawPileCount = drawPile.size,
        round = round,
        dealer = dealer,
        settings = settings,
        canDraw = allowed(Action.Draw(you)),
        canPass = allowed(Action.Pass(you)),
        canCallLastCard = allowed(Action.CallLastCard(you)),
        canChallenge = allowed(Action.ChallengeDrawFour(you)),
        turnMillisLeft = turnMillisLeft,
    )
}
