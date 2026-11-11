package dev.colorclash.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import dev.colorclash.app.ui.theme.BackPattern
import dev.colorclash.app.ui.theme.CardBackStyle
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalCardBack
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Face
import dev.colorclash.rules.Side
// The same package holds the CardBack composable, so the card's own side goes by another name here.
import dev.colorclash.rules.CardBack as CardSide
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val CARD_ASPECT = 1.5f

/** Colorblind mode: each color also gets its own shape. */
val LocalColorShapes = staticCompositionLocalOf { false }

/**
 * Shared by every card on screen. A card's labels repeat across the deck, so one cache means dealing
 * a hand mostly finds them measured already, where each card used to bring its own empty cache.
 */
val LocalCardMeasurer = staticCompositionLocalOf<TextMeasurer?> { null }

/**
 * Which side of a Flip deck is face up. Every card but a wild carries a color that says which side
 * it belongs to; a wild has none, so it takes the table's.
 */
val LocalCardSide = staticCompositionLocalOf { Side.LIGHT }

val HAND_CARD_WIDTH = 76.dp
val TABLE_CARD_WIDTH = 92.dp

/** A card face drawn from scratch. Every measurement is a fraction of the card's width. */
@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false, elevation: Dp = 2.dp) {
    val measurer = LocalCardMeasurer.current ?: rememberTextMeasurer()
    val shapes = LocalColorShapes.current
    val dark = card.sideOn(LocalCardSide.current) == Side.DARK
    Spacer(
        modifier
            .size(width, width * CARD_ASPECT)
            .shadow(elevation, RoundedCornerShape(width * CORNER))
            .drawWithCache {
                val face = faceCache(card, dimmed, measurer, dark)
                onDrawBehind { drawFace(card, face, shapes) }
            },
    )
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier, elevation: Dp = 2.dp, style: CardBackStyle = LocalCardBack.current) {
    val measurer = LocalCardMeasurer.current ?: rememberTextMeasurer()
    Spacer(
        modifier
            .size(width, width * CARD_ASPECT)
            .shadow(elevation, RoundedCornerShape(width * CORNER))
            .drawWithCache {
                val back = backCache(style, measurer)
                onDrawBehind { drawBack(back, style) }
            },
    )
}

/**
 * Everything a face needs that depends only on the card and its size. Built in the cache block,
 * because applying a Brush builds a Shader: these were rebuilt on every single draw.
 */
private class FaceCache(
    val tint: Tint,
    val paper: Brush,
    val body: Brush,
    /** The oval's gloss. Null for a wild, which gets four quadrants instead. */
    val shine: Brush?,
    val big: TextLayoutResult?,
    val corner: TextLayoutResult?,
    val ink: Color,
    val shade: Color,
    val cornerShade: Color,
    val white: Color,
    /** The two opposite corner marks, near first. */
    val corners: List<Offset>,
    val quadrants: List<Color>,
    /** The little cards in the middle of a +2 or +4. */
    val fills: List<Color>,
    /** The dark side's color ring around the oval, and the halo its marks are cut out of. Null on the light side. */
    val rim: Color? = null,
)

/**
 * A Flip card belongs to the side its color is on. A wild has no color: the two that only exist on
 * one side are placed by their face, and a plain Wild takes whichever side the table is on.
 */
private fun Card.sideOn(inPlay: Side): Side = when {
    color != null -> checkNotNull(color).side
    face == Face.WILD_DRAW_COLOR -> Side.DARK
    face == Face.WILD -> inPlay
    else -> Side.LIGHT
}

