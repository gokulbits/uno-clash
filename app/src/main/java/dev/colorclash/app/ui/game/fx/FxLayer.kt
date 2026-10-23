package dev.colorclash.app.ui.game.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.game.CARD_ASPECT
import dev.colorclash.app.ui.game.CardBack
import dev.colorclash.app.ui.game.CardFace
import dev.colorclash.app.ui.theme.GameTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val FLIGHT_CARD_WIDTH = 92.dp

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
        Confetti(fx.confettiBursts, fx.bigConfetti)
    }
}

@Composable
private fun FlightView(flight: Flight) {
    val density = LocalDensity.current
    val halfW = with(density) { FLIGHT_CARD_WIDTH.toPx() } / 2
    val halfH = halfW * CARD_ASPECT
    Box(
        Modifier
            .placeAt(Offset.Zero)
            .graphicsLayer {
                val p = flight.progress.value
                // Quadratic curve with the control point lifted above the midpoint: a nice toss arc.
                val mid = (flight.from + flight.to) / 2f
                val lift = (flight.to - flight.from).getDistance() * 0.35f
                val control = Offset(mid.x, mid.y - lift)
                val inv = 1 - p
                val pos = flight.from * (inv * inv) + control * (2 * inv * p) + flight.to * (p * p)
                translationX = pos.x - halfW
                translationY = pos.y - halfH
                rotationZ = flight.endRotation * p
                val s = flight.startScale + (1 - flight.startScale) * p
                scaleX = s
                scaleY = s
            },
    ) {
        val card = flight.card
        if (card != null) CardFace(card, FLIGHT_CARD_WIDTH) else CardBack(FLIGHT_CARD_WIDTH)
    }
}

@Composable
private fun FlashView(flash: Flash) {
    Text(
        flash.text,
        Modifier
            .placeAt(flash.at, centered = true)
            .graphicsLayer {
                val p = flash.progress.value
                val s = 0.6f + 0.9f * p
                scaleX = s
                scaleY = s
                alpha = if (p < 0.6f) 1f else (1 - p) / 0.4f
            },
        color = flash.color,
        fontSize = 34.sp,
        fontWeight = FontWeight.Black,
        fontFamily = GameTheme.displayFont,
    )
}

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
private const val MAX_CONFETTI = 120
private const val CONFETTI_NANOS = 3_200_000_000L
