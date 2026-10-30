package dev.colorclash.app.ui.game.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import dev.colorclash.app.feedback.Feedback
import dev.colorclash.app.feedback.Sfx
import dev.colorclash.app.feedback.YOUR_TURN_CUE
import dev.colorclash.app.feedback.cueFor
import dev.colorclash.app.ui.game.Announcement
import dev.colorclash.app.ui.game.SeatStamp
import dev.colorclash.app.ui.game.HAND_CARD_WIDTH
import dev.colorclash.app.ui.game.TABLE_CARD_WIDTH
import dev.colorclash.app.ui.game.announcementFor
import dev.colorclash.app.ui.game.seatStampsFor
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.rules.Card
import dev.colorclash.rules.Direction
import dev.colorclash.rules.Face
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sin

/** Places on screen that effects fly between. */
sealed interface Anchor {
    data class Seat(val seat: Int) : Anchor
    data class HandCard(val cardId: Int) : Anchor
    data object DrawPile : Anchor
    data object DiscardPile : Anchor
}

/** Hand cards lean around a point [pivotY] of their height down. */
class Spot(val bounds: Rect, val rotation: Float = 0f, val pivotY: Float = 0.5f) {
    val center: Offset
        get() {
            if (rotation == 0f) return bounds.center
            val pivot = Offset(bounds.center.x, bounds.top + bounds.height * pivotY)
            val d = bounds.center - pivot
            val r = Math.toRadians(rotation.toDouble())
            return pivot + Offset((d.x * cos(r) - d.y * sin(r)).toFloat(), (d.x * sin(r) + d.y * cos(r)).toFloat())
        }
}

/**
 * One card in the air. [to] is read every frame, so a card can land in a hand slot that's still
 * sliding into place. A card that [flips] starts face down and turns over on the way.
 */
class Flight(
    val card: Card?,
    val from: Offset,
    val to: () -> Offset,
    val fromScale: Float,
    val toScale: Float,
    val fromRotation: Float,
    val toRotation: Float,
    val flips: Boolean,
    val fadesOut: Boolean,
) {
    val progress = Animatable(0f)
}

class StampFx(val at: Offset, val forYou: Boolean, val stamp: SeatStamp) {
    val progress = Animatable(0f)
}

class Flash(val at: Offset, val text: String, val color: Color) {
    val progress = Animatable(0f)
}

class Burst(val at: Offset, val color: Color) {
    val progress = Animatable(0f)
}

class Banner(val announcement: Announcement) {
    val progress = Animatable(0f)
}

