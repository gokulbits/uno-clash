package dev.colorclash.app.ui.game.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.game.CARD_ASPECT
import dev.colorclash.app.ui.game.CardBack
import dev.colorclash.app.ui.game.CardFace
import dev.colorclash.app.ui.game.TABLE_CARD_WIDTH
import dev.colorclash.app.ui.theme.GameTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val FLIGHT_CARD_WIDTH = TABLE_CARD_WIDTH
private val CARD_SHAPE = RoundedCornerShape(FLIGHT_CARD_WIDTH * 0.11f)
private val POP = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Everything that moves over the table. Sits on top and never takes touches. */
@Composable
fun FxLayer(fx: TableFx, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().then(fx.layer())) {
        // Red edge glow when you get penalised.
        if (fx.penaltyGlow.value > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val a = fx.penaltyGlow.value * 0.7f
                drawRect(Brush.radialGradient(listOf(Color.Transparent, GameTheme.red.copy(alpha = a)), radius = size.maxDimension * 0.75f))
            }
        }
        fx.bursts.forEach { BurstView(it) }
        fx.flights.forEach { FlightView(it) }
        fx.flashes.forEach { FlashView(it) }
        fx.banner?.let { key(it) { BannerView(it) } }
        fx.reactions.forEach { r -> key(r.id) { ReactionView(r) { fx.reactionDone(r) } } }
        Confetti(fx.confettiBursts, fx.bigConfetti)
    }
}

@Composable
private fun FlightView(flight: Flight) {
    val density = LocalDensity.current
    val halfW = with(density) { FLIGHT_CARD_WIDTH.toPx() } / 2
    val halfH = halfW * CARD_ASPECT
    val rest = with(density) { 2.dp.toPx() }
    val lift = with(density) { 18.dp.toPx() }
    // Only recomposes once, when the card turns over; everything else moves in the layer.
    val faceUp by remember(flight) {
        derivedStateOf { flight.card != null && (!flight.flips || turn(flight.progress.value) >= 0.5f) }
    }
    Box(
        Modifier
            .placeAt(Offset.Zero)
            .graphicsLayer {
                val p = flight.progress.value
                val to = flight.to()
                // Quadratic curve with the control point lifted above the midpoint: a nice toss arc.
                val mid = (flight.from + to) / 2f
                val arc = (to - flight.from).getDistance() * 0.28f
                val control = Offset(mid.x, mid.y - arc)
                val inv = 1 - p
                val pos = flight.from * (inv * inv) + control * (2 * inv * p) + to * (p * p)
                translationX = pos.x - halfW
                translationY = pos.y - halfH
                rotationZ = mix(flight.fromRotation, flight.toRotation, p)
                val height = sin(p * PI).toFloat()
                // Swells a touch at the top of the arc, as if it came off the table towards you.
                val s = mix(flight.fromScale, flight.toScale, p) * (1 + 0.1f * height)
                scaleX = if (flight.flips) s * abs(cos(turn(p) * PI)).toFloat() else s
                scaleY = s
                shadowElevation = rest + lift * height
                shape = CARD_SHAPE
                clip = false
                alpha = if (flight.fadesOut) ((1 - p) / 0.3f).coerceIn(0f, 1f) else 1f
            },
    ) {
        val card = flight.card
        if (faceUp && card != null) {
            CardFace(card, FLIGHT_CARD_WIDTH, elevation = 0.dp)
        } else {
            CardBack(FLIGHT_CARD_WIDTH, elevation = 0.dp)
        }
    }
}

/** How far through turning over a flipping card is, 0 to 1. It turns in the middle stretch of the flight. */
private fun turn(p: Float) = ((p - 0.1f) / 0.55f).coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
private fun FlashView(flash: Flash) {
    val density = LocalDensity.current
    val rise = with(density) { 28.dp.toPx() }
    Text(
        flash.text,
        Modifier
            .placeAt(flash.at, centered = true)
            .graphicsLayer {
                val p = flash.progress.value
                // Pops in past full size, settles, then drifts up and fades.
                val pop = if (p < 0.25f) POP.transform(p / 0.25f) else 1f
                val s = 0.5f + 0.6f * pop
                scaleX = s
                scaleY = s
                translationY = -rise * p
                alpha = if (p < 0.65f) 1f else (1 - p) / 0.35f
            },
        color = flash.color,
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        fontFamily = GameTheme.displayFont,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), blurRadius = 10f)),
    )
}

/**
 * The callout for an action card or a picked color: slams in a little past full size with a
 * tilt, holds, then swells and fades. Painted in the card's color, or all four for a wild.
 */
@Composable
private fun BannerView(banner: Banner) {
    val a = banner.announcement
    val fill = a.color?.let { SolidColor(GameTheme.cardColor(it)) } ?: Brush.linearGradient(GameTheme.cardColors)
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .offset(y = (-36).dp)
                .graphicsLayer {
                    val p = banner.progress.value
                    val enter = if (p < 0.16f) POP.transform(p / 0.16f) else 1f
                    val exit = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
                    val s = 0.35f + 0.65f * enter + 0.15f * exit
                    scaleX = s
                    scaleY = s
                    rotationZ = -10f + 6f * enter
                    alpha = 1f - exit
                    shadowElevation = 16.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .background(fill)
                .border(3.dp, Color.White.copy(alpha = 0.8f), shape)
                .padding(horizontal = 30.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                a.title,
                color = Color.White,
                fontSize = 50.sp,
                fontWeight = FontWeight.Black,
                fontFamily = GameTheme.displayFont,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 5f), blurRadius = 8f)),
            )
            Text(
                a.detail,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), blurRadius = 4f)),
            )
        }
    }
}

