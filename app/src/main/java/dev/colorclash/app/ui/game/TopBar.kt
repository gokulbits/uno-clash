package dev.colorclash.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.colorclash.protocol.PlayerView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Leave, round and game length, your score, settings. */
@Composable
internal fun TableTopBar(view: PlayerView, onLeave: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val target = view.settings.rules.targetScore
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassButton(label = "Leave game", onClick = onLeave) { exitIcon() }
        RoundBadge(view.round, target, Modifier.weight(1f).wrapContentWidth())
        ScoreChip(view.seats[view.you].score, target)
        GlassButton(label = "Settings", onClick = onSettings) { gearIcon() }
    }
}

@Composable
private fun GlassButton(label: String, onClick: () -> Unit, icon: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(TOP_BAR_HEIGHT)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(GameTheme.panelStrong, GameTheme.panel)))
            .border(1.dp, GameTheme.glassEdge, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp), onDraw = icon)
    }
}

@Composable
private fun RoundBadge(round: Int, target: Int, modifier: Modifier) {
    Column(
        modifier
            .height(TOP_BAR_HEIGHT)
            .clip(PILL)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, PILL)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorDots()
            Text("ROUND $round", color = GameTheme.text, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.5.sp, maxLines = 1)
        }
        Text(
            if (target == 0) "One round game" else "First to $target",
            color = GameTheme.mutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ColorDots() {
    Canvas(Modifier.size(12.dp)) {
        val r = size.width * 0.16f
        val c = center
        val d = size.width * 0.34f
        drawCircle(GameTheme.red, r, c + Offset(0f, -d))
        drawCircle(GameTheme.yellow, r, c + Offset(d, 0f))
        drawCircle(GameTheme.green, r, c + Offset(0f, d))
        drawCircle(GameTheme.blue, r, c + Offset(-d, 0f))
    }
}

/** Counts up as rounds pay out, with progress to the target. */
@Composable
private fun ScoreChip(score: Int, target: Int) {
    val shown by animateIntAsState(score, tween(900), label = "score")
    val progress by animateFloatAsState(
        if (target > 0) (score.toFloat() / target).coerceIn(0f, 1f) else 0f,
        tween(900),
        label = "scoreProgress",
    )
    Box(
        Modifier
            .height(TOP_BAR_HEIGHT)
            .clip(PILL)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, PILL),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            // Sits a little high to leave room for the progress track underneath.
            Modifier.padding(start = 10.dp, end = 14.dp, bottom = if (target > 0) 5.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Canvas(Modifier.size(16.dp)) { star() }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$shown", Modifier.alignByBaseline(), color = GameTheme.text, fontWeight = FontWeight.Black, fontSize = 17.sp, lineHeight = 18.sp, maxLines = 1)
                Text("pts", Modifier.alignByBaseline(), color = GameTheme.mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (target > 0) {
            Canvas(Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth().height(3.dp)) {
                val corner = CornerRadius(size.height / 2)
                drawRoundRect(GameTheme.panelStrong, cornerRadius = corner)
                if (progress > 0f) drawRoundRect(GameTheme.yellow, size = Size(size.width * progress, size.height), cornerRadius = corner)
            }
        }
    }
}

private fun DrawScope.exitIcon() {
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

private fun DrawScope.gearIcon() {
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

private fun DrawScope.star() {
    val outer = size.width / 2
    val inner = outer * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val p = center + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, GameTheme.yellow)
}

private val TOP_BAR_HEIGHT = 42.dp
private val PILL = RoundedCornerShape(50)
