package dev.colorclash.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.data.FeedbackSettings
import dev.colorclash.app.data.PlaySettings
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.game.fx.Anchor
import dev.colorclash.app.ui.game.fx.FxLayer
import dev.colorclash.app.ui.game.fx.TableFx
import dev.colorclash.app.ui.settings.QuickSettingsDialog
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerIntent
import dev.colorclash.protocol.PlayerView
import dev.colorclash.protocol.SeatView
import dev.colorclash.rules.Card
import dev.colorclash.rules.Direction
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil

/** The game table. Every move goes to the host as an intent; this only ever shows what the host sent back. */
@Composable
fun GameScreen(
    view: PlayerView,
    fx: TableFx,
    log: List<String>,
    history: List<RoundResult>,
    notice: String?,
    isHost: Boolean,
    feedback: FeedbackSettings,
    onFeedbackChange: (FeedbackSettings) -> Unit,
    play: PlaySettings,
    onPlayChange: (PlaySettings) -> Unit,
    recentPlays: List<RecentPlay>,
    onIntent: (PlayerIntent) -> Unit,
    onReact: (Int) -> Unit,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    onResume: () -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
    onTick: () -> Unit,
) {
    CompositionLocalProvider(LocalColorShapes provides play.colorShapes) {
        GameTable(
            view, fx, log, history, notice, isHost, feedback, onFeedbackChange, play, onPlayChange, recentPlays,
            onIntent, onReact, onNextRound, onRematch, onResume, onBackToLobby, onLeave, onTick,
        )
    }
}