private fun CacheDrawScope.faceCache(card: Card, dimmed: Boolean, measurer: TextMeasurer, dark: Boolean): FaceCache {
    val w = size.width
    val tint = Tint(dimmed)
    val number = card.face.number?.toString()
    val bigText = number ?: card.face.centerText()
    val big = bigText?.let { measurer.label(it, bigTextSize(it) * w, this) }
    val cornerText = number ?: card.face.cornerText()
    val corner = cornerText?.let { measurer.label(it, if (it.length > 1) 0.18f * w else 0.24f * w, this) }

    val base = GameTheme.cardColor(card.color)
    val inset = w * INSET
    // The dark side keeps the color but turns the light down on it, so the marks glow off black.
    val body = Brush.radialGradient(
        0f to tint(if (dark) darker(base, 0.42f) else lighter(base, if (card.isWild) 0.12f else 0.22f)),
        0.55f to tint(if (dark) darker(base, 0.62f) else base),
        1f to tint(darker(base, if (dark) 0.84f else 0.28f)),
        center = Offset(inset + (w - inset * 2) * 0.3f, inset + (size.height - inset * 2) * 0.22f),
        radius = w * 1.25f,
    )
    val oval = ovalBounds()
    val shine = when {
        card.isWild -> null
        dark -> Brush.radialGradient(
            listOf(tint(GameTheme.darkCardOval), tint(GameTheme.darkCardOvalEdge)),
            center = oval.topLeft + Offset(oval.width * 0.42f, oval.height * 0.36f),
            radius = w * 0.62f,
        )
        else -> Brush.radialGradient(
            listOf(tint(GameTheme.cardPaper), tint(GameTheme.cardOvalEdge)),
            center = oval.topLeft + Offset(oval.width * 0.42f, oval.height * 0.36f),
            radius = w * 0.62f,
        )
    }
    // Wide marks (+2, +4) move in from the edge so their outline stays on the color.
    val outline = w * 0.045f
    val nearX = max(w * 0.18f, w * (INSET + 0.03f) + (corner?.size?.width ?: 0) / 2f + outline / 2)
    val near = Offset(nearX, w * 0.21f)
    return FaceCache(
        tint = tint,
        paper = Brush.verticalGradient(
            if (dark) {
                listOf(tint(GameTheme.darkCardPaper), tint(GameTheme.darkCardPaperEdge))
            } else {
                listOf(tint(GameTheme.cardPaper), tint(GameTheme.cardPaperEdge))
            },
        ),
        body = body,
        shine = shine,
        big = big,
        corner = corner,
        // On black the marks go white and the color rings them instead.
        ink = if (dark) tint(GameTheme.cardPaper) else tint(base),
        shade = tint(darker(base, if (dark) 0.88f else 0.55f)),
        cornerShade = tint(darker(base, if (dark) 0.9f else 0.65f)),
        white = tint(GameTheme.cardPaper),
        corners = listOf(near, Offset(w - near.x, size.height - near.y)),
        quadrants = (if (dark) DARK_QUADRANTS else QUADRANTS).map { tint(it) },
        fills = when (card.face) {
            Face.DRAW_ONE -> listOf(tint(base))
            Face.DRAW_TWO -> listOf(tint(base), tint(base))
            Face.DRAW_FOUR -> List(4) { tint(base) }
            Face.DRAW_FIVE -> List(5) { tint(base) }
            Face.DISCARD_ALL -> List(3) { tint(base) }
            Face.WILD_DRAW_FOUR -> DRAW_FOUR_FANS.map { tint(it) }
            Face.WILD_DRAW_TWO -> DRAW_TWO_FANS.map { tint(it) }
            else -> emptyList()
        },
        rim = if (dark) tint(base) else null,
    )
}

/** Everything a card back needs that depends only on its style and size. */
private class BackCache(
    val words: List<TextLayoutResult>,
    val paper: Brush,
    val body: Brush,
    val glow: Brush,
    /** The rounded body, so a pattern stays inside the card's edge. Null when there's no pattern. */
    val clip: Path?,
    /** Refilled per tile instead of a Path per diamond or wave. */
    val scratch: Path,
)

private fun CacheDrawScope.backCache(style: CardBackStyle, measurer: TextMeasurer): BackCache {
    val w = size.width
    val inset = w * INSET
    val oval = ovalBounds()
    return BackCache(
        words = BACK_WORDS.map { measurer.label(it, 0.17f * w, this) },
        paper = Brush.verticalGradient(listOf(GameTheme.cardPaper, GameTheme.cardPaperEdge)),
        body = Brush.radialGradient(
            listOf(style.glow, style.deep),
            center = Offset(w / 2, inset + (size.height - inset * 2) * 0.45f),
            radius = size.height * 0.62f,
        ),
        glow = Brush.radialGradient(
            listOf(style.oval, style.ovalDeep),
            center = oval.topLeft + Offset(oval.width * 0.4f, oval.height * 0.35f),
            radius = w * 0.65f,
        ),
        clip = if (style.pattern == BackPattern.PLAIN) {
            null
        } else {
            Path().apply { addRoundRect(RoundRect(Rect(inset, inset, w - inset, size.height - inset), CornerRadius(w * 0.075f))) }
        },
        scratch = Path(),
    )
}

/** The tilted oval, in the card's own coordinates. Same maths in the cache block and in the draw. */
private fun CacheDrawScope.ovalBounds(): Rect {
    val ovalSize = Size(size.width * OVAL_RX * 2, size.width * OVAL_RY * 2)
    return Rect(Offset((size.width - ovalSize.width) / 2, (size.height - ovalSize.height) / 2), ovalSize)
}

/**
 * An opponent's hand as a fan, so the count reads at a glance. One canvas for the whole hand.
 *
 * [facing] is the side of each card turned towards the table, which Flip makes public: given it,
 * the fan shows those colors instead of card backs. Too small for the faces, so the popup has those.
 */
