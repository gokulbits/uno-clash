package dev.colorclash.app.ui.game

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.rules.Card
import dev.colorclash.rules.Face
import kotlin.math.abs
import kotlin.math.max

const val CARD_ASPECT = 1.5f
val HAND_CARD_WIDTH = 76.dp
val TABLE_CARD_WIDTH = 92.dp

/**
 * A card face drawn from scratch at whatever size it's shown: paper frame, shaded body, tilted
 * oval, the symbol, and corner marks. Every measurement is a fraction of the card's width.
 */
@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false, elevation: Dp = 2.dp) {
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier
            .size(width, width * CARD_ASPECT)
            .shadow(elevation, RoundedCornerShape(width * CORNER))
            .drawWithCache {
                val w = size.width
                val number = card.face.number?.toString()
                val big = number?.let { measurer.label(it, 0.66f * w, this) }
                val cornerText = number ?: card.face.cornerText()
                val corner = cornerText?.let { measurer.label(it, if (it.length > 1) 0.18f * w else 0.24f * w, this) }
                onDrawBehind { drawFace(card, Tint(dimmed), big, corner) }
            },
    )
}

/** The back every face-down card shares. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier, elevation: Dp = 2.dp) {
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier
            .size(width, width * CARD_ASPECT)
            .shadow(elevation, RoundedCornerShape(width * CORNER))
            .drawWithCache {
                val words = BACK_WORDS.map { measurer.label(it, 0.17f * size.width, this) }
                onDrawBehind { drawBack(words) }
            },
    )
}

private fun Face.cornerText(): String? = when (this) {
    Face.DRAW_TWO -> "+2"
    Face.WILD_DRAW_FOUR -> "+4"
    else -> null
}

/** Unplayable cards are drawn a little darker and flatter, never muddied by a scrim. */
private class Tint(private val dimmed: Boolean) {
    operator fun invoke(c: Color): Color {
        if (!dimmed) return c
        val l = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
        return lerp(lerp(c, Color(l, l, l, c.alpha), 0.3f), Color.Black.copy(alpha = c.alpha), 0.3f)
    }
}

private fun lighter(c: Color, t: Float) = lerp(c, Color.White, t)
private fun darker(c: Color, t: Float) = lerp(c, Color.Black, t)

private fun TextMeasurer.label(text: String, px: Float, density: Density): TextLayoutResult = measure(
    text,
    TextStyle(
        fontSize = with(density) { px.toSp() },
        fontWeight = FontWeight.Black,
        fontFamily = GameTheme.displayFont,
        letterSpacing = (-0.04).em,
    ),
    softWrap = false,
    maxLines = 1,
)

// Shared card stock

private fun DrawScope.drawPaperAndBody(body: Brush, tint: Tint) {
    val w = size.width
    drawRoundRect(
        Brush.verticalGradient(listOf(tint(GameTheme.cardPaper), tint(GameTheme.cardPaperEdge))),
        cornerRadius = CornerRadius(w * CORNER),
    )
    val inset = w * INSET
    drawRoundRect(
        body,
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(w * 0.075f),
    )
    // A fine highlight just inside the body's edge.
    val hl = inset + w * 0.022f
    drawRoundRect(
        Color.White.copy(alpha = 0.22f),
        topLeft = Offset(hl, hl),
        size = Size(w - hl * 2, size.height - hl * 2),
        cornerRadius = CornerRadius(w * 0.06f),
        style = Stroke(w * 0.008f),
    )
}

/** The tilted oval's frame: everything drawn inside [block] is in the oval's rotated space. */
private inline fun DrawScope.inOval(block: DrawScope.(topLeft: Offset, size: Size) -> Unit) {
    val w = size.width
    val ovalSize = Size(w * OVAL_RX * 2, w * OVAL_RY * 2)
    val topLeft = Offset((size.width - ovalSize.width) / 2, (size.height - ovalSize.height) / 2)
    rotate(OVAL_TILT, pivot = center) { block(topLeft, ovalSize) }
}

