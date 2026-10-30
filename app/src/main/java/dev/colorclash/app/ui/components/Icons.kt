package dev.colorclash.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import dev.colorclash.app.ui.theme.GameTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Drawn in code like the cards, so they look the same on every phone. Emoji and text arrows don't.
// All in a square, in fractions of its width, with one stroke weight.

private fun DrawScope.line() = Stroke(size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Lines through points given as x, y fractions of the width. */
private fun DrawScope.polyline(color: Color, vararg points: Float) {
    val w = size.width
    val path = Path().apply {
        moveTo(points[0] * w, points[1] * w)
        for (i in 2 until points.size step 2) lineTo(points[i] * w, points[i + 1] * w)
    }
    drawPath(path, color, style = line())
}

fun DrawScope.backIcon(color: Color) {
    polyline(color, 0.86f, 0.5f, 0.14f, 0.5f)
    polyline(color, 0.44f, 0.2f, 0.14f, 0.5f, 0.44f, 0.8f)
}

fun DrawScope.closeIcon(color: Color) {
    polyline(color, 0.2f, 0.2f, 0.8f, 0.8f)
    polyline(color, 0.8f, 0.2f, 0.2f, 0.8f)
}

fun DrawScope.checkIcon(color: Color) = polyline(color, 0.14f, 0.54f, 0.4f, 0.8f, 0.88f, 0.24f)

fun DrawScope.plusIcon(color: Color) {
    polyline(color, 0.5f, 0.14f, 0.5f, 0.86f)
    polyline(color, 0.14f, 0.5f, 0.86f, 0.5f)
}

fun DrawScope.chevronIcon(color: Color) = polyline(color, 0.34f, 0.14f, 0.7f, 0.5f, 0.34f, 0.86f)

fun DrawScope.exitIcon(color: Color) {
    polyline(color, 0.5f, 0.12f, 0.88f, 0.12f, 0.88f, 0.88f, 0.5f, 0.88f)
    polyline(color, 0.66f, 0.5f, 0.12f, 0.5f)
    polyline(color, 0.32f, 0.3f, 0.12f, 0.5f, 0.32f, 0.7f)
}

fun DrawScope.gearIcon(color: Color) {
    val w = size.width
    val tooth = Size(w * 0.2f, w * 0.22f)
    repeat(8) { i ->
        rotate(i * 45f) {
            drawRoundRect(
                color,
                topLeft = Offset(center.x - tooth.width / 2, w * 0.02f),
                size = tooth,
                cornerRadius = CornerRadius(w * 0.05f),
            )
        }
    }
    drawCircle(color, radius = w * 0.27f, style = Stroke(w * 0.15f))
}

fun DrawScope.cameraIcon(color: Color) {
    val w = size.width
    drawRoundRect(color, Offset(w * 0.08f, w * 0.26f), Size(w * 0.84f, w * 0.6f), CornerRadius(w * 0.12f), style = line())
    polyline(color, 0.34f, 0.26f, 0.42f, 0.14f, 0.58f, 0.14f, 0.66f, 0.26f)
    drawCircle(color, w * 0.14f, Offset(w * 0.5f, w * 0.56f), style = line())
}

fun DrawScope.smileIcon(color: Color) {
    val w = size.width
    drawCircle(color, w * 0.42f, style = line())
    drawCircle(color, w * 0.06f, Offset(w * 0.35f, w * 0.4f))
    drawCircle(color, w * 0.06f, Offset(w * 0.65f, w * 0.4f))
    drawArc(color, 25f, 130f, false, Offset(w * 0.28f, w * 0.3f), Size(w * 0.44f, w * 0.4f), style = line())
}

/** The four card colors, whatever color the button is. */
fun DrawScope.colorDotsIcon() {
    val w = size.width
    val r = w * 0.2f
    drawCircle(GameTheme.red, r, Offset(w * 0.27f, w * 0.27f))
    drawCircle(GameTheme.yellow, r, Offset(w * 0.73f, w * 0.27f))
    drawCircle(GameTheme.blue, r, Offset(w * 0.27f, w * 0.73f))
    drawCircle(GameTheme.green, r, Offset(w * 0.73f, w * 0.73f))
}

/** Which way play is going round the table. */
fun DrawScope.directionIcon(color: Color, clockwise: Boolean) {
    val w = size.width
    val radius = w * 0.36f
    val sweep = 290f
    val start = -50f
    scale(if (clockwise) 1f else -1f, 1f) {
        drawArc(
            color, start, sweep, false,
            Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2),
            style = line(),
        )
        val end = (start + sweep) * PI / 180
        val tip = center + Offset((radius * cos(end)).toFloat(), (radius * sin(end)).toFloat())
        val along = Offset(-sin(end).toFloat(), cos(end).toFloat())
        val across = Offset(cos(end).toFloat(), sin(end).toFloat())
        val head = Path().apply {
            moveTo(tip.x + along.x * w * 0.26f, tip.y + along.y * w * 0.26f)
            lineTo(tip.x + across.x * w * 0.17f, tip.y + across.y * w * 0.17f)
            lineTo(tip.x - across.x * w * 0.17f, tip.y - across.y * w * 0.17f)
            close()
        }
        drawPath(head, color)
    }
}

/** A bot, or a bot playing someone's seat. */
fun DrawScope.robotIcon(color: Color) {
    val w = size.width
    drawRoundRect(color, Offset(w * 0.12f, w * 0.32f), Size(w * 0.76f, w * 0.56f), CornerRadius(w * 0.16f))
    polyline(color, 0.5f, 0.32f, 0.5f, 0.12f)
    val eye = w * 0.08f
    val eyes = if (color == GameTheme.ink) GameTheme.text else GameTheme.ink
    drawCircle(eyes, eye, Offset(w * 0.35f, w * 0.56f))
    drawCircle(eyes, eye, Offset(w * 0.65f, w * 0.56f))
}

/** Someone's connection dropped. */
fun DrawScope.offlineIcon(color: Color) {
    val w = size.width
    val wave = Stroke(w * 0.1f, cap = StrokeCap.Round)
    drawArc(color, 215f, 110f, false, Offset(w * 0.08f, w * 0.22f), Size(w * 0.84f, w * 0.84f), style = wave)
    drawArc(color, 215f, 110f, false, Offset(w * 0.27f, w * 0.41f), Size(w * 0.46f, w * 0.46f), style = wave)
    drawCircle(color, w * 0.08f, Offset(w * 0.5f, w * 0.78f))
    polyline(color, 0.14f, 0.14f, 0.86f, 0.86f)
}