@Composable
fun HandFan(count: Int, modifier: Modifier = Modifier, facing: List<CardSide> = emptyList()) {
    val style = LocalCardBack.current
    val shapes = LocalColorShapes.current
    Canvas(modifier.size(FAN_WIDTH, FAN_HEIGHT)) {
        val cards = if (facing.isEmpty()) count else facing.size
        if (cards <= 0) return@Canvas
        val cw = FAN_CARD_WIDTH.toPx()
        val cardSize = Size(cw, cw * CARD_ASPECT)
        val step = if (cards > 1) min(7.dp.toPx(), 36.dp.toPx() / (cards - 1)) else 0f
        val turn = if (cards > 1) min(7f, 36f / (cards - 1)) else 0f
        val top = 4.dp.toPx()
        val drop = Offset(0.6.dp.toPx(), 0.9.dp.toPx())
        val inset = cw * INSET
        val body = lerp(style.glow, style.deep, 0.5f)
        val oval = lerp(style.oval, style.ovalDeep, 0.5f)
        for (i in 0 until cards) {
            val t = i - (cards - 1) / 2f
            val topLeft = Offset(center.x + t * step - cw / 2, top)
            val cardCenter = topLeft + Offset(cw / 2, cardSize.height / 2)
            val show = facing.getOrNull(i)
            val shown = show?.color
            val innerTopLeft = topLeft + Offset(inset, inset)
            val innerSize = Size(cardSize.width - inset * 2, cardSize.height - inset * 2)
            // Each card leans around a point well below the hand, like cards held in a fan.
            rotate(t * turn, pivot = Offset(cardCenter.x, top + cardSize.height * 1.8f)) {
                drawRoundRect(GameTheme.shadow.copy(alpha = 0.3f), topLeft + drop, cardSize, CornerRadius(cw * CORNER))
                drawRoundRect(GameTheme.cardPaper, topLeft, cardSize, CornerRadius(cw * CORNER))
                val fill = shown?.let { GameTheme.cardColor(it) } ?: body
                drawRoundRect(fill, innerTopLeft, innerSize, CornerRadius(cw * 0.08f))
                when {
                    show == null -> rotate(OVAL_TILT, pivot = cardCenter) {
                        val ovalSize = Size(cw * 0.6f, cw * 0.96f)
                        val ovalTopLeft = cardCenter - Offset(ovalSize.width / 2, ovalSize.height / 2)
                        drawOval(oval, ovalTopLeft, ovalSize)
                        drawOval(GameTheme.cardPaper, ovalTopLeft, ovalSize, style = Stroke(cw * 0.035f))
                    }
                    // A wild has no color of its own, so it keeps the four-way mark it wears on the card.
                    shown == null -> {
                        val half = Size(innerSize.width / 2, innerSize.height / 2)
                        QUADRANTS.forEachIndexed { q, color ->
                            val at = innerTopLeft + Offset(half.width * (q % 2), half.height * (q / 2))
                            drawRect(color, at, half)
                        }
                    }
                    shapes -> colorShape(shown, cardCenter, cw * 0.2f, GameTheme.cardPaper, GameTheme.shadow, cw * 0.04f)
                }
            }
        }
    }
}

private fun Face.cornerText(): String? = when (this) {
    Face.DRAW_ONE -> "+1"
    Face.DRAW_TWO, Face.WILD_DRAW_TWO -> "+2"
    Face.DRAW_FIVE -> "+5"
    Face.DRAW_FOUR, Face.WILD_DRAW_FOUR, Face.WILD_REVERSE_DRAW_FOUR -> "+4"
    Face.WILD_DRAW_SIX -> "+6"
    Face.WILD_DRAW_TEN -> "+10"
    else -> null
}

/** What goes in the middle when there's no digit and no icon for it. */
private fun Face.centerText(): String? = when (this) {
    Face.WILD_DRAW_SIX -> "+6"
    Face.WILD_DRAW_TEN -> "+10"
    else -> null
}

private fun bigTextSize(text: String) = when (text.length) {
    1 -> 0.66f
    2 -> 0.44f
    else -> 0.33f
}

/** Unplayable cards are drawn a little darker and flatter, never muddied by a scrim. */
private class Tint(private val dimmed: Boolean) {
    operator fun invoke(c: Color): Color {
        if (!dimmed) return c
        val l = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
        return lerp(lerp(c, Color(l, l, l, c.alpha), 0.12f), GameTheme.shadow.copy(alpha = c.alpha), 0.22f)
    }
}

private fun lighter(c: Color, t: Float) = lerp(c, GameTheme.cardPaper, t)
private fun darker(c: Color, t: Float) = lerp(c, GameTheme.shadow, t)

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

