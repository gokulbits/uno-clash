package dev.colorclash.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.ListRow
import dev.colorclash.app.ui.components.SheetSurface
import dev.colorclash.app.ui.components.SheetTitle
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

private const val MAX_HISTORY_ROWS = 5

/** Dims the table and holds a choice the player has to make. */
@Composable
private fun Overlay(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            // Past its own edges: the table sits inside the system bars, and they should dim with it.
            .drawBehind {
                val bleed = 200.dp.toPx()
                drawRect(GameTheme.scrim, Offset(-bleed, -bleed), Size(size.width + bleed * 2, size.height + bleed * 2))
            }
            // Taps outside close it if it can be closed, and never reach the table underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        SheetSurface(
            // Taps on the card itself mustn't count as tapping outside.
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            onClose = onDismiss,
            content = content,
        )
    }
}

/** The last few cards played this round, newest first, and who played them. */
@Composable
fun RecentPlays(view: PlayerView, plays: List<RecentPlay>, onDismiss: () -> Unit) {
    Overlay(onDismiss = onDismiss) {
        SheetTitle("Recent plays")
        if (plays.isEmpty()) HintText("Nothing played yet this round")
        plays.asReversed().forEach { play ->
            ListRow {
                CardFace(play.card, 34.dp, elevation = 1.dp)
                Text(
                    if (play.seat == view.you) "You" else view.nameOf(play.seat),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HintText(play.card.label())
            }
        }
    }
}

@Composable
fun ColorPicker(hand: List<Card>, onPick: (CardColor) -> Unit) {
    val counts = hand.mapNotNull { it.color }.groupingBy { it }.eachCount()
    Overlay {
        SheetTitle("Pick a color")
        CardColor.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { color ->
                    val fill = GameTheme.cardColor(color)
                    val content = GameTheme.contentOn(fill)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .clip(GameTheme.panelShape)
                            .background(fill)
                            .clickable { onPick(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(color.name.lowercase().replaceFirstChar { it.uppercase() }, color = content, style = MaterialTheme.typography.titleLarge)
                            val count = counts[color] ?: 0
                            Text(if (count == 1) "1 card" else "$count cards", color = content, style = MaterialTheme.typography.bodySmall)
                        }
                        if (LocalColorShapes.current) {
                            Canvas(Modifier.align(Alignment.TopStart).padding(12.dp).size(22.dp)) {
                                colorShape(color, center, size.minDimension * 0.42f, GameTheme.text, GameTheme.scrim, 1.5.dp.toPx())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwapPicker(view: PlayerView, onPick: (Int) -> Unit) {
    Overlay {
        SheetTitle("Swap hands with…")
        opponentOrder(view).forEach { seat ->
            val info = view.seats[seat]
            ListRow(onClick = { onPick(seat) }) {
                AvatarBadge(info.info, size = 40.dp)
                Text(info.info.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (info.cardCount == 1) "1 card" else "${info.cardCount} cards", color = GameTheme.highlight, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** One finished round, collected on this phone as RoundWon events arrive. */
data class RoundResult(val round: Int, val winner: Int, val points: Int)

/** Shown between rounds and at the end. The host moves things along; everyone else waits. */
@Composable
fun ScoreBoard(
    view: PlayerView,
    headline: String,
    history: List<RoundResult>,
    isHost: Boolean,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit,
) {
    val gameOver = view.phase == PhaseKind.GAME_OVER
    val target = view.settings.rules.targetScore
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(view.round, view.phase) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }

    Overlay(onDismiss = onClose) {
        // Moved in the layer and drawn in the bars below, so the spring doesn't recompose all of this.
        Text(
            if (gameOver) "🏆" else "🎉",
            Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
            fontSize = 48.sp,
        )
        SheetTitle(headline)
        view.roundPoints?.let { Text("+$it points", color = GameTheme.highlight, style = MaterialTheme.typography.titleMedium) }

        view.seats.sortedByDescending { it.score }.forEachIndexed { rank, seat ->
            val isWinner = seat.info.seat == view.winner
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(GameTheme.tileShape)
                    .background(GameTheme.panelStrong)
                    .then(if (isWinner) Modifier.border(2.dp, GameTheme.highlight, GameTheme.tileShape) else Modifier)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${rank + 1}", Modifier.width(14.dp), color = GameTheme.mutedText, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    AvatarBadge(seat.info, size = 34.dp)
                    Text(
                        seat.info.name + if (seat.info.seat == view.you) " (you)" else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${seat.score}", style = MaterialTheme.typography.titleMedium)
                }
                // Progress to the target; a one-round game has none.
                if (target > 0) {
                    val filled = (seat.score.toFloat() / target).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        val corner = CornerRadius(size.height / 2)
                        drawRoundRect(GameTheme.panel, cornerRadius = corner)
                        if (filled > 0f) {
                            drawRoundRect(GameTheme.highlight, size = Size(size.width * filled * pop.value.coerceAtMost(1f), size.height), cornerRadius = corner)
                        }
                    }
                }
            }
        }
        HintText(if (target > 0) "Playing to $target" else "One round game")
        NextRoundCountdown(view.nextRoundMillis, view.round)

        if (history.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                history.takeLast(MAX_HISTORY_ROWS).forEach { result ->
                    HintText("Round ${result.round}: ${view.nameOf(result.winner)} +${result.points}")
                }
            }
        }

        when {
            isHost && gameOver -> {
                BigButton("Play again", ButtonTone.PRIMARY, onRematch)
                BigButton("Back to lobby", ButtonTone.ACTION, onBackToLobby)
                BigButton("Close room", ButtonTone.QUIET, onLeave)
            }
            isHost -> {
                BigButton("Next round", ButtonTone.PRIMARY, onNextRound)
                BigButton("Close room", ButtonTone.QUIET, onLeave)
            }
            else -> {
                if (!gameOver && view.nextRoundMillis == null) {
                    HintText("Waiting for the host to start the next round…", textAlign = TextAlign.Center)
                }
                BigButton(if (gameOver) "Leave" else "Leave game", ButtonTone.QUIET, onLeave)
            }
        }
    }
}

/** "Next round in 9" while a round-over scoreboard counts down, run locally from what the host said. */
@Composable
fun NextRoundCountdown(millisLeft: Long?, round: Int) {
    if (millisLeft == null) return
    var seconds by remember(round, millisLeft) { mutableIntStateOf(ceil(millisLeft / 1000.0).toInt()) }
    LaunchedEffect(round, millisLeft) {
        // Monotonic, like the host's own clocks: a wall-clock correction mid-countdown made this jump.
        val deadline = TimeSource.Monotonic.markNow() + millisLeft.milliseconds
        while (seconds > 0) {
            delay(200)
            val left = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
            seconds = ceil(left.inWholeMilliseconds / 1000.0).toInt()
        }
    }
    Text("Next round in $seconds…", color = GameTheme.highlight, style = MaterialTheme.typography.titleSmall)
}