/**
 * An emoji reaction: the big one pops out of the seat with a wobble and floats up a little,
 * while a stream of small copies drifts away and fades, like hearts on a live stream.
 */
@Composable
private fun ReactionView(reaction: ReactionFx, onDone: () -> Unit) {
    val progress = remember(reaction) { Animatable(0f) }
    LaunchedEffect(reaction) {
        progress.animateTo(1f, tween(REACTION_MILLIS, easing = LinearEasing))
        onDone()
    }
    val emoji = GameTheme.reaction(reaction.reaction)
    val density = LocalDensity.current
    val rise = with(density) { 96.dp.toPx() }
    val sway = with(density) { 28.dp.toPx() }
    val sparks = remember(reaction) {
        val r = Random(reaction.id)
        List(SPARKS) { i -> Spark(start = i * 0.06f + r.nextFloat() * 0.05f, drift = r.nextFloat() * 2 - 1, size = 16f + r.nextFloat() * 10f, phase = r.nextFloat() * 6.28f) }
    }
    sparks.forEach { s ->
        Text(
            emoji,
            Modifier
                .placeAt(reaction.at, centered = true)
                .graphicsLayer {
                    val p = ((progress.value - s.start) / (1 - s.start)).coerceIn(0f, 1f)
                    translationY = -rise * 1.4f * p
                    translationX = sway * (s.drift * p + 0.35f * sin(p * 9f + s.phase))
                    val grow = (p / 0.12f).coerceAtMost(1f)
                    scaleX = grow
                    scaleY = grow
                    alpha = if (p <= 0f) 0f else 1f - p
                },
            fontSize = s.size.sp,
        )
    }
    Text(
        emoji,
        Modifier
            .placeAt(reaction.at, centered = true)
            .graphicsLayer {
                val p = progress.value
                val pop = if (p < 0.18f) POP.transform(p / 0.18f) else 1f
                scaleX = pop
                scaleY = pop
                rotationZ = sin(p * 6 * PI).toFloat() * 16f * (1 - p)
                translationY = -rise * 0.45f * p
                alpha = if (p < 0.75f) 1f else (1 - p) / 0.25f
            },
        fontSize = 48.sp,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 4f), blurRadius = 8f)),
    )
}

private class Spark(val start: Float, val drift: Float, val size: Float, val phase: Float)

@Composable
private fun BurstView(burst: Burst) {
    Canvas(Modifier.fillMaxSize()) {
        val p = burst.progress.value
        val maxRadius = size.minDimension * 0.45f
        val alpha = 1 - p
        drawCircle(burst.color.copy(alpha = alpha * 0.45f), radius = maxRadius * p, center = burst.at)
        // Rays of dots shooting outward.
        for (i in 0 until BURST_RAYS) {
            val angle = i * (2 * Math.PI / BURST_RAYS)
            val r = maxRadius * (0.3f + 0.9f * p)
            val dot = Offset((burst.at.x + cos(angle) * r).toFloat(), (burst.at.y + sin(angle) * r).toFloat())
            drawCircle(burst.color.copy(alpha = alpha), radius = 10.dp.toPx() * (1 - p * 0.6f), center = dot)
        }
    }
}

/**
 * Confetti from the top of the screen. Particle positions are worked out from elapsed time
 * alone, so a frame only redraws the canvas and never recomposes.
 */
@Composable
private fun Confetti(trigger: Int, big: Boolean) {
    if (trigger == 0) return
    val density = LocalDensity.current
    val particles = remember(trigger) { List(if (big) MAX_CONFETTI else MAX_CONFETTI / 2) { Particle.random(Random(trigger * 1000 + it)) } }
    var elapsed by remember(trigger) { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (elapsed < CONFETTI_NANOS) {
            elapsed = withFrameNanos { it } - start
        }
    }
    if (elapsed >= CONFETTI_NANOS) return
    val gravity = with(density) { 900.dp.toPx() }
    val speed = with(density) { 1.dp.toPx() }
    val piece = with(density) { 9.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsed / 1_000_000_000f
        particles.forEach { p ->
            val x = p.x0 * size.width + p.vx * speed * t
            val y = -piece + p.vy * speed * t + 0.5f * gravity * p.weight * t * t
            if (y > size.height) return@forEach
            rotate(p.spin * t * 360f, pivot = Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x, y), size = Size(piece, piece * 0.55f))
            }
        }
    }
}

private class Particle(val x0: Float, val vx: Float, val vy: Float, val weight: Float, val spin: Float, val color: Color) {
    companion object {
        fun random(r: Random) = Particle(
            x0 = r.nextFloat(),
            vx = r.nextFloat() * 240f - 120f,
            vy = r.nextFloat() * 160f,
            weight = 0.35f + r.nextFloat() * 0.4f,
            spin = r.nextFloat() * 2f - 1f,
            color = GameTheme.cardColors[r.nextInt(GameTheme.cardColors.size)],
        )
    }
}

/** Places a child with its top-left (or centre) at a window-relative point inside the layer. */
private fun Modifier.placeAt(at: Offset, centered: Boolean = false): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val x = if (centered) at.x - placeable.width / 2f else at.x
        val y = if (centered) at.y - placeable.height / 2f else at.y
        placeable.place(x.toInt(), y.toInt())
    }
}

private const val BURST_RAYS = 12
private const val REACTION_MILLIS = 2400
private const val SPARKS = 6
private const val MAX_CONFETTI = 120
private const val CONFETTI_NANOS = 3_200_000_000L