private fun DrawScope.drawPaperAndBody(paper: Brush, body: Brush) {
    val w = size.width
    drawRoundRect(paper, cornerRadius = CornerRadius(w * CORNER))
    val inset = w * INSET
    drawRoundRect(
        body,
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(w * 0.075f),
    )
    val hl = inset + w * 0.022f
    drawRoundRect(
        GameTheme.cardPaper.copy(alpha = 0.22f),
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
    drawOval(GameTheme.shadow.copy(alpha = alpha), topLeft = topLeft + Offset(size.width * 0.014f, size.width * 0.026f), size = ovalSize)
}

private fun DrawScope.drawQuadrants(topLeft: Offset, ovalSize: Size, colors: List<Color>) {
    colors.forEachIndexed { i, color ->
        drawArc(color, startAngle = i * 90f - 90f, sweepAngle = 90f, useCenter = true, topLeft = topLeft, size = ovalSize)
    }
}

private fun DrawScope.drawFace(card: Card, face: FaceCache, shapes: Boolean) {
    val w = size.width
    drawPaperAndBody(face.paper, face.body)

    val shine = face.shine
    inOval { topLeft, ovalSize ->
        drawOvalShadow(topLeft, ovalSize, 0.22f)
        if (shine == null) {
            drawQuadrants(topLeft, ovalSize, face.quadrants)
            drawOval(face.white, topLeft, ovalSize, style = Stroke(w * 0.03f))
        } else {
            drawOval(shine, topLeft, ovalSize)
            // The dark side's oval is nearly black, so the color becomes a ring around it.
            face.rim?.let { drawOval(it, topLeft, ovalSize, style = Stroke(w * 0.032f)) }
        }
    }

    // What the middle mark is cut out of: the color on a dark card, its own shadow on a light one.
    val edge = face.rim ?: face.shade
    val mid = center
    when (card.face) {
        Face.SKIP -> outlined(w, edge, face.ink, mid, w * 0.009f) { c, color, grow -> skipIcon(c, w * 0.25f, color, grow) }
        Face.SKIP_EVERYONE ->
            outlined(w, edge, face.ink, mid, w * 0.009f) { c, color, grow -> skipEveryoneIcon(c, w * 0.19f, color, grow) }
        Face.REVERSE -> outlined(w, edge, face.ink, mid, w * 0.009f) { c, color, grow -> reverseIcon(c, w * 0.25f, color, grow) }
        Face.FLIP -> outlined(w, edge, face.ink, mid, w * 0.009f) { c, color, grow -> flipIcon(c, w * 0.22f, color, grow) }
        Face.DRAW_ONE, Face.DRAW_TWO, Face.DRAW_FOUR, Face.DRAW_FIVE -> miniCards(face.fills, face.shade, face.white)
        Face.DISCARD_ALL -> {
            translate(top = -w * 0.07f) { miniCards(face.fills, face.shade, face.white) }
            outlined(w, face.shade, face.ink, mid + Offset(0f, w * 0.27f), w * 0.009f) { c, color, grow ->
                downArrow(c, w * 0.1f, color, grow)
            }
        }
        Face.WILD_DRAW_FOUR, Face.WILD_DRAW_TWO -> miniCards(face.fills, face.tint(GameTheme.wild), face.white)
        // Draw until a color turns up: cards coming down onto the color you're waiting on.
        Face.WILD_DRAW_COLOR -> {
            outlined(w, face.cornerShade, face.white, mid - Offset(0f, w * 0.18f), w * 0.014f) { c, color, grow ->
                downArrow(c, w * 0.13f, color, grow)
            }
            wildPip(mid + Offset(0f, w * 0.21f), w * 0.13f, face.quadrants, face.white, face.cornerShade)
        }
        // White over the rainbow oval, like the corner marks: the card's own ink would vanish into it.
        Face.WILD_REVERSE_DRAW_FOUR ->
            outlined(w, face.cornerShade, face.white, mid, w * 0.014f) { c, color, grow -> reverseIcon(c, w * 0.22f, color, grow) }
        Face.WILD_COLOR_ROULETTE -> rouletteIcon(mid, w * 0.19f, face.quadrants, face.white, face.cornerShade)
        Face.WILD_SWAP_HANDS ->
            outlined(w, face.cornerShade, face.white, mid, w * 0.014f) { c, color, grow -> swapIcon(c, w * 0.22f, color, grow) }
        Face.WILD -> Unit // the oval says it all
        Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN -> face.big?.let {
            drawLabel(it, mid, face.white, face.cornerShade, w, outline = face.cornerShade, outlineWidth = w * 0.05f)
        }
        else -> if (face.big != null) {
            drawLabel(face.big, mid, face.ink, face.shade, w, outline = edge)
            if (card.face == Face.SIX || card.face == Face.NINE) {
                // Under the digit and a bit narrower, like a printed card.
                underline(mid.x, mid.y + w * 0.27f, Size(face.big.size.width * 0.75f, w * 0.04f), face.ink, face.shade, w * 0.011f)
            }
        }
    }

    // The oval leans away from these two corners, so the marks never run into it.
    val corner = face.corner
    val outline = w * 0.045f
    face.corners.forEachIndexed { i, at ->
        rotate(if (i == 0) 0f else 180f, pivot = at) {
            when {
                corner != null -> {
                    drawLabel(corner, at, face.white, face.cornerShade, w * 0.9f, outline = face.cornerShade, outlineWidth = outline)
                    if (card.face == Face.SIX || card.face == Face.NINE) {
                        underline(at.x, at.y + w * 0.108f, Size(w * 0.13f, w * 0.024f), face.white, face.cornerShade, w * 0.01f)
                    }
                }
                card.face == Face.SKIP || card.face == Face.SKIP_EVERYONE ->
                    outlined(w * 0.9f, face.cornerShade, face.white, at, w * 0.022f) { c, color, grow -> skipIcon(c, w * 0.09f, color, grow) }
                card.face == Face.REVERSE ->
                    outlined(w * 0.9f, face.cornerShade, face.white, at, w * 0.022f) { c, color, grow -> reverseIcon(c, w * 0.09f, color, grow) }
                card.face == Face.FLIP ->
                    outlined(w * 0.9f, face.cornerShade, face.white, at, w * 0.022f) { c, color, grow -> flipIcon(c, w * 0.08f, color, grow) }
                card.face == Face.DISCARD_ALL ->
                    outlined(w * 0.9f, face.cornerShade, face.white, at, w * 0.022f) { c, color, grow -> downArrow(c, w * 0.085f, color, grow) }
                else -> wildPip(at, w * 0.08f, face.quadrants, face.white, face.cornerShade)
            }
            // Under the corner mark, where it still shows in a tight fan. Drawn relative to the mark, so
            // the bottom corner's half-turn carries it along.
            if (shapes) {
                card.color?.let {
                    colorShape(it, at + Offset(w * 0.18f - face.corners[0].x, w * 0.22f), w * 0.065f, face.white, face.cornerShade, w * 0.015f)
                }
            }
        }
    }
}

/**
 * Colorblind shapes: red triangle, blue square, green circle, yellow diamond, and on a Flip deck's
 * dark side pink cross, teal upside-down triangle, orange hexagon, purple dome.
 */
fun DrawScope.colorShape(color: CardColor, at: Offset, r: Float, fill: Color, edge: Color, edgeWidth: Float) {
    val path = Path().apply {
        when (color) {
            CardColor.RED -> {
                moveTo(at.x, at.y - r)
                lineTo(at.x + r * 1.05f, at.y + r * 0.8f)
                lineTo(at.x - r * 1.05f, at.y + r * 0.8f)
            }
            CardColor.BLUE -> addRect(Rect(at.x - r * 0.85f, at.y - r * 0.85f, at.x + r * 0.85f, at.y + r * 0.85f))
            CardColor.GREEN -> addOval(Rect(at.x - r, at.y - r, at.x + r, at.y + r))
            CardColor.YELLOW -> {
                moveTo(at.x, at.y - r * 1.15f)
                lineTo(at.x + r, at.y)
                lineTo(at.x, at.y + r * 1.15f)
                lineTo(at.x - r, at.y)
            }
            CardColor.PINK -> {
                // Fat arms, so it still reads as a cross at corner-mark size.
                val a = r * 0.4f
                moveTo(at.x - a, at.y - r)
                lineTo(at.x + a, at.y - r)
                lineTo(at.x + a, at.y - a)
                lineTo(at.x + r, at.y - a)
                lineTo(at.x + r, at.y + a)
                lineTo(at.x + a, at.y + a)
                lineTo(at.x + a, at.y + r)
                lineTo(at.x - a, at.y + r)
                lineTo(at.x - a, at.y + a)
                lineTo(at.x - r, at.y + a)
                lineTo(at.x - r, at.y - a)
                lineTo(at.x - a, at.y - a)
            }
            CardColor.TEAL -> {
                moveTo(at.x, at.y + r)
                lineTo(at.x - r * 1.05f, at.y - r * 0.8f)
                lineTo(at.x + r * 1.05f, at.y - r * 0.8f)
            }
            CardColor.ORANGE -> {
                // Flat top and bottom, which tells it from the circle better than points would.
                val x = r * 0.95f
                val y = r * 0.52f
                moveTo(at.x - x * 0.52f, at.y - r)
                lineTo(at.x + x * 0.52f, at.y - r)
                lineTo(at.x + x, at.y - y * 0.1f)
                lineTo(at.x + x * 0.52f, at.y + r)
                lineTo(at.x - x * 0.52f, at.y + r)
                lineTo(at.x - x, at.y - y * 0.1f)
            }
            CardColor.PURPLE -> arcTo(
                Rect(at.x - r * 1.05f, at.y - r * 0.85f, at.x + r * 1.05f, at.y + r * 1.25f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
        }
        close()
    }
    drawPath(path, edge, style = Stroke(edgeWidth * 2, join = StrokeJoin.Round))
    drawPath(path, fill)
}

private fun DrawScope.drawBack(back: BackCache, style: CardBackStyle) {
    val w = size.width
    drawPaperAndBody(back.paper, back.body)
    back.clip?.let { drawPattern(style.pattern, it, back.scratch) }
    inOval { topLeft, ovalSize ->
        drawOvalShadow(topLeft, ovalSize, 0.3f)
        drawOval(back.glow, topLeft, ovalSize)
        drawOval(GameTheme.cardPaper, topLeft, ovalSize, style = Stroke(w * 0.025f))
    }
    rotate(-18f, pivot = center) {
        drawLabel(back.words[0], center - Offset(0f, w * 0.1f), GameTheme.cardPaper, style.ink, w * 0.6f)
        drawLabel(back.words[1], center + Offset(0f, w * 0.1f), style.accent, style.ink, w * 0.6f)
    }
}

/** A faint repeating pattern over a card back's body, kept inside its rounded edge. */
private fun DrawScope.drawPattern(pattern: BackPattern, clip: Path, scratch: Path) {
    val w = size.width
    val inset = w * INSET
    val body = Rect(inset, inset, w - inset, size.height - inset)
    val ink = GameTheme.cardPaper.copy(alpha = 0.09f)
    clipPath(clip) {
        val step = w * 0.16f
        when (pattern) {
            BackPattern.DIAMONDS -> {
                val r = step * 0.32f
                var row = 0
                var y = body.top
                while (y < body.bottom + step) {
                    var x = body.left + if (row % 2 == 0) 0f else step / 2
                    while (x < body.right + step) {
                        // One path, refilled: this used to allocate a Path per diamond.
                        scratch.rewind()
                        scratch.moveTo(x, y - r)
                        scratch.lineTo(x + r, y)
                        scratch.lineTo(x, y + r)
                        scratch.lineTo(x - r, y)
                        scratch.close()
                        drawPath(scratch, ink)
                        x += step
                    }
                    y += step / 2
                    row++
                }
            }
            BackPattern.STRIPES -> {
                var x = body.left - body.height
                while (x < body.right) {
                    drawLine(ink, Offset(x, body.bottom), Offset(x + body.height, body.top), strokeWidth = step * 0.28f)
                    x += step
                }
            }
            BackPattern.DOTS -> {
                var row = 0
                var y = body.top + step / 2
                while (y < body.bottom) {
                    var x = body.left + if (row % 2 == 0) step / 2 else step
                    while (x < body.right) {
                        drawCircle(ink, step * 0.2f, Offset(x, y))
                        x += step
                    }
                    y += step * 0.75f
                    row++
                }
            }
            BackPattern.WAVES -> {
                var y = body.top
                while (y < body.bottom + step) {
                    scratch.rewind()
                    scratch.moveTo(body.left, y)
                    var x = body.left
                    while (x < body.right) {
                        scratch.quadraticTo(x + step / 4, y - step * 0.22f, x + step / 2, y)
                        scratch.quadraticTo(x + step * 0.75f, y + step * 0.22f, x + step, y)
                        x += step
                    }
                    drawPath(scratch, ink, style = Stroke(step * 0.14f, cap = StrokeCap.Round))
                    y += step * 0.6f
                }
            }
            BackPattern.PLAIN -> Unit
        }
    }
}

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
    // Fill has to be passed explicitly: on Android the text paint keeps the last stroke otherwise,
    // and the "fill" comes out as another outline.
    drawText(text, color = shade.copy(alpha = 0.55f), topLeft = topLeft + Offset(unit * 0.02f, unit * 0.03f), drawStyle = Fill)
    if (outline != null) drawText(text, color = outline, topLeft = topLeft, drawStyle = Stroke(outlineWidth, join = StrokeJoin.Round))
    drawText(text, color = fill, topLeft = topLeft, drawStyle = Fill)
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

/**
 * Draws an icon like the digits: a soft shadow, then the icon grown by [edgeWidth] all round in [shade]
 * as its outline, then the icon itself in [ink]. [icon] gets how much to grow its strokes by.
 */
private inline fun DrawScope.outlined(
    unit: Float,
    shade: Color,
    ink: Color,
    at: Offset,
    edgeWidth: Float,
    icon: DrawScope.(Offset, Color, Float) -> Unit,
) {
    icon(at + Offset(unit * 0.02f, unit * 0.03f), shade.copy(alpha = 0.55f), edgeWidth)
    icon(at, shade, edgeWidth)
    icon(at, ink, 0f)
}

private fun DrawScope.skipIcon(c: Offset, r: Float, ink: Color, grow: Float) {
    val sw = r * 0.36f
    val rr = r - sw / 2
    drawCircle(ink, rr, c, style = Stroke(sw + grow * 2))
    val d = rr * SQRT_HALF
    drawLine(ink, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), sw + grow * 2)
}

/** Skip Everyone: the no-entry sign with a little one either side, so it reads as all of them. */
private fun DrawScope.skipEveryoneIcon(c: Offset, r: Float, ink: Color, grow: Float) {
    skipIcon(c - Offset(r * 1.45f, 0f), r * 0.52f, ink, grow * 0.6f)
    skipIcon(c + Offset(r * 1.45f, 0f), r * 0.52f, ink, grow * 0.6f)
    skipIcon(c, r, ink, grow)
}

/** Discard All: the cards above it are on their way to the pile. */
private fun DrawScope.downArrow(c: Offset, r: Float, ink: Color, grow: Float) {
    val sw = r * 0.5f
    val head = r * 0.85f
    drawLine(ink, Offset(c.x, c.y - r), Offset(c.x, c.y + r - head * 0.7f), sw + grow * 2, StrokeCap.Round)
    val tip = Path().apply {
        moveTo(c.x, c.y + r)
        lineTo(c.x + head, c.y + r - head)
        lineTo(c.x - head, c.y + r - head)
        close()
    }
    drawPath(tip, ink)
    drawPath(tip, ink, style = Stroke(sw * 0.3f + grow * 2, join = StrokeJoin.Round))
}

/** Color Roulette: a little four-color wheel with a pointer over it. */
private fun DrawScope.rouletteIcon(c: Offset, r: Float, quadrants: List<Color>, white: Color, edge: Color) {
    val w = size.width
    drawCircle(edge.copy(alpha = 0.45f), r, c + Offset(w * 0.02f, w * 0.03f))
    wildPip(c, r, quadrants, white, edge)
    val spoke = w * 0.016f
    drawLine(white, c, c + Offset(r * 0.78f, -r * 0.78f), spoke * 2, StrokeCap.Round)
    drawCircle(edge, spoke * 1.9f, c)
    drawCircle(white, spoke * 1.2f, c)
}

/** Swap Hands: two arrows passing each other, one hand going each way. */
private fun DrawScope.swapIcon(c: Offset, r: Float, ink: Color, grow: Float) {
    swapArrow(c - Offset(0f, r * 0.45f), r, ink, right = true, grow)
    swapArrow(c + Offset(0f, r * 0.45f), r, ink, right = false, grow)
}

private fun DrawScope.swapArrow(at: Offset, r: Float, ink: Color, right: Boolean, grow: Float) {
    val sw = r * 0.3f
    val half = r * 1.05f
    val head = r * 0.44f
    val sign = if (right) 1f else -1f
    val tip = Offset(at.x + half * sign, at.y)
    drawLine(ink, Offset(at.x - half * sign, at.y), Offset(tip.x - head * sign, at.y), sw + grow * 2, StrokeCap.Round)
    val point = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - head * sign, tip.y - head * 0.78f)
        lineTo(tip.x - head * sign, tip.y + head * 0.78f)
        close()
    }
    drawPath(point, ink)
    drawPath(point, ink, style = Stroke(sw * 0.3f + grow * 2, join = StrokeJoin.Round))
}