@Composable
private fun GameTable(
    view: PlayerView,
    fx: TableFx,
    log: List<String>,
    history: List<RoundResult>,
    notice: String?,
    isHost: Boolean,
    feedback: FeedbackSettings,
    onFeedbackChange: (FeedbackSettings) -> Unit,
    play: PlaySettings,
    onPlayChange: (PlaySettings) -> Unit,
    recentPlays: List<RecentPlay>,
    onIntent: (PlayerIntent) -> Unit,
    onReact: (Int) -> Unit,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    onResume: () -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
    onTick: () -> Unit,
) {
    val controls = tableControls(view)
    var showPeek by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    val roundOver = view.phase == PhaseKind.ROUND_OVER || view.phase == PhaseKind.GAME_OVER
    // Closing the scoreboard lasts until the next round ends; then it opens again by itself.
    var scoresClosed by remember(view.round, view.phase) { mutableStateOf(false) }
    BackHandler(enabled = roundOver && !scoresClosed) { scoresClosed = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().graphicsLayer { translationX = fx.shake.value }) {
            TableTopBar(view, onLeave = onLeave, onSettings = { showSettings = true })

            OpponentsArc(
                view = view,
                fx = fx,
                next = nextSeat(view),
                catchTarget = controls.catchTarget,
                onCatch = { onIntent(PlayerIntent.Catch(it)) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )

            CenterTable(
                view = view,
                fx = fx,
                controls = controls,
                onDraw = { onIntent(PlayerIntent.Draw) },
                onPeek = { showPeek = true },
                onTick = onTick,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            // Kept clear of the UNO buzzer, which sits over its right end.
            Column(
                Modifier.fillMaxWidth().height(54.dp).padding(start = 16.dp, end = BUZZER_SIZE + 16.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                log.forEachIndexed { i, line ->
                    Text(
                        line,
                        color = GameTheme.text.copy(alpha = 0.45f + 0.55f * (i + 1) / log.size),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(Modifier.fillMaxWidth()) {
                val sides = Modifier.padding(horizontal = REACTION_BUTTON_SIZE)
                if (roundOver && scoresClosed) {
                    RoundOverBar(
                        gameOver = view.phase == PhaseKind.GAME_OVER,
                        isHost = isHost,
                        onScores = { scoresClosed = false },
                        onNextRound = onNextRound,
                        onRematch = onRematch,
                        modifier = sides,
                    )
                } else {
                    ActionBar(view, controls, onIntent, sides)
                }
                SortSwitch(
                    byNumber = play.sortByNumber,
                    onToggle = { onPlayChange(play.copy(sortByNumber = !play.sortByNumber)) },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                )
                ReactionButton(
                    open = showReactions,
                    onClick = { showReactions = !showReactions },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                )
            }

            Hand(view, fx, play, onPlay = { onIntent(PlayerIntent.Play(it)) })
        }

        // Always shown and always the same, so it never hints when to press it. Out of turn it does nothing.
        if (!roundOver) {
            UnoBuzzer(
                onPress = { if (controls.canCallLastCard) onIntent(PlayerIntent.CallLastCard) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = HAND_HEIGHT + ACTION_BAR_HEIGHT - 10.dp),
            )
        }

        FxLayer(fx)

        // Timed out too often: a bot has your seat until you say you're back.
        val you = view.seats[view.you].info
        if (you.botControlled && you.connected && view.phase.isLive()) {
            AwayBanner(onResume, Modifier.align(Alignment.Center))
        }

        if (showReactions) {
            ReactionTray(
                onPick = {
                    onReact(it)
                    showReactions = false
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = HAND_HEIGHT + ACTION_BAR_HEIGHT),
            )
        }

        if (showPeek) RecentPlays(view, recentPlays, onDismiss = { showPeek = false })
        if (controls.pickingColor) ColorPicker(view.hand, onPick = { onIntent(PlayerIntent.ChooseColor(it)) })
        if (controls.pickingSwapTarget) SwapPicker(view, onPick = { onIntent(PlayerIntent.SwapWith(it)) })
        if (roundOver && !scoresClosed) {
            ScoreBoard(view, controls.headline, history, isHost, onNextRound, onRematch, onBackToLobby, onLeave, onClose = { scoresClosed = true })
        }

        notice?.let {
            Text(
                it,
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White,
            )
        }
    }

    if (showSettings) QuickSettingsDialog(feedback, onFeedbackChange, play, onPlayChange, onDismiss = { showSettings = false })
}

@Composable
private fun OpponentsArc(view: PlayerView, fx: TableFx, next: Int?, catchTarget: Int?, onCatch: (Int) -> Unit, modifier: Modifier) {
    val opponents = opponentOrder(view)
    val positions = arcPositions(opponents.size)
    BoxWithConstraints(modifier) {
        // Narrow enough that five opponents fit across a small phone.
        val seatWidth = 76.dp
        val seatHeight = 136.dp
        opponents.forEachIndexed { i, seat ->
            val (fx0, fy) = positions[i]
            val x = maxWidth * fx0 - seatWidth / 2
            val y = (maxHeight - seatHeight) * fy.coerceIn(0f, 1f)
            OpponentSeat(
                seat = view.seats[seat],
                isCurrent = seat == view.current && view.phase.isLive(),
                isNext = seat == next && seat != view.current,
                turnMillisLeft = view.turnMillisLeft.takeIf { seat == view.current },
                turnSeconds = view.settings.turnTimerSeconds,
                canCatch = catchTarget == seat,
                onCatch = { onCatch(seat) },
                modifier = Modifier
                    .offset(x, y)
                    .width(seatWidth)
                    .then(fx.anchor(Anchor.Seat(seat)))
                    .graphicsLayer {
                        if (fx.joltedSeat == seat) translationX = fx.jolt.value
                        if (fx.cheeredSeat == seat) {
                            translationY = -14.dp.toPx() * fx.cheer.value
                            scaleX = 1f + 0.12f * fx.cheer.value
                            scaleY = 1f + 0.12f * fx.cheer.value
                        }
                    },
            )
        }
    }
}

@Composable
private fun OpponentSeat(
    seat: SeatView,
    isCurrent: Boolean,
    isNext: Boolean,
    turnMillisLeft: Long?,
    turnSeconds: Int?,
    canCatch: Boolean,
    onCatch: () -> Unit,
    modifier: Modifier,
) {
    // Tap a seat for the exact count; big hands bunch up in the fan.
    var showCount by remember { mutableStateOf(false) }
    LaunchedEffect(showCount) {
        if (showCount) {
            delay(2_000)
            showCount = false
        }
    }
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showCount = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCurrent) TurnRing(turnMillisLeft, turnSeconds, size = 58.dp)
            AvatarBadge(
                seat.info,
                size = 46.dp,
                ring = if (isCurrent) GameTheme.yellow else null,
                modifier = if (isCurrent) Modifier.scale(pulse()) else Modifier,
            )
            if (showCount) {
                Text(
                    "${seat.cardCount}",
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(GameTheme.yellow)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp,
                )
            }
            if (isNext) {
                Text(
                    "NEXT",
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-12).dp, y = (-4).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GameTheme.blue)
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp,
                )
            }
        }
        HandFan(seat.cardCount, Modifier.offset(y = (-8).dp))
        Text(
            seat.info.name + when {
                seat.info.botControlled -> " 🤖"
                !seat.info.connected -> " 📶"
                else -> ""
            },
            // Dark pill so it reads over anything; yellow on their turn.
            Modifier
                .offset(y = (-6).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GameTheme.wild.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = if (isCurrent) GameTheme.yellow else GameTheme.text,
            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
        )
        when {
            canCatch -> Text(
                "CATCH!",
                Modifier
                    .scale(pulse(1.15f))
                    .clip(RoundedCornerShape(10.dp))
                    .background(GameTheme.red)
                    .clickable(onClick = onCatch)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp,
            )
            seat.cardCount == 1 && seat.calledLastCard -> Text(
                "UNO",
                Modifier.clip(RoundedCornerShape(8.dp)).background(GameTheme.yellow).padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CenterTable(
    view: PlayerView,
    fx: TableFx,
    controls: TableControls,
    onDraw: () -> Unit,
    onPeek: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.then(fx.anchor(Anchor.DrawPile)).scale(if (controls.canDraw) pulse(1.05f) else 1f)) {
                // A couple of cards peeking out underneath, so it reads as a deck.
                if (view.drawPileCount > 2) CardBack(TABLE_CARD_WIDTH, Modifier.offset(3.dp, 5.dp), elevation = 1.dp)
                if (view.drawPileCount > 1) CardBack(TABLE_CARD_WIDTH, Modifier.offset(1.5.dp, 2.5.dp), elevation = 1.dp)
                Box(
                    Modifier
                        .clip(PILE_SHAPE)
                        .then(if (controls.canDraw) Modifier.border(3.dp, GameTheme.yellow, PILE_SHAPE) else Modifier)
                        .clickable(enabled = controls.canDraw, onClick = onDraw),
                ) {
                    CardBack(TABLE_CARD_WIDTH, elevation = 3.dp)
                    Text(
                        view.drawPileCount.toString(),
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                        color = GameTheme.mutedText, fontSize = 12.sp,
                    )
                }
            }

            Text(
                if (view.direction == Direction.CLOCKWISE) "↻" else "↺",
                Modifier.graphicsLayer { rotationZ = fx.arrowSpin.value },
                fontSize = 40.sp,
                color = GameTheme.text,
                fontWeight = FontWeight.Black,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = fx.anchor(Anchor.DiscardPile).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPeek),
            ) {
                // Halo in the active color, which matters most after a wild.
                Box(
                    Modifier
                        .size(TABLE_CARD_WIDTH + 22.dp, TABLE_CARD_WIDTH * CARD_ASPECT + 22.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GameTheme.cardColor(view.activeColor).copy(alpha = if (view.activeColor == null) 0f else 0.55f)),
                )
                fx.pileUnder.forEach { card ->
                    key(card.id) { CardFace(card, TABLE_CARD_WIDTH, Modifier.graphicsLayer { rotationZ = TableFx.discardTilt(card) }, elevation = 1.dp) }
                }
                if (!fx.pileCleared) {
                    val top = fx.pileTop ?: view.topCard
                    key(top.id) {
                        CardFace(
                            top,
                            TABLE_CARD_WIDTH,
                            Modifier.graphicsLayer {
                                rotationZ = TableFx.discardTilt(top)
                                val bump = 1f + 0.06f * fx.pileBump.value
                                scaleX = bump
                                scaleY = bump
                            },
                            elevation = 3.dp,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.isYourTurn && view.phase.isLive()) {
                TurnRing(view.turnMillisLeft, view.settings.turnTimerSeconds, size = 36.dp, showSeconds = true, onFinalSeconds = onTick)
            }
            Text(
                controls.headline,
                Modifier.scale(if (view.isYourTurn && view.phase.isLive()) pulse(1.04f) else 1f),
                style = MaterialTheme.typography.titleLarge,
                color = if (view.isYourTurn) GameTheme.yellow else GameTheme.text,
                textAlign = TextAlign.Center,
            )
        }
        if (view.pendingDraw > 0) {
            Text("+${view.pendingDraw} waiting", color = GameTheme.red, fontWeight = FontWeight.Black)
        }
        if (nextSeat(view) == view.you && !view.isYourTurn) {
            Text("You're up next", color = GameTheme.mutedText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionBar(view: PlayerView, controls: TableControls, onIntent: (PlayerIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controls.canChallenge) ActionButton("Challenge!", GameTheme.red) { onIntent(PlayerIntent.Challenge) }
        if (controls.canDraw && view.pendingDraw > 0) ActionButton(controls.drawLabel, GameTheme.blue) { onIntent(PlayerIntent.Draw) }
        if (controls.canKeep) ActionButton("Keep it", GameTheme.blue) { onIntent(PlayerIntent.Pass) }
        controls.catchTarget?.let { target ->
            ActionButton("Catch ${view.nameOf(target)}!", GameTheme.red) { onIntent(PlayerIntent.Catch(target)) }
        }
    }
}

@Composable
private fun AwayBanner(onResume: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GameTheme.wild.copy(alpha = 0.94f))
            .border(2.dp, GameTheme.yellow, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🤖", fontSize = 40.sp)
        Text("You ran out of time a few turns in a row, so a bot is playing for you.", textAlign = TextAlign.Center)
        ActionButton("I'm back!", GameTheme.green, onClick = onResume)
    }
}

/** With the scoreboard closed: a way back to it, and for the host, a way on. */
@Composable
private fun RoundOverBar(
    gameOver: Boolean,
    isHost: Boolean,
    onScores: () -> Unit,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton("Scores", GameTheme.blue, onClick = onScores)
        if (isHost) {
            if (gameOver) {
                ActionButton("Play again", GameTheme.green, onClick = onRematch)
            } else {
                ActionButton("Next round", GameTheme.green, onClick = onNextRound)
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
    ) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

/**
 * Your hand as an overlapping fan. Each item only takes a slice of width and lets its card
 * spill over the next, so the lazy row can animate cards in, out and around as the hand changes.
 */
@Composable
private fun Hand(view: PlayerView, fx: TableFx, play: PlaySettings, onPlay: (Int) -> Unit) {
    val cards = sortHand(view.hand, play.sortByNumber).filter { it.id !in fx.hiddenCards }
    var selected by remember { mutableStateOf<Int?>(null) }
    if (selected != null && selected !in view.playable) selected = null
    val mid = (cards.size - 1) / 2f
    val tilt = if (cards.size <= 1) 0f else (30f / cards.size).coerceAtMost(5f)
    val slice = if (cards.size > 6) HAND_CARD_WIDTH - 36.dp else HAND_CARD_WIDTH - 22.dp
    LazyRow(
        Modifier
            .fillMaxWidth()
            .height(HAND_HEIGHT)
            .then(fx.anchor(Anchor.Seat(view.you)))
            .graphicsLayer {
                if (fx.joltedSeat == view.you) translationX = fx.jolt.value
                if (fx.cheeredSeat == view.you) translationY = -14.dp.toPx() * fx.cheer.value
            },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp + HAND_CARD_WIDTH - slice, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(cards, key = { _, card -> card.id }) { i, card ->
            val rotation = (i - mid) * tilt
            // The thrown card is already flying from its slot, so it mustn't fade out there too.
            Box(Modifier.animateItem(fadeOutSpec = null, placementSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)).width(slice)) {
                DisposableEffect(card.id) { onDispose { fx.forget(Anchor.HandCard(card.id)) } }
                HandCard(
                    card = card,
                    playable = card.id in view.playable,
                    rotation = rotation,
                    drop = (abs(i - mid) * abs(i - mid) * 1.2f).dp.coerceAtMost(18.dp),
                    incoming = { card.id in fx.incomingCards },
                    selected = selected == card.id,
                    onTap = {
                        if (!play.tapToSelect || selected == card.id) {
                            selected = null
                            onPlay(card.id)
                        } else {
                            selected = card.id
                        }
                    },
                    onPlay = {
                        selected = null
                        onPlay(card.id)
                    },
                    anchor = fx.anchor(Anchor.HandCard(card.id), rotation, pivotY = HAND_PIVOT),
                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                )
            }
        }
    }
}

@Composable
private fun HandCard(
    card: Card,
    playable: Boolean,
    rotation: Float,
    drop: Dp,
    incoming: () -> Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    anchor: Modifier,
    modifier: Modifier,
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val playThreshold = with(LocalDensity.current) { 70.dp.toPx() }
    val lift by animateDpAsState(
        when {
            selected -> (-34).dp
            playable -> (-14).dp
            else -> 0.dp
        },
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "lift",
    )
    val sink by animateDpAsState(drop, spring(stiffness = Spring.StiffnessMediumLow), label = "drop")
    val tilt by animateFloatAsState(rotation, spring(stiffness = Spring.StiffnessMediumLow), label = "tilt")
    val pull by animateFloatAsState(
        dragY,
        if (dragging) snap<Float>() else spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pull",
    )
    Box(
        modifier
            .offset(y = sink + lift)
            .then(anchor)
            .graphicsLayer {
                rotationZ = tilt
                translationY = pull
                transformOrigin = TransformOrigin(0.5f, HAND_PIVOT)
                val reach = 1f + 0.08f * (-pull / playThreshold).coerceIn(0f, 1f)
                scaleX = reach
                scaleY = reach
                alpha = if (incoming()) 0f else 1f
            }
            .then(
                if (playable) {
                    Modifier
                        .clickable(onClick = onTap)
                        .pointerInput(card.id) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    if (dragY < -playThreshold) onPlay()
                                    dragging = false
                                    dragY = 0f
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragY = 0f
                                },
                            ) { _, dy -> dragY = (dragY + dy).coerceAtMost(0f) }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        CardFace(
            card,
            HAND_CARD_WIDTH,
            Modifier.then(if (selected) Modifier.border(3.dp, GameTheme.yellow, RoundedCornerShape(HAND_CARD_WIDTH * 0.11f)) else Modifier),
            dimmed = !playable,
            elevation = if (selected) 10.dp else 3.dp,
        )
    }
}

/** Turn timer ring, counted down locally from what the host said was left. */
@Composable
fun TurnRing(millisLeft: Long?, totalSeconds: Int?, size: Dp, showSeconds: Boolean = false, onFinalSeconds: (() -> Unit)? = null) {
    if (millisLeft == null || totalSeconds == null) return
    val total = totalSeconds * 1000f
    val progress = remember { Animatable(1f) }
    LaunchedEffect(millisLeft) {
        progress.snapTo((millisLeft / total).coerceIn(0f, 1f))
        progress.animateTo(0f, tween(millisLeft.toInt().coerceAtLeast(0), easing = LinearEasing))
    }
    // Recompose once a second for the number, not every frame.
    val seconds by remember(totalSeconds) { derivedStateOf { ceil(progress.value * totalSeconds).toInt() } }
    val urgent by remember { derivedStateOf { progress.value < 0.25f } }
    LaunchedEffect(seconds) { if (seconds in 1..FINAL_SECONDS && progress.isRunning) onFinalSeconds?.invoke() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawArc(
                if (progress.value < 0.25f) GameTheme.red else GameTheme.yellow,
                startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (showSeconds) {
            Text(
                seconds.toString(),
                Modifier.scale(if (urgent) pulse(1.15f) else 1f),
                color = if (urgent) GameTheme.red else GameTheme.text,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/** The UNO call as an arcade buzzer that sinks into its base when pressed. */
@Composable
private fun UnoBuzzer(onPress: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh), label = "sink")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    Box(
        modifier
            .size(BUZZER_SIZE * 1.3f)
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val d = BUZZER_SIZE.toPx()
            val rim = d * 0.5f
            val dome = d * 0.4f
            val lift = d * 0.07f
            val top = center + Offset(0f, -lift + d * 0.05f * sink)
            drawOval(Color.Black.copy(alpha = 0.35f), center - Offset(rim, rim * 0.97f - d * 0.06f), Size(rim * 2, rim * 1.94f))
            drawCircle(Brush.radialGradient(listOf(GameTheme.buzzerRim, GameTheme.buzzerRimDeep), center - Offset(0f, rim * 0.3f), rim * 1.4f), rim)
            drawCircle(Color.White.copy(alpha = 0.18f), rim * 0.98f, style = Stroke(d * 0.015f))
            // The side of the dome shows while it's up; pressing sinks the cap down over it.
            drawCircle(GameTheme.buzzerSide, dome, center + Offset(0f, lift * 0.35f))
            drawCircle(
                Brush.radialGradient(
                    0f to (if (pressed) GameTheme.red else GameTheme.buzzerShine),
                    0.6f to (if (pressed) GameTheme.buzzerPressed else GameTheme.red),
                    1f to GameTheme.buzzerDeep,
                    center = top - Offset(dome * 0.3f, dome * 0.4f),
                    radius = dome * 1.6f,
                ),
                dome,
                top,
            )
            rotate(-20f, pivot = top - Offset(dome * 0.25f, dome * 0.45f)) {
                drawOval(
                    Color.White.copy(alpha = if (pressed) 0.18f else 0.35f),
                    top - Offset(dome * 0.7f, dome * 0.65f),
                    Size(dome * 0.9f, dome * 0.4f),
                )
            }
        }
        Text(
            "UNO!",
            Modifier.graphicsLayer { translationY = BUZZER_SIZE.toPx() * (-0.07f + 0.05f * sink) },
            color = Color.White,
            fontSize = (BUZZER_SIZE.value * 0.22f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = GameTheme.displayFont,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(2f, 3f), blurRadius = 3f)),
        )
    }
}

@Composable
private fun SortSwitch(byNumber: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(REACTION_BUTTON_SIZE)
            .clip(CircleShape)
            .background(GameTheme.panelStrong)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (byNumber) "1-9" else "🎨", fontSize = if (byNumber) 14.sp else 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ReactionButton(open: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(REACTION_BUTTON_SIZE)
            .clip(CircleShape)
            .background(if (open) GameTheme.yellow else GameTheme.panelStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("😊", fontSize = 24.sp)
    }
}

/** Emoji and quick chat you can send. */
@Composable
private fun ReactionTray(onPick: (Int) -> Unit, modifier: Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    Column(
        modifier
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
                alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(1f, 1f)
            }
            .clip(RoundedCornerShape(20.dp))
            .background(GameTheme.wild.copy(alpha = 0.92f))
            .border(1.5.dp, GameTheme.panelStrong, RoundedCornerShape(20.dp))
            .padding(8.dp),
    ) {
        GameTheme.reactions.indices.chunked(REACTIONS_PER_ROW).forEach { row ->
            Row {
                row.forEach { i ->
                    Text(
                        GameTheme.reaction(i),
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(i) }
                            .padding(6.dp),
                        fontSize = 28.sp,
                    )
                }
            }
        }
        val firstPhrase = GameTheme.reactions.size
        GameTheme.phrases.indices.chunked(PHRASES_PER_ROW).forEach { row ->
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { p ->
                    Text(
                        GameTheme.reaction(firstPhrase + p),
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GameTheme.panel)
                            .clickable { onPick(firstPhrase + p) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun pulse(to: Float = 1.08f): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "pulseScale",
    )
    return scale
}

private fun PhaseKind.isLive() = this != PhaseKind.ROUND_OVER && this != PhaseKind.GAME_OVER

/** Hand cards lean around a point this far down their height, below the card itself. */
private const val HAND_PIVOT = 1.2f
private const val REACTIONS_PER_ROW = 6
private val BUZZER_SIZE = 88.dp
private const val PHRASES_PER_ROW = 3
private const val FINAL_SECONDS = 5
private val REACTION_BUTTON_SIZE = 44.dp
private val ACTION_BAR_HEIGHT = 60.dp
private val HAND_HEIGHT = HAND_CARD_WIDTH * CARD_ASPECT + 48.dp
private val PILE_SHAPE = RoundedCornerShape(TABLE_CARD_WIDTH * 0.11f)