private fun DrawScope.drawOvalShadow(topLeft: Offset, ovalSize: Size, alpha: Float) {
    drawOval(Color.Black.copy(alpha = alpha), topLeft = topLeft + Offset(size.width * 0.014f, size.width * 0.026f), size = ovalSize)
}

private fun DrawScope.drawQuadrants(topLeft: Offset, ovalSize: Size, tint: Tint) {
    QUADRANTS.forEachIndexed { i, color ->
        drawArc(tint(color), startAngle = i * 90f - 90f, sweepAngle = 90f, useCenter = true, topLeft = topLeft, size = ovalSize)
    }
}

// Faces

private fun DrawScope.drawFace(card: Card, tint: Tint, bigLabel: TextLayoutResult?, corner: TextLayoutResult?) {
    val w = size.width
    val base = GameTheme.cardColor(card.color)
    val inset = w * INSET
    val bodyBrush = Brush.radialGradient(
        0f to tint(lighter(base, if (card.isWild) 0.12f else 0.22f)),
        0.55f to tint(base),
        1f to tint(darker(base, 0.28f)),
        center = Offset(inset + (w - inset * 2) * 0.3f, inset + (size.height - inset * 2) * 0.22f),
        radius = w * 1.25f,
    )
    drawPaperAndBody(bodyBrush, tint)

    inOval { topLeft, ovalSize ->
        drawOvalShadow(topLeft, ovalSize, 0.22f)
        if (card.isWild) {
            drawQuadrants(topLeft, ovalSize, tint)
            drawOval(tint(Color.White), topLeft, ovalSize, style = Stroke(w * 0.03f))
        } else {
            val shine = Brush.radialGradient(
                listOf(tint(Color.White), tint(GameTheme.cardOvalEdge)),
                center = topLeft + Offset(ovalSize.width * 0.42f, ovalSize.height * 0.36f),
                radius = w * 0.62f,
            )
            drawOval(shine, topLeft, ovalSize)
        }
    }

    val ink = tint(base)
    val shade = tint(darker(base, 0.55f))
    val mid = center
    when (card.face) {
        Face.SKIP -> withShadow(w, shade, ink, mid) { c, color -> skipIcon(c, w * 0.25f, color) }
        Face.REVERSE -> withShadow(w, shade, ink, mid) { c, color -> reverseIcon(c, w * 0.25f, color) }
        Face.DRAW_TWO -> miniCards(listOf(ink, ink), shade, tint)
        Face.WILD_DRAW_FOUR -> miniCards(DRAW_FOUR_FANS.map { tint(it) }, tint(GameTheme.wild), tint)
        Face.WILD -> Unit // the oval says it all
        else -> if (bigLabel != null) {
            drawLabel(bigLabel, mid, ink, shade, w, outline = shade)
            if (card.face == Face.SIX || card.face == Face.NINE) {
                // Tucked right under the digit and a bit narrower than it, like a printed card.
                underline(mid.x, mid.y + w * 0.27f, Size(bigLabel.size.width * 0.75f, w * 0.04f), ink, shade, w * 0.011f)
            }
        }
    }

    val cornerShade = tint(darker(base, 0.65f))
    val white = tint(Color.White)
    // The oval leans away from these two corners, so the marks never run into it. Wide marks (+2, +4)
    // move in from the edge so their outline stays on the color, off the white border.
    val outline = w * 0.045f
    val nearX = max(w * 0.18f, w * (INSET + 0.03f) + (corner?.size?.width ?: 0) / 2f + outline / 2)
    val near = Offset(nearX, w * 0.21f)
    val far = Offset(w - near.x, size.height - near.y)
    for (at in listOf(near, far)) {
        rotate(if (at == near) 0f else 180f, pivot = at) {
            when {
                corner != null -> {
                    // Outlined and shadowed so they read at a glance in a tight fan.
                    drawLabel(corner, at, white, cornerShade, w * 0.9f, outline = cornerShade, outlineWidth = outline)
                    if (card.face == Face.SIX || card.face == Face.NINE) {
                        underline(at.x, at.y + w * 0.108f, Size(w * 0.13f, w * 0.024f), white, cornerShade, w * 0.01f)
                    }
                }
                card.face == Face.SKIP -> withShadow(w * 0.9f, cornerShade, white, at) { c, color -> skipIcon(c, w * 0.09f, color) }
                card.face == Face.REVERSE -> withShadow(w * 0.9f, cornerShade, white, at) { c, color -> reverseIcon(c, w * 0.09f, color) }
                else -> wildPip(at, w * 0.08f, tint)
            }
        }
    }
}