/**
 * Flip: one card caught halfway over — the side going away behind, the side coming up in front —
 * with an arrow round each end for which way it turns. The mode's signature card.
 */
private fun DrawScope.flipIcon(c: Offset, r: Float, ink: Color, grow: Float) {
    val cw = r * 0.8f
    val ch = cw * CARD_ASPECT
    // Different leans, so the pair still reads as two cards once they overlap.
    drawTurningCard(c + Offset(-r * 0.3f, -r * 0.12f), cw * 0.9f, ch * 0.9f, -26f, ink, grow)
    drawTurningCard(c + Offset(r * 0.28f, r * 0.14f), cw, ch, 8f, ink, grow)
    flipArrow(c, r * 1.25f, ink, from = 198f, grow)
    flipArrow(c, r * 1.25f, ink, from = 18f, grow)
}

private fun DrawScope.drawTurningCard(at: Offset, cw: Float, ch: Float, turn: Float, ink: Color, grow: Float) {
    rotate(turn, pivot = at) {
        drawRoundRect(
            ink,
            topLeft = Offset(at.x - cw / 2 - grow, at.y - ch / 2 - grow),
            size = Size(cw + grow * 2, ch + grow * 2),
            cornerRadius = CornerRadius(cw * 0.26f + grow),
        )
    }
}

