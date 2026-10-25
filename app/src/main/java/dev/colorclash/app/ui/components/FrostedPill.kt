package dev.colorclash.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.theme.GameTheme

/** The frosted glass look of the game's top bar, as a pill with an icon and a label. */
@Composable
fun FrostedPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: DrawScope.() -> Unit) {
    Row(
        modifier
            .height(42.dp)
            .clip(PILL)
            .background(Brush.verticalGradient(listOf(GameTheme.panelStrong, GameTheme.panel)))
            .border(1.dp, GameTheme.glassEdge, PILL)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(18.dp), onDraw = icon)
        Text(text, color = GameTheme.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

fun DrawScope.exitIcon() {
    val w = size.width
    val stroke = Stroke(w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val door = Path().apply {
        moveTo(w * 0.5f, w * 0.12f)
        lineTo(w * 0.88f, w * 0.12f)
        lineTo(w * 0.88f, w * 0.88f)
        lineTo(w * 0.5f, w * 0.88f)
    }
    drawPath(door, GameTheme.text, style = stroke)
    val arrow = Path().apply {
        moveTo(w * 0.66f, w * 0.5f)
        lineTo(w * 0.12f, w * 0.5f)
        moveTo(w * 0.32f, w * 0.3f)
        lineTo(w * 0.12f, w * 0.5f)
        lineTo(w * 0.32f, w * 0.7f)
    }
    drawPath(arrow, GameTheme.text, style = stroke)
}

fun DrawScope.gearIcon() {
    val w = size.width
    val tooth = Size(w * 0.2f, w * 0.22f)
    repeat(8) { i ->
        rotate(i * 45f) {
            drawRoundRect(
                GameTheme.text,
                topLeft = Offset(center.x - tooth.width / 2, w * 0.02f),
                size = tooth,
                cornerRadius = CornerRadius(w * 0.05f),
            )
        }
    }
    drawCircle(GameTheme.text, radius = w * 0.27f, style = Stroke(w * 0.15f))
}

private val PILL = RoundedCornerShape(50)