private fun DrawScope.drawBack(words: List<TextLayoutResult>) {
    val w = size.width
    val tint = Tint(false)
    val inset = w * INSET
    val body = Brush.radialGradient(
        listOf(GameTheme.cardBackGlow, GameTheme.cardBackDeep),
        center = Offset(size.width / 2, inset + (size.height - inset * 2) * 0.45f),
        radius = size.height * 0.62f,
    )
    drawPaperAndBody(body, tint)
    inOval { topLeft, ovalSize ->
        drawOvalShadow(topLeft, ovalSize, 0.3f)
        val glow = Brush.radialGradient(
            listOf(GameTheme.cardBackOval, GameTheme.cardBackOvalDeep),
            center = topLeft + Offset(ovalSize.width * 0.4f, ovalSize.height * 0.35f),
            radius = w * 0.65f,
        )
        drawOval(glow, topLeft, ovalSize)
        drawOval(Color.White, topLeft, ovalSize, style = Stroke(w * 0.025f))
    }
    rotate(-18f, pivot = center) {
        drawLabel(words[0], center - Offset(0f, w * 0.1f), Color.White, GameTheme.cardBackInk, w * 0.6f)
        drawLabel(words[1], center + Offset(0f, w * 0.1f), GameTheme.yellow, GameTheme.cardBackInk, w * 0.6f)
    }
}

// Symbols

/**
 * Draws [text] centred on [at], with a soft drop shadow and an optional outline. Shadow offsets
 * scale with [unit] so small corner marks get a small shadow.
 */
private fun DrawScope.drawLabel(
    text: TextLayoutResult,
    at: Offset,
    fill: Color,
    shade: Color,
    unit: Float,
    outline: Color? = null,
    outlineWidth: Float = unit * 0.014f,
) {
    val px = text.layoutInput.style.fontSize.toPx()
    // Centre on the cap height (digits and "+"), not the line box, which sits too high.
    val topLeft = Offset(at.x - text.size.width / 2f, at.y - text.firstBaseline + px * CAP_HEIGHT / 2)
    drawText(text, color = shade.copy(alpha = 0.55f), topLeft = topLeft + Offset(unit * 0.02f, unit * 0.03f))
    if (outline != null) drawText(text, color = outline, topLeft = topLeft, drawStyle = Stroke(outlineWidth, join = StrokeJoin.Round))
    drawText(text, color = fill, topLeft = topLeft)
}

/** The bar that tells a 6 from a 9: outlined in [edge] and shadowed like the digit above it. */
private fun DrawScope.underline(centerX: Float, top: Float, bar: Size, fill: Color, edge: Color, edgeWidth: Float) {
    val topLeft = Offset(centerX - bar.width / 2, top)
    val r = bar.height / 2
    // The shadow drops by about the bar's own thickness, so a thin bar doesn't look doubled.
    drawRoundRect(edge.copy(alpha = 0.45f), topLeft + Offset(bar.height * 0.35f, bar.height * 0.6f), bar, CornerRadius(r))
    drawRoundRect(
        edge,
        topLeft - Offset(edgeWidth, edgeWidth),
        Size(bar.width + edgeWidth * 2, bar.height + edgeWidth * 2),
        CornerRadius(r + edgeWidth),
    )
    drawRoundRect(fill, topLeft, bar, CornerRadius(r))
}