/** One of the flip arrows: an arc clockwise from [from], head on its end. */
private fun DrawScope.flipArrow(c: Offset, r: Float, ink: Color, from: Float, grow: Float) {
    val sw = r * 0.2f
    val sweep = 86f
    drawArc(
        ink,
        startAngle = from,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(sw + grow * 2, cap = StrokeCap.Round),
    )
    val a = (from + sweep) * DEG
    val on = Offset(c.x + r * cos(a), c.y + r * sin(a))
    // Along the circle for the tip, across it for the head's base.
    val along = Offset(-sin(a), cos(a))
    val across = Offset(cos(a), sin(a))
    val head = r * 0.36f
    val tip = on + along * head
    val point = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(on.x + across.x * head * 0.7f, on.y + across.y * head * 0.7f)
        lineTo(on.x - across.x * head * 0.7f, on.y - across.y * head * 0.7f)
        close()
    }
    drawPath(point, ink)
    drawPath(point, ink, style = Stroke(sw * 0.3f + grow * 2, join = StrokeJoin.Round))
}

private fun DrawScope.reverseIcon(c: Offset, r: Float, ink: Color, grow: Float) {
    reverseArrow(c, r, ink, flip = false, grow)
    reverseArrow(c, r, ink, flip = true, grow)
}

