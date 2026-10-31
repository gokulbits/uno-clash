package dev.colorclash.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_HISTORY_ROWS = 5
private val MAX_WHEEL = 340.dp
/** How much of the wheel's radius is the open middle. */
private const val WHEEL_HOLE = 0.4f
/** Degrees of table showing between the slices. */
private const val WHEEL_GAP = 3f
/** How far the ring from a pick gets, in wheel radii. */
private const val WHEEL_RING_REACH = 1.35f
private const val WHEEL_BURST_MILLIS = 340
private const val WHEEL_SEND_AFTER_MILLIS = 180L
private const val WHEEL_LEAVE_MILLIS = 160
private const val WHEEL_RETRY_MILLIS = 1_500L

/** Dims the table. Taps outside what's on it close it if it can be closed, and never reach the table underneath. */
@Composable
private fun Scrim(onDismiss: (() -> Unit)? = null, dim: () -> Float = { 1f }, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            // Past its own edges: the table sits inside the system bars, and they should dim with it.
            .drawBehind {
                val bleed = 200.dp.toPx()
                drawRect(GameTheme.scrim, Offset(-bleed, -bleed), Size(size.width + bleed * 2, size.height + bleed * 2), alpha = dim())
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Dims the table and holds a choice the player has to make. */
@Composable
private fun Overlay(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scrim(onDismiss) {
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

/**
 * The four colors as a wheel, spinning in over the dimmed table with your hand still in view below it.
 * A tap plays [onTap] straight away and a short effect, and the pick goes to the host partway through
 * so the effect has time to be seen.
 */
@Composable
fun ColorPicker(hand: List<Card>, onPick: (CardColor) -> Unit, onTap: () -> Unit) {
    val counts = hand.mapNotNull { it.color }.groupingBy { it }.eachCount()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    var picked by remember { mutableStateOf<CardColor?>(null) }
    val burst = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val latestOnPick by rememberUpdatedState(onPick)
    LaunchedEffect(picked) {
        val color = picked ?: return@LaunchedEffect
        launch { burst.animateTo(1f, tween(WHEEL_BURST_MILLIS)) }
        delay(WHEEL_SEND_AFTER_MILLIS)
        latestOnPick(color)
        delay(WHEEL_BURST_MILLIS - WHEEL_SEND_AFTER_MILLIS)
        leave.animateTo(1f, tween(WHEEL_LEAVE_MILLIS))
        // Normally gone by now. Still here means the pick was lost with the connection, so offer it again.
        delay(WHEEL_RETRY_MILLIS)
        burst.snapTo(0f)
        leave.snapTo(0f)
        picked = null
    }
    Scrim(dim = { 1f - leave.value }) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val wheel = minOf(maxWidth - GameTheme.screenPadding * 2, MAX_WHEEL)
            val hubColor = LocalTableStyle.current.top
            Box(
                Modifier
                    .size(wheel)
                    .graphicsLayer {
                        val scale = (0.6f + 0.4f * appear.value) * (1f + 0.08f * leave.value)
                        scaleX = scale
                        scaleY = scale
                        rotationZ = -60f * (1f - appear.value)
                        alpha = appear.value.coerceIn(0f, 1f) * (1f - leave.value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Solid behind the slices, so the table's cards and text don't show through the gaps.
                Box(Modifier.fillMaxSize().clip(CircleShape).background(hubColor))
                CardColor.entries.forEachIndexed { i, color ->
                    WheelSlice(color, i, counts[color] ?: 0, wheel, picked, { burst.value }) {
                        if (picked == null) {
                            picked = color
                            onTap()
                        }
                    }
                }
                val choice = picked?.let { GameTheme.cardColor(it) }
                // A ring in the picked color, thrown out past the wheel's edge.
                if (choice != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = burst.value
                        drawCircle(
                            choice.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                            radius = size.minDimension / 2 * (WHEEL_HOLE + (WHEEL_RING_REACH - WHEEL_HOLE) * p),
                            style = Stroke(10.dp.toPx() * (1f - p) + 1.dp.toPx()),
                        )
                    }
                }
                // The middle takes on the color too, and says which one you went with.
                Box(
                    Modifier
                        .size(wheel * WHEEL_HOLE * 0.9f)
                        .clip(CircleShape)
                        .drawBehind {
                            drawRect(hubColor)
                            // Quicker than the rest of the burst, since its text has already gone dark for it.
                            if (choice != null) drawRect(choice, alpha = (burst.value * 3f).coerceIn(0f, 1f))
                        }
                        .border(1.dp, GameTheme.glassEdge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        picked?.let { colorName(it) } ?: "Pick a\ncolor",
                        color = if (choice != null) GameTheme.contentOn(choice) else GameTheme.text,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun colorName(color: CardColor) = color.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * One quarter of the wheel. Each slice fills the whole wheel and is clipped to its own wedge, which
 * limits its taps to that wedge too, so they stack without getting in each other's way. Pressed it
 * lights up and pushes out; once [picked], the chosen slice flashes and bounces while the rest fall back.
 */
@Composable
private fun WheelSlice(color: CardColor, index: Int, count: Int, wheel: Dp, picked: CardColor?, burst: () -> Float, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pop = animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh), label = "slice")
    val fill = GameTheme.cardColor(color)
    val content = GameTheme.contentOn(fill)
    val name = colorName(color)
    val chosen = picked == color
    val passedOver = picked != null && !chosen
    val start = -90f + index * 90f + WHEEL_GAP / 2
    val sweep = 90f - WHEEL_GAP
    val wedge = remember(index) { wedgeShape(start, sweep) }
    val middle = (start + sweep / 2) * PI / 180
    val labelAt = wheel / 2 * ((1f + WHEEL_HOLE) / 2)
    Box(
        Modifier
            .fillMaxSize()
            // Slices are square boxes, so they push out from the wheel's center when pressed.
            .graphicsLayer {
                val b = burst().coerceIn(0f, 1f)
                val grow = 1f + 0.05f * pop.value.coerceIn(0f, 1f) + when {
                    chosen -> 0.1f * sin(b * PI).toFloat()
                    passedOver -> -0.06f * b
                    else -> 0f
                }
                scaleX = grow
                scaleY = grow
                alpha = if (passedOver) 1f - 0.7f * b else 1f
            }
            .clip(wedge)
            .drawWithCache {
                val radius = size.minDimension / 2
                val face = Brush.radialGradient(
                    WHEEL_HOLE to lerp(fill, GameTheme.shadow, 0.2f),
                    0.72f to fill,
                    1f to lerp(fill, GameTheme.text, 0.16f),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = radius,
                )
                val rimWidth = 2.dp.toPx()
                val rim = lerp(fill, GameTheme.text, 0.55f)
                onDrawBehind {
                    drawRect(face)
                    drawArc(
                        rim, start, sweep, false,
                        Offset(rimWidth / 2, rimWidth / 2),
                        Size(size.width - rimWidth, size.height - rimWidth),
                        style = Stroke(rimWidth),
                    )
                    val light = maxOf(0.22f * pop.value.coerceIn(0f, 1f), if (chosen) 0.6f * (1f - burst().coerceIn(0f, 1f)) else 0f)
                    if (light > 0f) drawRect(GameTheme.text, alpha = light)
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = picked == null, onClickLabel = "Pick $name", role = Role.Button, onClick = onTap),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .offset(x = labelAt * cos(middle).toFloat(), y = labelAt * sin(middle).toFloat()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (LocalColorShapes.current) {
                Canvas(Modifier.size(20.dp)) {
                    colorShape(color, center, size.minDimension * 0.42f, GameTheme.text, GameTheme.scrim, 1.5.dp.toPx())
                }
            }
            Text(name, color = content, style = MaterialTheme.typography.titleLarge)
            Text(if (count == 1) "1 card" else "$count cards", color = content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A curved wedge of a ring, [sweep] degrees round from [start], with [WHEEL_HOLE] of the radius left open. */
private fun wedgeShape(start: Float, sweep: Float): Shape = GenericShape { size, _ ->
    val middle = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2
    arcTo(Rect(middle, radius), start, sweep, forceMoveTo = true)
    arcTo(Rect(middle, radius * WHEEL_HOLE), start + sweep, -sweep, forceMoveTo = false)
    close()
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
                    val empty = GameTheme.panel
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        val corner = CornerRadius(size.height / 2)
                        drawRoundRect(empty, cornerRadius = corner)
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