/** Draws an icon twice: once offset in [shade] as its shadow, then on top in [ink]. */
private inline fun DrawScope.withShadow(unit: Float, shade: Color, ink: Color, at: Offset, icon: DrawScope.(Offset, Color) -> Unit) {
    icon(at + Offset(unit * 0.02f, unit * 0.03f), shade.copy(alpha = 0.55f))
    icon(at, ink)
}

private fun DrawScope.skipIcon(c: Offset, r: Float, ink: Color) {
    val sw = r * 0.36f
    val rr = r - sw / 2
    drawCircle(ink, rr, c, style = Stroke(sw))
    val d = rr * SQRT_HALF
    drawLine(ink, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), sw)
}

/** Two straight arrows on the diagonal, pointing opposite ways. */
private fun DrawScope.reverseIcon(c: Offset, r: Float, ink: Color) {
    reverseArrow(c, r, ink, flip = false)
    reverseArrow(c, r, ink, flip = true)
}

private fun DrawScope.reverseArrow(c: Offset, r: Float, ink: Color, flip: Boolean) {
    val sw = r * 0.34f
    val len = r * 1.5f
    val sign = if (flip) -1f else 1f
    val along = Offset(SQRT_HALF, -SQRT_HALF) * sign
    val across = Offset(SQRT_HALF, SQRT_HALF)
    val origin = c - across * (r * 0.36f * sign)
    val tail = origin - along * (len / 2)
    val headBase = origin + along * (len / 2 - r * 0.42f)
    val tip = origin + along * (len / 2)
    val hw = r * 0.36f
    drawLine(ink, tail, headBase, sw, StrokeCap.Round)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(headBase.x + across.x * hw, headBase.y + across.y * hw)
        lineTo(headBase.x - across.x * hw, headBase.y - across.y * hw)
        close()
    }
    drawPath(head, ink)
    drawPath(head, ink, style = Stroke(sw * 0.3f, join = StrokeJoin.Round))
}

/** Little cards in the middle of a draw card: two stacked for +2, four fanned for +4. */
private fun DrawScope.miniCards(fills: List<Color>, shade: Color, tint: Tint) {
    val w = size.width
    val mw = w * 0.2f
    val mh = mw * CARD_ASPECT
    val n = fills.size
    fills.forEachIndexed { i, fill ->
        val t = i - (n - 1) / 2f
        val c = if (n == 2) {
            center + Offset(t * w * 0.06f, t * w * 0.07f)
        } else {
            center + Offset(t * w * 0.094f, abs(t) * w * 0.03f)
        }
        val topLeft = c - Offset(mw / 2, mh / 2)
        rotate(if (n == 2) -14f else t * 14f, pivot = c) {
            val radius = CornerRadius(w * 0.035f)
            drawRoundRect(shade.copy(alpha = 0.45f), topLeft + Offset(w * 0.015f, w * 0.022f), Size(mw, mh), radius)
            drawRoundRect(fill, topLeft, Size(mw, mh), radius)
            drawRoundRect(tint(Color.White), topLeft, Size(mw, mh), radius, style = Stroke(w * 0.018f))
        }
    }
}

private fun DrawScope.wildPip(at: Offset, r: Float, tint: Tint) {
    val topLeft = at - Offset(r, r)
    drawQuadrants(topLeft, Size(r * 2, r * 2), tint)
    drawCircle(tint(Color.White), r, at, style = Stroke(size.width * 0.014f))
}

private const val CORNER = 0.11f
private const val INSET = 0.055f
private const val OVAL_RX = 0.34f
private const val OVAL_RY = 0.54f
/** Leans the oval's top to the right, clear of the top-left and bottom-right corner marks. */
private const val OVAL_TILT = 28f
private const val CAP_HEIGHT = 0.71f
private const val SQRT_HALF = 0.70710677f
private val BACK_WORDS = listOf("COLOR", "CLASH")
private val QUADRANTS get() = listOf(GameTheme.red, GameTheme.blue, GameTheme.yellow, GameTheme.green)
private val DRAW_FOUR_FANS get() = listOf(GameTheme.blue, GameTheme.green, GameTheme.red, GameTheme.yellow)