private fun DrawScope.reverseArrow(c: Offset, r: Float, ink: Color, flip: Boolean, grow: Float) {
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
    drawLine(ink, tail, headBase, sw + grow * 2, StrokeCap.Round)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(headBase.x + across.x * hw, headBase.y + across.y * hw)
        lineTo(headBase.x - across.x * hw, headBase.y - across.y * hw)
        close()
    }
    drawPath(head, ink)
    drawPath(head, ink, style = Stroke(sw * 0.3f + grow * 2, join = StrokeJoin.Round))
}

/** Little cards in the middle of a draw card: one or two stacked, three or more fanned. */
private fun DrawScope.miniCards(fills: List<Color>, shade: Color, white: Color) {
    val w = size.width
    val mw = w * 0.2f
    val mh = mw * CARD_ASPECT
    val n = fills.size
    // Five lean tighter than four, so the fan stays inside the oval.
    val spread = if (n >= 5) w * 0.072f else w * 0.094f
    val turn = if (n >= 5) 11f else 14f
    fills.forEachIndexed { i, fill ->
        val t = i - (n - 1) / 2f
        val c = if (n <= 2) {
            center + Offset(t * w * 0.06f, t * w * 0.07f)
        } else {
            center + Offset(t * spread, abs(t) * w * 0.03f)
        }
        val topLeft = c - Offset(mw / 2, mh / 2)
        rotate(if (n <= 2) -14f else t * turn, pivot = c) {
            val radius = CornerRadius(w * 0.035f)
            val edge = w * 0.012f
            drawRoundRect(shade.copy(alpha = 0.45f), topLeft + Offset(w * 0.015f, w * 0.022f), Size(mw, mh), radius)
            // A dark rim outside the white border, so the cards stand out from the white oval.
            drawRoundRect(shade, topLeft - Offset(edge, edge), Size(mw + edge * 2, mh + edge * 2), CornerRadius(radius.x + edge))
            drawRoundRect(fill, topLeft, Size(mw, mh), radius)
            drawRoundRect(white, topLeft, Size(mw, mh), radius, style = Stroke(w * 0.018f))
        }
    }
}

