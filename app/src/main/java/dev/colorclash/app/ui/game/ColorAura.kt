package dev.colorclash.app.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.rules.CardColor

/**
 * The mat the discard pile is dealt onto, in whatever color is in play. It is the one thing on the
 * table that says what the color is when the top card can't — after a wild — so it stays put and
 * stays readable: stripes drift across it, and it swells when the color changes or a card lands.
 * The drift and the swell are read while drawing, so none of this recomposes.
 */
@Composable
fun ColorAura(
    color: CardColor?,
    /** Changes whenever a card lands, which is what makes the mat swell. Null while the pile is empty. */
    beat: Any?,
    cardWidth: Dp,
) {
    val tint by animateColorAsState(GameTheme.cardColor(color), tween(COLOR_FADE), label = "matTint")
    val strength by animateFloatAsState(if (color == null) 0f else 1f, tween(COLOR_FADE), label = "matStrength")
    val drift = rememberDrift()
    val land = remember { Animatable(0f) }
    val change = remember { Animatable(0f) }
    LaunchedEffect(beat) {
        if (beat == null) return@LaunchedEffect
        land.snapTo(1f)
        land.animateTo(0f, tween(LAND_MILLIS, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(color) {
        if (color == null) return@LaunchedEffect
        change.snapTo(1f)
        change.animateTo(0f, tween(CHANGE_MILLIS, easing = FastOutSlowInEasing))
    }
    val shapes = LocalColorShapes.current
    val edge = remember { Path() }
    val reach = MAT_PAD + SOFT_STEP * SOFT_RINGS
    Canvas(Modifier.freeSize(cardWidth + reach * 2, cardWidth * CARD_ASPECT + reach * 2)) {
        if (strength < 0.01f) return@Canvas
        val pad = MAT_PAD.toPx()
        val swell = 1f + 0.035f * land.value + 0.02f * change.value
        val mat = Size((cardWidth.toPx() + pad * 2) * swell, (cardWidth.toPx() * CARD_ASPECT + pad * 2) * swell)
        val at = Offset(center.x - mat.width / 2, center.y - mat.height / 2)
        val corner = cardWidth.toPx() * 0.2f
        softEdge(tint, at, mat, corner, strength)
        drawRoundRect(tint, at, mat, CornerRadius(corner), alpha = strength * (0.55f + 0.2f * change.value))
        edge.reset()
        edge.addRoundRect(RoundRect(Rect(at, mat), CornerRadius(corner)))
        clipPath(edge) { stripes(mat, at, drift.value, strength) }
        drawRoundRect(
            lerp(tint, GameTheme.text, 0.45f),
            at,
            mat,
            CornerRadius(corner),
            alpha = strength * 0.8f,
            style = Stroke(MAT_BORDER.toPx()),
        )
        // With colors off, the mat is all there is to say which color is in play.
        if (shapes && color != null) {
            val away = mat.height / 2 - pad * swell / 2
            val mark = GameTheme.contentOn(tint).copy(alpha = strength * 0.9f)
            colorShape(color, center - Offset(0f, away), pad * 0.4f, mark, Color.Transparent, 0f)
            colorShape(color, center + Offset(0f, away), pad * 0.4f, mark, Color.Transparent, 0f)
        }
    }
}

/** Rings of the mat's shape, each wider and no stronger, so its edge melts into the table. */
private fun DrawScope.softEdge(tint: Color, at: Offset, mat: Size, corner: Float, strength: Float) {
    val step = SOFT_STEP.toPx()
    for (ring in SOFT_RINGS downTo 1) {
        val out = step * ring
        drawRoundRect(
            tint,
            Offset(at.x - out, at.y - out),
            Size(mat.width + out * 2, mat.height + out * 2),
            CornerRadius(corner + out),
            alpha = strength * SOFT_ALPHA,
        )
    }
}

/** Light running across the mat on the diagonal. [drift] is one stripe's worth, so it never jumps. */
private fun DrawScope.stripes(mat: Size, at: Offset, drift: Float, strength: Float) {
    val gap = STRIPE_GAP.toPx()
    val shade = GameTheme.text.copy(alpha = strength * 0.07f)
    val width = STRIPE_WIDTH.toPx()
    var x = -mat.height
    while (x < mat.width + mat.height) {
        val edge = at.x + x + drift * gap
        drawLine(shade, Offset(edge - mat.height, at.y), Offset(edge, at.y + mat.height), strokeWidth = width)
        x += gap
    }
}

/** One stripe's worth of travel, over and over, which reads as an endless drift. */
@Composable
private fun rememberDrift(): State<Float> = rememberInfiniteTransition(label = "drift").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(DRIFT_MILLIS, easing = LinearEasing)),
    label = "driftPhase",
)

/** Measures at the given size but takes up nothing, so it sits centered behind the pile. */
private fun Modifier.freeSize(width: Dp, height: Dp) = layout { measurable, _ ->
    val w = width.roundToPx()
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(0, 0) { placeable.place(-w / 2, -h / 2) }
}

private const val COLOR_FADE = 420
private const val LAND_MILLIS = 600
private const val CHANGE_MILLIS = 900
private const val DRIFT_MILLIS = 2860
/** How far the mat reaches past the cards, before its soft edge. */
private val MAT_PAD = 15.dp
private val MAT_BORDER = 2.dp
private val SOFT_STEP = 5.dp
private const val SOFT_RINGS = 4
private const val SOFT_ALPHA = 0.1f
private val STRIPE_GAP = 26.dp
private val STRIPE_WIDTH = 7.dp
