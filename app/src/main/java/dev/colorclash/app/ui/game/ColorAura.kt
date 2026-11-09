package dev.colorclash.app.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.rules.CardColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * The backdrop behind the discard pile: a blob of whatever color is in play, wobbling the way a drop
 * of liquid would and turning with the play, so a reverse is something you feel at the pile and not
 * only in the arrow. Every card that lands gives it a shove. Nothing here recomposes — the shape is
 * worked out while drawing, off a clock of its own.
 */
@Composable
fun ColorAura(
    color: CardColor?,
    clockwise: Boolean,
    /** Changes whenever a card lands, which is what shoves the blob. Null while the pile is empty. */
    beat: Any?,
    cardWidth: Dp,
) {
    val tint by animateColorAsState(GameTheme.cardColor(color), tween(COLOR_FADE), label = "auraTint")
    val strength by animateFloatAsState(if (color == null) 0f else 1f, tween(COLOR_FADE), label = "auraStrength")
    val clock = rememberBlobClock(clockwise, color != null)
    val splash = remember { Animatable(0f) }
    LaunchedEffect(beat) {
        if (beat == null) return@LaunchedEffect
        splash.snapTo(1f)
        splash.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    val fill = remember(tint) {
        Brush.radialGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.5f), tint.copy(alpha = 0.18f)))
    }
    val shapes = LocalColorShapes.current
    val path = remember { Path() }
    Canvas(Modifier.freeSize(cardWidth * AURA_CARDS)) {
        if (strength < 0.01f) return@Canvas
        val radius = cardWidth.toPx() * BLOB_RADIUS * (1f + 0.1f * splash.value)
        blob(path, radius, clock.turn.value, clock.time.value)
        clipPath(path) { drawCircle(fill, radius * 1.45f, alpha = strength) }
        drawPath(path, tint, alpha = strength * 0.9f, style = Stroke(BLOB_EDGE.toPx()))
        drawPath(path, GameTheme.text, alpha = strength * 0.22f, style = Stroke(BLOB_SHINE.toPx()))
        // With colors off, the blob is all there is to say which color is in play.
        if (shapes && color != null) {
            val up = radius * BLOB_TALL * 0.8f
            val r = cardWidth.toPx() * 0.07f
            val mark = GameTheme.contentOn(tint).copy(alpha = strength * 0.9f)
            colorShape(color, center - Offset(0f, up), r, mark, Color.Transparent, 0f)
            colorShape(color, center + Offset(0f, up), r, mark, Color.Transparent, 0f)
        }
    }
}

/** Rebuilds [path] as the blob's outline: a circle pushed around by three waves running over it. */
private fun DrawScope.blob(path: Path, radius: Float, turn: Float, time: Float) {
    path.reset()
    val breath = 1f + 0.03f * sin(time * BREATH_RATE)
    for (i in 0..BLOB_POINTS) {
        val a = i.toFloat() / BLOB_POINTS * TAU
        val wobble = 1f + 0.085f * sin(3f * a + turn * 2f) + 0.055f * sin(5f * a - turn * 1.4f) + 0.04f * sin(2f * a + turn * 3f)
        val r = radius * wobble * breath
        val x = center.x + cos(a) * r
        val y = center.y + sin(a) * r * BLOB_TALL
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** The whole table lit by the color in play, which is how you spot it from across the room. */
@Composable
fun ColorWash(color: CardColor?) {
    val tint by animateColorAsState(GameTheme.cardColor(color), tween(COLOR_FADE), label = "washTint")
    val strength by animateFloatAsState(if (color == null) 0f else 1f, tween(COLOR_FADE), label = "washStrength")
    val breathe = rememberBreathe()
    val glow = LocalTableStyle.current.glow
    Box(
        Modifier.fillMaxSize().drawBehind {
            if (strength < 0.01f) return@drawBehind
            drawRect(
                Brush.radialGradient(
                    listOf(tint, tint.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * WASH_CENTER),
                    radius = size.minDimension * 1.15f,
                ),
                alpha = strength * glow * (0.13f + 0.05f * breathe.value),
            )
        },
    )
}

/** A slow breath, 0 to 1 and back. The State is what's passed on, so reading it doesn't recompose. */
@Composable
private fun rememberBreathe(): State<Float> = rememberInfiniteTransition(label = "breathe").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "breathePhase",
)

/** [turn] runs the way the play does, [time] always forward. Both in seconds-worth of radians. */
private class BlobClock(val turn: State<Float>, val time: State<Float>)

/**
 * Counted up by hand off the frame clock rather than animated to a target: a reverse only changes
 * which way [BlobClock.turn] is counting, so the blob carries on from the shape it's in.
 */
@Composable
private fun rememberBlobClock(clockwise: Boolean, live: Boolean): BlobClock {
    val turn = remember { mutableFloatStateOf(0f) }
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { BlobClock(turn, time) }
    val way = rememberUpdatedState(if (clockwise) 1f else -1f)
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val seconds = (now - last) / 1_000_000_000f
                    turn.value = (turn.value + TURN_RATE * way.value * seconds).mod(TAU)
                    time.value = (time.value + seconds).mod(TIME_WRAP)
                }
                last = now
            }
        }
    }
    return clock
}

/** Measures at [side] but takes up nothing, so it sits centered behind the pile without moving it. */
private fun Modifier.freeSize(side: Dp) = layout { measurable, _ ->
    val px = side.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(px, px))
    layout(0, 0) { placeable.place(-px / 2, -px / 2) }
}

private const val TAU = (Math.PI * 2).toFloat()
private const val COLOR_FADE = 420
private const val BREATH_MILLIS = 2400
private const val BREATH_RATE = 1.1f
/** Radians a second the waves travel round the blob. */
private const val TURN_RATE = 0.5f
/** Seconds before the clock wraps, long enough that nobody sees it and short enough to stay precise. */
private const val TIME_WRAP = 3600f
private const val BLOB_POINTS = 96
/** The blob's radius and how much taller than wide it sits, both in cards. */
private const val BLOB_RADIUS = 1.02f
private const val BLOB_TALL = 1.12f
private const val AURA_CARDS = 2.9f
private const val WASH_CENTER = 0.44f
private val BLOB_EDGE = 2.5.dp
private val BLOB_SHINE = 1.dp
