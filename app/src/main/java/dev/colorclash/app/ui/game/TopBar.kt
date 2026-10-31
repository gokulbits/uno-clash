package dev.colorclash.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.components.RoundIconButton
import dev.colorclash.app.ui.components.colorDotsIcon
import dev.colorclash.app.ui.components.exitIcon
import dev.colorclash.app.ui.components.gearIcon
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
        RoundIconButton("Leave game", onLeave) { exitIcon(it) }
        RoundBadge(view.round, target, Modifier.weight(1f).wrapContentWidth())
        ScoreChip(view.seats[view.you].score, target)
        RoundIconButton("Settings", onSettings) { gearIcon(it) }
    }
}

@Composable
private fun RoundBadge(round: Int, target: Int, modifier: Modifier) {
    Column(
        modifier
            .height(TOP_BAR_HEIGHT)
            .clip(GameTheme.pillShape)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, GameTheme.pillShape)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(12.dp)) { colorDotsIcon() }
            Text("ROUND $round", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, maxLines = 1)
        }
        Text(
            if (target == 0) "One round game" else "First to $target",
            color = GameTheme.mutedText,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
        )
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
            .clip(GameTheme.pillShape)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, GameTheme.pillShape),
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
                Text("$shown", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, lineHeight = 18.sp, maxLines = 1)
                Text("pts", Modifier.alignByBaseline(), color = GameTheme.mutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (target > 0) {
            val empty = GameTheme.panelStrong
            // Matches the chip rather than filling the width on offer: that stretched the chip right
            // across the bar, pushing the round badge and the settings button out of sight.
            Canvas(Modifier.matchParentSize()) {
                val inset = 14.dp.toPx()
                val track = Size(size.width - inset * 2, 3.dp.toPx())
                val at = Offset(inset, size.height - 5.dp.toPx() - track.height)
                val corner = CornerRadius(track.height / 2)
                drawRoundRect(empty, at, track, corner)
                if (progress > 0f) drawRoundRect(GameTheme.highlight, at, Size(track.width * progress, track.height), corner)
            }
        }
    }
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
    drawPath(path, GameTheme.highlight)
}

private val TOP_BAR_HEIGHT = 46.dp