private fun DrawScope.wildPip(at: Offset, r: Float, quadrants: List<Color>, white: Color, edge: Color) {
    val topLeft = at - Offset(r, r)
    val ring = size.width * 0.014f
    drawCircle(edge, r + ring, at, style = Stroke(ring))
    drawQuadrants(topLeft, Size(r * 2, r * 2), quadrants)
    drawCircle(white, r, at, style = Stroke(ring))
}

private val FAN_CARD_WIDTH = 20.dp
private val FAN_WIDTH = 76.dp
private val FAN_HEIGHT = 40.dp
private const val CORNER = 0.11f
private const val INSET = 0.055f
private const val OVAL_RX = 0.34f
private const val OVAL_RY = 0.54f
/** Leans the oval's top to the right, clear of the top-left and bottom-right corner marks. */
private const val OVAL_TILT = 28f
private const val CAP_HEIGHT = 0.71f
private const val SQRT_HALF = 0.70710677f
private const val DEG = 0.017453292f
private val BACK_WORDS = listOf("COLOR", "CLASH")
// Plain vals, not get(): a `get() = listOf(...)` rebuilt these four-element lists on every draw.
private val QUADRANTS = listOf(GameTheme.red, GameTheme.blue, GameTheme.yellow, GameTheme.green)
private val DARK_QUADRANTS = listOf(GameTheme.pink, GameTheme.teal, GameTheme.orange, GameTheme.purple)
private val DRAW_FOUR_FANS = listOf(GameTheme.blue, GameTheme.green, GameTheme.red, GameTheme.yellow)
private val DRAW_TWO_FANS = listOf(GameTheme.blue, GameTheme.red)
