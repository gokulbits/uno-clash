package dev.colorclash.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.rules.Card
import dev.colorclash.rules.Face

const val CARD_ASPECT = 1.5f

/** A card face drawn from scratch: colored body, tilted white oval, big symbol, corner marks. */
@Composable
fun CardFace(card: Card, width: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val height = width * CARD_ASPECT
    val symbol = card.face.symbol()
    val ink = if (card.isWild) Color.White else GameTheme.cardColor(card.color)
    Box(modifier.size(width, height)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCardBody(GameTheme.cardColor(card.color))
            if (card.isWild) drawWildOval() else drawOval(Color.White)
        }
        Text(
            symbol,
            Modifier.align(Alignment.Center),
            color = ink,
            fontSize = (width.value * if (symbol.length > 2) 0.26f else 0.42f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = GameTheme.displayFont,
            textAlign = TextAlign.Center,
        )
        val corner = (width.value * 0.18f).sp
        Text(symbol, Modifier.align(Alignment.TopStart).padding(width * 0.07f), color = Color.White, fontSize = corner, fontWeight = FontWeight.Black)
        Text(
            symbol,
            Modifier.align(Alignment.BottomEnd).padding(width * 0.07f).rotate(180f),
            color = Color.White, fontSize = corner, fontWeight = FontWeight.Black,
        )
        if (dimmed) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(Color.Black.copy(alpha = 0.45f), cornerRadius = CornerRadius(size.width * 0.12f))
            }
        }
    }
}

/** The back every face-down card shares. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width, width * CARD_ASPECT)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCardBody(GameTheme.wild)
            drawWildOval()
        }
        Text(
            "CC",
            Modifier.align(Alignment.Center).rotate(-20f),
            color = Color.White,
            fontSize = (width.value * 0.3f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = GameTheme.displayFont,
        )
    }
}

private fun DrawScope.drawCardBody(fill: Color) {
    val radius = CornerRadius(size.width * 0.12f)
    drawRoundRect(Color.White, cornerRadius = radius)
    val inset = size.width * 0.05f
    drawRoundRect(
        fill,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(size.width * 0.09f),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.15f), cornerRadius = radius, style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawOval(color: Color) {
    val ovalSize = Size(size.width * 0.78f, size.height * 0.62f)
    rotate(-28f) {
        drawOval(color, topLeft = Offset((size.width - ovalSize.width) / 2, (size.height - ovalSize.height) / 2), size = ovalSize)
    }
}

/** Four-color oval for wilds and the card back. */
private fun DrawScope.drawWildOval() {
    val ovalSize = Size(size.width * 0.78f, size.height * 0.62f)
    val topLeft = Offset((size.width - ovalSize.width) / 2, (size.height - ovalSize.height) / 2)
    rotate(-28f) {
        listOf(GameTheme.red, GameTheme.blue, GameTheme.yellow, GameTheme.green).forEachIndexed { i, color ->
            drawArc(color, startAngle = i * 90f - 90f, sweepAngle = 90f, useCenter = true, topLeft = topLeft, size = ovalSize)
        }
    }
}

fun Face.symbol(): String = number?.toString() ?: when (this) {
    Face.SKIP -> "⊘"
    Face.REVERSE -> "⇄"
    Face.DRAW_TWO -> "+2"
    Face.WILD -> "W"
    Face.WILD_DRAW_FOUR -> "+4"
    else -> "?"
}