class ReactionFx(val id: Int, val at: Offset, val reaction: Int)

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
    /** Cards already in your hand's layout but still flying in; their slot stays empty until they land. */
    val incomingCards = mutableStateListOf<Int>()

    /** The pile as it looks now: a thrown card joins it as it lands, not when the update is done. */
    var pileTop by mutableStateOf<Card?>(null)
        private set
    val pileUnder = mutableStateListOf<Card>()
    /** Between dealing and the starter being turned over, the pile is empty. */
    var pileCleared by mutableStateOf(false)
        private set
    val pileBump = Animatable(0f)

    var banner by mutableStateOf<Banner?>(null)
        private set
    val reactions = mutableStateListOf<ReactionFx>()

    val arrowSpin = Animatable(0f)
    val shake = Animatable(0f)
    val stamps = mutableStateListOf<StampFx>()
    /** The seat a bad stamp is knocking sideways (yours means your hand). */
    var joltedSeat by mutableStateOf<Int?>(null)
        private set
    val jolt = Animatable(0f)
    /** The seat a good stamp is bouncing. */
    var cheeredSeat by mutableStateOf<Int?>(null)
        private set
    val cheer = Animatable(0f)
    val penaltyGlow = Animatable(0f)
    var confettiBursts by mutableIntStateOf(0)
        private set
    var bigConfetti by mutableStateOf(false)
        private set

    // Written during layout, read by the effects coroutine and flights, all on the main thread, so no snapshot state needed.
    private val anchors = HashMap<Anchor, Spot>()
    private var layerOrigin = Offset.Zero
    private val pending = AtomicInteger(0)
    /**
     * Frames stop while the app is out of sight but updates don't, and a host with its screen off can
     * play for hours. Every view is complete, so when the queue is full the oldest can simply go.
     */
    private val queue = Channel<ServerMessage.Game>(MAX_QUEUED, BufferOverflow.DROP_OLDEST) { pending.decrementAndGet() }
    /** For effects that shouldn't hold up the next event, like the pile settling. */
    private var sideEffects: CoroutineScope? = null
    private var bannerJob: Job? = null
    private var nextReactionId = 0

    fun submit(update: ServerMessage.Game) {
        pending.incrementAndGet()
        queue.trySend(update)
    }

    fun anchor(anchor: Anchor, rotation: Float = 0f, pivotY: Float = 0.5f): Modifier = Modifier.onGloballyPositioned {
        // Not boundsInWindow: that clips to what's visible, and a hand card half off screen would land in the wrong place.
        anchors[anchor] = Spot(Rect(it.positionInWindow(), it.size.toSize()), rotation, pivotY)
    }

    fun forget(anchor: Anchor) {
        anchors.remove(anchor)
    }

    fun layer(): Modifier = Modifier.onGloballyPositioned { layerOrigin = it.positionInWindow() }

    /** Shown straight away, not queued behind the game's effects. */
    fun react(seat: Int, reaction: Int) {
        val at = center(Anchor.Seat(seat)) ?: return
        reactions += ReactionFx(nextReactionId++, at, reaction)
        while (reactions.size > MAX_REACTIONS) reactions.removeAt(0)
    }

    fun reactionDone(reaction: ReactionFx) {
        reactions -= reaction
    }

    /** Launch from composition so animations have a frame clock. */
    suspend fun run(feedback: Feedback) = coroutineScope {
        sideEffects = this
        for (update in queue) {
            val backlog = pending.decrementAndGet()
            try {
                if (backlog < MAX_BACKLOG && update.events.isNotEmpty()) {
                    play(update, feedback)
                }
            } finally {
                // Even when cancelled partway (the activity was recreated), or the table stays on
                // whatever the animation had got to, like the empty one from before a deal.
                commit(update.view, feedback)
            }
        }
    }

    /** Back in the lobby: forget the last table so the next game doesn't flash the old one. */
    fun reset() {
        while (queue.tryReceive().isSuccess) pending.decrementAndGet()
        displayed = null
        hiddenCards.clear()
        incomingCards.clear()
        clearPile(cleared = false)
        reactions.clear()
        banner = null
    }

    private suspend fun play(update: ServerMessage.Game, feedback: Feedback) {
        val view = update.view
        if (update.events.any { it is EventView.RoundStarted }) {
            // Empty hands and table while the cards fly out, instead of last round's.
            displayed = preDeal(view)
            clearPile(cleared = true)
            anchors.keys.removeAll { it is Anchor.HandCard }
            awaitAnchor(Anchor.DrawPile)
        }
        var dealt = false
        for (event in update.events) {
            if (event is EventView.Dealt) {
                if (!dealt) deal(update.events.filterIsInstance<EventView.Dealt>(), view, feedback)
                dealt = true
                continue
            }
            feedback.cue(cueFor(event, view.you))
            val callout = announcementFor(event, view)
            val seatStamps = seatStampsFor(event, view.you)
            // A played card is called out as it lands; a picked color or an UNO call straight away.
            if (event !is EventView.CardPlayed) callout?.let { announce(it, feedback) }
            // Stamps land first; the event's own animation (cards flying to the victim) starts as they hit.
            coroutineScope {
                seatStamps.forEach { launch { stamp(it, view.you) } }
                if (seatStamps.isNotEmpty()) delay(STAMP_IMPACT_MILLIS)
                animate(event, view, feedback)
            }
            if (event is EventView.CardPlayed) callout?.let { announce(it, feedback) }
        }
    }

    private fun commit(view: PlayerView, feedback: Feedback) {
        val before = displayed
        displayed = view
        hiddenCards.clear()
        incomingCards.clear()
        // A round whose effects were skipped still shouldn't leave last round's cards on the pile.
        if (before != null && before.round != view.round) clearPile(cleared = false)
        placeOnPile(view.topCard)
        val turnStarts = view.isYourTurn && view.phase == PhaseKind.TURN &&
            (before == null || !before.isYourTurn || before.round != view.round || before.phase != PhaseKind.TURN)
        if (turnStarts) feedback.cue(YOUR_TURN_CUE)
    }

    private suspend fun animate(event: EventView, view: PlayerView, feedback: Feedback) {
        when (event) {
            is EventView.RoundStarted -> delay(350)
            is EventView.StarterFlipped -> {
                fly(event.card, Anchor.DrawPile, Anchor.DiscardPile, 420, toRotation = discardTilt(event.card), flips = true)
                land(event.card, feedback)
            }
            is EventView.CardPlayed -> {
                if (event.player == view.you) {
                    // A card scrolled out of sight in a big hand has no slot on screen; throw it from the hand instead.
                    val from = Anchor.HandCard(event.card.id).takeIf { it in anchors } ?: Anchor.Seat(view.you)
                    hiddenCards += event.card.id
                    fly(event.card, from, Anchor.DiscardPile, 380, fromScale = HAND_SCALE, toRotation = discardTilt(event.card))
                } else {
                    fly(event.card, Anchor.Seat(event.player), Anchor.DiscardPile, 440, fromScale = SEAT_SCALE, toRotation = discardTilt(event.card), flips = true)
                }
                land(event.card, feedback)
                if (event.card.face == Face.DRAW_TWO || event.card.face == Face.WILD_DRAW_FOUR) shakeTable()
            }
            is EventView.Drew -> {
                val cards = event.cards
                if (event.player == view.you && cards != null) {
                    drawIntoHand(cards)
                } else {
                    coroutineScope {
                        repeat(event.count.coerceAtMost(MAX_DRAW_FLIGHTS)) { i ->
                            launch {
                                delay(i * 80L)
                                fly(null, Anchor.DrawPile, Anchor.Seat(event.player), 360, toScale = SEAT_SCALE, fadesOut = true)
                            }
                        }
                    }
                }
            }
            is EventView.Skipped -> Unit // its stamp is the whole effect
            is EventView.Reversed -> {
                val turn = if (event.direction == Direction.CLOCKWISE) 360f else -360f
                arrowSpin.animateTo(arrowSpin.value + turn, tween(550, easing = FastOutSlowInEasing))
            }
            is EventView.WildColorChosen -> burst(Anchor.DiscardPile, GameTheme.cardColor(event.color))
            is EventView.LastCardCalled -> flash(Anchor.Seat(event.player), "UNO!", GameTheme.yellow)
            is EventView.PenaltyApplied -> coroutineScope {
                launch { flash(Anchor.Seat(event.player), "+${event.cards}", GameTheme.red) }
                if (event.player == view.you) launch { glowRed() }
            }
            is EventView.Challenged -> Unit // both players' stamps say it
            is EventView.HandsSwapped -> coroutineScope {
                launch { fly(null, Anchor.Seat(event.player), Anchor.Seat(event.target), 460, SEAT_SCALE, SEAT_SCALE) }
                launch { fly(null, Anchor.Seat(event.target), Anchor.Seat(event.player), 460, SEAT_SCALE, SEAT_SCALE) }
            }
            is EventView.HandsRotated -> flash(Anchor.DiscardPile, "Pass hands!", GameTheme.blue)
            is EventView.DeckReshuffled -> {
                pileUnder.clear()
                flash(Anchor.DrawPile, "Shuffled!", GameTheme.blue)
            }
            is EventView.RoundWon -> if (event.player == view.you) celebrate(big = false)
            is EventView.GameWon -> celebrate(big = true)
            is EventView.Dealt -> Unit
        }
    }

    /** A card to each player in turn; yours fly into their slots and turn over as they land. */
    private suspend fun deal(dealt: List<EventView.Dealt>, view: PlayerView, feedback: Feedback) = coroutineScope {
        val mine = dealt.firstOrNull { it.player == view.you }?.cards.orEmpty()
        val rounds = dealt.maxOf { it.count }
        var i = 0
        for (round in 0 until rounds) {
            for (d in dealt) {
                val card = if (d.player == view.you) mine.getOrNull(round) else null
                if (card == null && round >= DEAL_FLIGHTS_PER_PLAYER) continue
                val wait = i++ * DEAL_GAP_MILLIS
                launch {
                    delay(wait)
                    feedback.sfx(Sfx.DEAL, 0.5f)
                    if (card != null) {
                        displayed?.let { if (it.hand.none { c -> c.id == card.id }) displayed = it.copy(hand = it.hand + card) }
                        incomingCards += card.id
                        flyIntoHand(card, 340)
                        incomingCards -= card.id
                    } else {
                        fly(null, Anchor.DrawPile, Anchor.Seat(d.player), 320, toScale = SEAT_SCALE, fadesOut = true)
                    }
                }
            }
        }
    }

    /** Makes room in the hand first, then flies each drawn card into its slot. */
    private suspend fun drawIntoHand(cards: List<Card>) = coroutineScope {
        val current = displayed ?: return@coroutineScope
        val fresh = cards.filter { card -> current.hand.none { it.id == card.id } }
        incomingCards += fresh.map { it.id }
        displayed = current.copy(hand = current.hand + fresh)
        fresh.forEachIndexed { i, card ->
            launch {
                if (i < MAX_DRAW_FLIGHTS) {
                    delay(i * 90L)
                    flyIntoHand(card, 400)
                }
                incomingCards -= card.id
            }
        }
    }

    private suspend fun flyIntoHand(card: Card, millis: Int) {
        val slot = Anchor.HandCard(card.id)
        awaitAnchor(slot)
        fly(card, Anchor.DrawPile, slot, millis, toScale = HAND_SCALE, toRotation = anchors[slot]?.rotation ?: 0f, flips = true)
    }

    private suspend fun fly(
        card: Card?,
        from: Anchor,
        to: Anchor,
        millis: Int,
        fromScale: Float = 1f,
        toScale: Float = 1f,
        toRotation: Float = 0f,
        flips: Boolean = false,
        fadesOut: Boolean = false,
    ) {
        val start = anchors[from] ?: anchors[Anchor.DrawPile] ?: return
        val fallback = center(to) ?: return
        val flight = Flight(
            card = card,
            from = start.center - layerOrigin,
            to = { center(to) ?: fallback },
            fromScale = fromScale,
            toScale = toScale,
            fromRotation = start.rotation,
            toRotation = toRotation,
            flips = flips,
            fadesOut = fadesOut,
        )
        flights += flight
        try {
            flight.progress.animateTo(1f, tween(millis, easing = TOSS))
        } finally {
            flights -= flight
        }
    }

    /** Doesn't hold up the next event. */
    private fun announce(announcement: Announcement, feedback: Feedback) {
        feedback.say(announcement.spoken)
        bannerJob?.cancel()
        val shown = Banner(announcement)
        banner = shown
        bannerJob = sideEffects?.launch {
            try {
                shown.progress.animateTo(1f, tween(BANNER_MILLIS, easing = LinearEasing))
            } finally {
                if (banner === shown) banner = null
            }
        }
    }

    private fun land(card: Card, feedback: Feedback) {
        placeOnPile(card)
        feedback.sfx(Sfx.LAND)
        sideEffects?.launch {
            pileBump.snapTo(1f)
            pileBump.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    private fun placeOnPile(card: Card) {
        pileCleared = false
        val previous = pileTop
        if (previous?.id == card.id) return
        if (previous != null) {
            pileUnder += previous
            while (pileUnder.size > PILE_DEPTH) pileUnder.removeAt(0)
        }
        pileTop = card
    }

    private fun clearPile(cleared: Boolean) {
        pileTop = null
        pileUnder.clear()
        pileCleared = cleared
    }

    private suspend fun stamp(stamp: SeatStamp, you: Int) = coroutineScope {
        val at = center(Anchor.Seat(stamp.seat)) ?: return@coroutineScope
        val shown = StampFx(at, forYou = stamp.seat == you, stamp)
        stamps += shown
        launch { if (stamp.good) cheerSeat(stamp.seat) else joltSeat(stamp.seat) }
        try {
            shown.progress.animateTo(1f, tween(STAMP_MILLIS, easing = LinearEasing))
        } finally {
            stamps -= shown
        }
    }

    private suspend fun joltSeat(seat: Int) {
        joltedSeat = seat
        try {
            delay(STAMP_IMPACT_MILLIS) // knocked as the stamp hits, not before
            for (x in listOf(16f, -13f, 10f, -7f, 4f, 0f)) jolt.animateTo(x, tween(55))
        } finally {
            joltedSeat = null
        }
    }

    private suspend fun cheerSeat(seat: Int) {
        cheeredSeat = seat
        try {
            delay(STAMP_IMPACT_MILLIS)
            cheer.snapTo(0f)
            cheer.animateTo(1f, tween(140))
            cheer.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
        } finally {
            cheeredSeat = null
        }
    }

    private suspend fun flash(at: Anchor, text: String, color: Color) {
        val center = center(at) ?: return
        val flash = Flash(center, text, color)
        flashes += flash
        try {
            flash.progress.animateTo(1f, tween(700, easing = LinearEasing))
        } finally {
            flashes -= flash
        }
    }

    private suspend fun burst(at: Anchor, color: Color) {
        val center = center(at) ?: return
        val burst = Burst(center, color)
        bursts += burst
        try {
            burst.progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
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

    /** Anchors only exist once their composable has laid out, which can lag a frame or two behind the state. */
    private suspend fun awaitAnchor(anchor: Anchor) {
        withTimeoutOrNull(500) {
            while (anchor !in anchors) delay(16)
        }
    }

    companion object {
        private const val MAX_BACKLOG = 3
        private const val MAX_QUEUED = 32
        private const val MAX_DRAW_FLIGHTS = 5
        private const val DEAL_FLIGHTS_PER_PLAYER = 3
        private const val DEAL_GAP_MILLIS = 55L
        private const val PILE_DEPTH = 3
        private const val BANNER_MILLIS = 1400
        private const val STAMP_MILLIS = 1150
        private const val STAMP_IMPACT_MILLIS = 170L
        private const val MAX_REACTIONS = 12
        private val HAND_SCALE = HAND_CARD_WIDTH / TABLE_CARD_WIDTH
        private const val SEAT_SCALE = 0.42f
        /** Quick off the hand, gentle into place. */
        private val TOSS = CubicBezierEasing(0.2f, 0.7f, 0.25f, 1f)

        /** A steady little tilt per card, so the discard pile looks tossed rather than stacked. */
        fun discardTilt(card: Card): Float = ((card.id * 7919) % 25 - 12).toFloat()
    }
}
