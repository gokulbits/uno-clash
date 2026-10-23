package dev.colorclash.app.ui.game.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import dev.colorclash.app.feedback.Buzz
import dev.colorclash.app.feedback.Feedback
import dev.colorclash.app.feedback.Sfx
import dev.colorclash.app.feedback.YOUR_TURN_CUE
import dev.colorclash.app.feedback.cueFor
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.rules.Card
import dev.colorclash.rules.Direction
import dev.colorclash.rules.Face
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/** Places on screen that effects fly between. */
sealed interface Anchor {
    data class Seat(val seat: Int) : Anchor
    data class HandCard(val cardId: Int) : Anchor
    data object DrawPile : Anchor
    data object DiscardPile : Anchor
}

class Flight(val card: Card?, val from: Offset, val to: Offset, val endRotation: Float, val startScale: Float) {
    val progress = Animatable(0f)
}

class Flash(val at: Offset, val text: String, val color: Color) {
    val progress = Animatable(0f)
}

class Burst(val at: Offset, val color: Color) {
    val progress = Animatable(0f)
}

/**
 * Turns host updates into animations, one update at a time. The table shows [displayed], which
 * only moves on once an update's events have played, so effects never trip over each other.
 * If updates pile up (a burst of bot moves, a resync) it skips the effects and catches up.
 */
@Stable
class TableFx {
    var displayed by mutableStateOf<PlayerView?>(null)
        private set

    val flights = mutableStateListOf<Flight>()
    val flashes = mutableStateListOf<Flash>()
    val bursts = mutableStateListOf<Burst>()
    /** Cards of yours that are mid-flight to the pile, so they don't show in the hand twice. */
    val hiddenCards = mutableStateListOf<Int>()

    val arrowSpin = Animatable(0f)
    val shake = Animatable(0f)
    val penaltyGlow = Animatable(0f)
    var confettiBursts by mutableIntStateOf(0)
        private set
    var bigConfetti by mutableStateOf(false)
        private set

    // Written during layout, read only by the effects coroutine, both on the main thread, so no snapshot state needed.
    private val anchors = HashMap<Anchor, Rect>()
    private var layerOrigin = Offset.Zero
    private val queue = Channel<ServerMessage.Game>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    fun submit(update: ServerMessage.Game) {
        pending.incrementAndGet()
        queue.trySend(update)
    }

    fun anchor(anchor: Anchor): Modifier = Modifier.onGloballyPositioned { anchors[anchor] = it.boundsInWindow() }

    fun layer(): Modifier = Modifier.onGloballyPositioned { layerOrigin = it.boundsInWindow().topLeft }

    /** Runs for as long as the room screen is up. Must be launched from composition so animations have a frame clock. */
    suspend fun run(feedback: Feedback) {
        for (update in queue) {
            val backlog = pending.decrementAndGet()
            if (backlog < MAX_BACKLOG && update.events.isNotEmpty()) {
                play(update, feedback)
            }
            commit(update.view, feedback)
        }
    }

    /** Back in the lobby: forget the last table so the next game doesn't flash the old one. */
    fun reset() {
        displayed = null
        hiddenCards.clear()
    }

    private suspend fun play(update: ServerMessage.Game, feedback: Feedback) {
        val view = update.view
        if (update.events.any { it is EventView.RoundStarted }) {
            // Empty hands while the cards fly out, instead of last round's scoreboard.
            displayed = preDeal(view)
            awaitTable()
        }
        var dealt = false
        for (event in update.events) {
            if (event is EventView.Dealt) {
                if (!dealt) deal(update.events.filterIsInstance<EventView.Dealt>(), feedback)
                dealt = true
                continue
            }
            feedback.cue(cueFor(event, view.you))
            animate(event, view)
        }
    }

    private fun commit(view: PlayerView, feedback: Feedback) {
        val before = displayed
        displayed = view
        hiddenCards.clear()
        val turnStarts = view.isYourTurn && view.phase == PhaseKind.TURN &&
            (before == null || !before.isYourTurn || before.round != view.round || before.phase != PhaseKind.TURN)
        if (turnStarts) feedback.cue(YOUR_TURN_CUE)
    }

    private suspend fun animate(event: EventView, view: PlayerView) {
        when (event) {
            is EventView.RoundStarted -> delay(350)
            is EventView.StarterFlipped -> fly(event.card, Anchor.DrawPile, Anchor.DiscardPile, 320, discardTilt(event.card), startScale = 0.7f)
            is EventView.CardPlayed -> {
                val from = if (event.player == view.you) Anchor.HandCard(event.card.id) else Anchor.Seat(event.player)
                if (event.player == view.you) hiddenCards += event.card.id
                fly(event.card, from, Anchor.DiscardPile, 320, discardTilt(event.card), startScale = 0.75f)
                if (event.card.face == Face.DRAW_TWO || event.card.face == Face.WILD_DRAW_FOUR) shakeTable()
            }
            is EventView.Drew -> coroutineScope {
                repeat(event.count.coerceAtMost(MAX_DRAW_FLIGHTS)) { i ->
                    launch {
                        delay(i * 70L)
                        fly(null, Anchor.DrawPile, Anchor.Seat(event.player), 260, 0f, startScale = 0.7f)
                    }
                }
            }
            is EventView.Skipped -> flash(Anchor.Seat(event.player), "⊘", GameTheme.red)
            is EventView.Reversed -> {
                val turn = if (event.direction == Direction.CLOCKWISE) 360f else -360f
                arrowSpin.animateTo(arrowSpin.value + turn, tween(550, easing = FastOutSlowInEasing))
            }
            is EventView.WildColorChosen -> burst(Anchor.DiscardPile, GameTheme.cardColor(event.color))
            is EventView.LastCardCalled -> flash(Anchor.Seat(event.player), "LAST CARD!", GameTheme.yellow)
            is EventView.PenaltyApplied -> coroutineScope {
                launch { flash(Anchor.Seat(event.player), "+${event.cards}", GameTheme.red) }
                if (event.player == view.you) launch { glowRed() }
            }
            is EventView.Challenged -> flash(
                Anchor.Seat(event.offender),
                if (event.succeeded) "Bluff!" else "Legit!",
                if (event.succeeded) GameTheme.red else GameTheme.green,
            )
            is EventView.HandsSwapped -> coroutineScope {
                launch { fly(null, Anchor.Seat(event.player), Anchor.Seat(event.target), 420, 0f, startScale = 0.7f) }
                launch { fly(null, Anchor.Seat(event.target), Anchor.Seat(event.player), 420, 0f, startScale = 0.7f) }
            }
            is EventView.HandsRotated -> flash(Anchor.DiscardPile, "Pass hands!", GameTheme.blue)
            is EventView.DeckReshuffled -> flash(Anchor.DrawPile, "Shuffled!", GameTheme.blue)
            is EventView.RoundWon -> if (event.player == view.you) celebrate(big = false)
            is EventView.GameWon -> celebrate(big = true)
            is EventView.Dealt -> Unit
        }
    }

    private suspend fun deal(dealt: List<EventView.Dealt>, feedback: Feedback) = coroutineScope {
        // A few cards per player is enough to read as dealing without dragging on.
        val rounds = dealt.maxOf { it.count }.coerceAtMost(DEAL_FLIGHTS_PER_PLAYER)
        var i = 0
        for (round in 0 until rounds) {
            for (d in dealt) {
                val wait = i++ * 45L
                launch {
                    delay(wait)
                    feedback.sfx(Sfx.DEAL, 0.5f)
                    fly(null, Anchor.DrawPile, Anchor.Seat(d.player), 220, 0f, startScale = 0.7f)
                }
            }
        }
    }

    private suspend fun fly(card: Card?, from: Anchor, to: Anchor, millis: Int, endRotation: Float, startScale: Float) {
        val start = center(from) ?: center(Anchor.DrawPile) ?: return
        val end = center(to) ?: return
        val flight = Flight(card, start, end, endRotation, startScale)
        flights += flight
        try {
            flight.progress.animateTo(1f, tween(millis, easing = FastOutSlowInEasing))
        } finally {
            flights -= flight
        }
    }

    private suspend fun flash(at: Anchor, text: String, color: Color) {
        val center = center(at) ?: return
        val flash = Flash(center, text, color)
        flashes += flash
        try {
            flash.progress.animateTo(1f, tween(600, easing = LinearEasing))
        } finally {
            flashes -= flash
        }
    }

    private suspend fun burst(at: Anchor, color: Color) {
        val center = center(at) ?: return
        val burst = Burst(center, color)
        bursts += burst
        try {
            burst.progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        } finally {
            bursts -= burst
        }
    }

    private suspend fun shakeTable() {
        for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
    }

    private suspend fun glowRed() {
        penaltyGlow.animateTo(1f, tween(120))
        penaltyGlow.animateTo(0f, tween(500))
    }

    private fun celebrate(big: Boolean) {
        bigConfetti = big
        confettiBursts++
    }

    private fun center(anchor: Anchor): Offset? = anchors[anchor]?.center?.minus(layerOrigin)

    private fun preDeal(view: PlayerView) = view.copy(
        hand = emptyList(),
        playable = emptySet(),
        seats = view.seats.map { it.copy(cardCount = 0, calledLastCard = false) },
        phase = PhaseKind.TURN,
        winner = null,
        roundPoints = null,
        pendingDraw = 0,
        exposedPlayer = null,
        canDraw = false,
        canPass = false,
        canCallLastCard = false,
        canChallenge = false,
    )

    /** The table's anchors only exist once the game screen has laid out, which can lag the first update. */
    private suspend fun awaitTable() {
        withTimeoutOrNull(500) {
            while (Anchor.DrawPile !in anchors) delay(16)
        }
    }

    companion object {
        private const val MAX_BACKLOG = 3
        private const val MAX_DRAW_FLIGHTS = 5
        private const val DEAL_FLIGHTS_PER_PLAYER = 3

        /** A steady little tilt per card, so the discard pile looks tossed rather than stacked. */
        fun discardTilt(card: Card): Float = ((card.id * 7919) % 25 - 12).toFloat()
    }
}
