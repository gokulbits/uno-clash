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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonLabel
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.RoundButton
import dev.colorclash.app.ui.components.RoundIconButton
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.SheetSurface
import dev.colorclash.app.ui.components.SmallButton
import dev.colorclash.app.ui.components.Tag
import dev.colorclash.app.ui.components.Toast
import dev.colorclash.app.ui.components.colorDotsIcon
import dev.colorclash.app.ui.components.directionIcon
import dev.colorclash.app.ui.components.offlineIcon
import dev.colorclash.app.ui.components.robotIcon
import dev.colorclash.app.ui.components.smileIcon
import dev.colorclash.app.ui.game.fx.Anchor
import dev.colorclash.app.ui.game.fx.FxLayer
import dev.colorclash.app.ui.game.fx.TableFx
import dev.colorclash.app.ui.settings.QuickSettingsDialog
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.REACTION_GAP_MILLIS
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
    // One heartbeat for the whole table. Each seat used to run its own two, a dozen animations ticking
    // every frame, nearly all of them for nothing.
    val pulse = rememberPulse()
    CompositionLocalProvider(LocalColorShapes provides play.colorShapes, LocalPulse provides pulse) {
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
    // The host rations reactions; grey the button out on the same numbers so a drop is never a surprise.
    var reactionsSent by remember { mutableIntStateOf(0) }
    var canReact by remember { mutableStateOf(true) }
    LaunchedEffect(reactionsSent) {
        if (reactionsSent == 0) return@LaunchedEffect
        canReact = false
        delay(REACTION_GAP_MILLIS)
        canReact = true
    }
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
                        style = MaterialTheme.typography.bodySmall,
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
                    enabled = canReact,
                    onClick = { if (canReact) showReactions = !showReactions else showReactions = false },
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

        if (showReactions && canReact) {
            ReactionTray(
                onPick = {
                    onReact(it)
                    reactionsSent++
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

        notice?.let { Toast(it, Modifier.align(Alignment.Center)) }
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
    val pulse = LocalPulse.current
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
                ring = if (isCurrent) GameTheme.highlight else null,
                modifier = if (isCurrent) Modifier.pulsing(pulse) else Modifier,
            )
            if (showCount) {
                Tag(
                    "${seat.cardCount}",
                    GameTheme.highlight,
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (isNext) {
                Tag("NEXT", GameTheme.blue, Modifier.align(Alignment.TopStart).offset(x = (-12).dp, y = (-4).dp))
            }
            // Beside the avatar rather than after the name, where a long name would ellipsize it away.
            when {
                seat.info.botControlled -> SeatMark(Modifier.align(Alignment.BottomEnd)) { robotIcon(it) }
                !seat.info.connected -> SeatMark(Modifier.align(Alignment.BottomEnd)) { offlineIcon(it) }
            }
        }
        HandFan(seat.cardCount, Modifier.offset(y = (-8).dp))
        // Dark so it reads over anything; yellow on their turn.
        Tag(
            seat.info.name,
            GameTheme.scrim,
            Modifier.offset(y = (-6).dp),
            color = if (isCurrent) GameTheme.highlight else GameTheme.text,
            style = MaterialTheme.typography.labelMedium,
        )
        when {
            canCatch -> Tag(
                "CATCH!",
                GameTheme.danger,
                Modifier
                    .pulsing(pulse, 1.15f)
                    .clip(GameTheme.tagShape)
                    .clickable(role = Role.Button, onClick = onCatch),
                style = MaterialTheme.typography.labelMedium,
            )
            seat.cardCount == 1 && seat.calledLastCard -> Tag("UNO", GameTheme.highlight)
        }
    }
}

/** A small badge on a seat's avatar saying something's up with that player. */
@Composable
private fun SeatMark(modifier: Modifier, icon: DrawScope.(Color) -> Unit) {
    Box(
        modifier
            .offset(x = 6.dp, y = 2.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(GameTheme.ink)
            .border(1.dp, GameTheme.glassEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(13.dp)) { icon(GameTheme.text) }
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
    val pulse = LocalPulse.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier
                    .then(fx.anchor(Anchor.DrawPile))
                    .then(if (controls.canDraw) Modifier.pulsing(pulse, 1.05f) else Modifier),
            ) {
                // A couple of cards peeking out underneath, so it reads as a deck.
                if (view.drawPileCount > 2) CardBack(TABLE_CARD_WIDTH, Modifier.offset(3.dp, 5.dp), elevation = 1.dp)
                if (view.drawPileCount > 1) CardBack(TABLE_CARD_WIDTH, Modifier.offset(1.5.dp, 2.5.dp), elevation = 1.dp)
                Box(
                    Modifier
                        .clip(PILE_SHAPE)
                        .then(if (controls.canDraw) Modifier.border(3.dp, GameTheme.highlight, PILE_SHAPE) else Modifier)
                        .clickable(enabled = controls.canDraw, onClick = onDraw),
                ) {
                    CardBack(TABLE_CARD_WIDTH, elevation = 3.dp)
                    Tag(view.drawPileCount.toString(), GameTheme.scrim, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                }
            }

            val clockwise = view.direction == Direction.CLOCKWISE
            Canvas(Modifier.size(40.dp).graphicsLayer { rotationZ = fx.arrowSpin.value }) { directionIcon(GameTheme.text, clockwise) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = fx.anchor(Anchor.DiscardPile).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPeek),
            ) {
                // Halo in the active color, which matters most after a wild.
                Box(
                    Modifier
                        .size(TABLE_CARD_WIDTH + 22.dp, TABLE_CARD_WIDTH * CARD_ASPECT + 22.dp)
                        .clip(GameTheme.panelShape)
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
                if (view.isYourTurn && view.phase.isLive()) Modifier.pulsing(pulse, 1.04f) else Modifier,
                style = MaterialTheme.typography.titleLarge,
                color = if (view.isYourTurn) GameTheme.highlight else GameTheme.text,
                textAlign = TextAlign.Center,
            )
        }
        if (view.pendingDraw > 0) {
            Tag("+${view.pendingDraw} waiting", GameTheme.danger, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (nextSeat(view) == view.you && !view.isYourTurn) {
            HintText("You're up next")
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
        if (controls.canChallenge) SmallButton("Challenge!", ButtonTone.DANGER, { onIntent(PlayerIntent.Challenge) })
        if (controls.canDraw && view.pendingDraw > 0) SmallButton(controls.drawLabel, ButtonTone.ACTION, { onIntent(PlayerIntent.Draw) })
        if (controls.canKeep) SmallButton("Keep it", ButtonTone.ACTION, { onIntent(PlayerIntent.Pass) })
        controls.catchTarget?.let { target ->
            SmallButton("Catch ${view.nameOf(target)}!", ButtonTone.DANGER, { onIntent(PlayerIntent.Catch(target)) })
        }
    }
}

@Composable
private fun AwayBanner(onResume: () -> Unit, modifier: Modifier) {
    SheetSurface(modifier) {
        Canvas(Modifier.size(44.dp)) { robotIcon(GameTheme.text) }
        Text("You ran out of time a few turns in a row, so a bot is playing for you.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        BigButton("I'm back!", ButtonTone.PRIMARY, onResume)
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
        SmallButton("Scores", ButtonTone.QUIET, onScores)
        if (isHost) {
            if (gameOver) {
                SmallButton("Play again", ButtonTone.PRIMARY, onRematch)
            } else {
                SmallButton("Next round", ButtonTone.PRIMARY, onNextRound)
            }
        }
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
            // The lambda form reads these springs in layout, so the card doesn't recompose on every frame.
            .offset { IntOffset(0, (sink + lift).roundToPx()) }
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
            Modifier.then(if (selected) Modifier.border(3.dp, GameTheme.highlight, RoundedCornerShape(HAND_CARD_WIDTH * 0.11f)) else Modifier),
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
    val pulse = LocalPulse.current
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
                if (progress.value < 0.25f) GameTheme.danger else GameTheme.highlight,
                startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (showSeconds) {
            Text(
                seconds.toString(),
                if (urgent) Modifier.pulsing(pulse, 1.15f) else Modifier,
                color = if (urgent) GameTheme.danger else GameTheme.text,
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
            drawOval(GameTheme.shadow.copy(alpha = 0.35f), center - Offset(rim, rim * 0.97f - d * 0.06f), Size(rim * 2, rim * 1.94f))
            drawCircle(Brush.radialGradient(listOf(GameTheme.buzzerRim, GameTheme.buzzerRimDeep), center - Offset(0f, rim * 0.3f), rim * 1.4f), rim)
            drawCircle(GameTheme.text.copy(alpha = 0.18f), rim * 0.98f, style = Stroke(d * 0.015f))
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
                    GameTheme.text.copy(alpha = if (pressed) 0.18f else 0.35f),
                    top - Offset(dome * 0.7f, dome * 0.65f),
                    Size(dome * 0.9f, dome * 0.4f),
                )
            }
        }
        Text(
            "UNO!",
            Modifier.graphicsLayer { translationY = BUZZER_SIZE.toPx() * (-0.07f + 0.05f * sink) },
            color = GameTheme.text,
            fontSize = (BUZZER_SIZE.value * 0.22f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = GameTheme.displayFont,
            style = TextStyle(shadow = Shadow(GameTheme.shadow.copy(alpha = 0.4f), Offset(2f, 3f), blurRadius = 3f)),
        )
    }
}

@Composable
private fun SortSwitch(byNumber: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    RoundButton(if (byNumber) "Sort by color" else "Sort by number", onToggle, modifier) { tint ->
        if (byNumber) {
            ButtonLabel("1-9", style = MaterialTheme.typography.labelMedium, fill = tint)
        } else {
            Canvas(Modifier.size(20.dp)) { colorDotsIcon() }
        }
    }
}

@Composable
private fun ReactionButton(open: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    RoundIconButton("Reactions", onClick, modifier, enabled = enabled, selected = open) { smileIcon(it) }
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
            .clip(GameTheme.panelShape)
            .background(LocalTableStyle.current.top)
            .border(1.dp, GameTheme.glassEdge, GameTheme.panelShape)
            // There are a lot of phrases now; scroll rather than run off the top of the screen.
            .heightIn(max = REACTION_TRAY_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        GameTheme.reactions.indices.chunked(REACTIONS_PER_ROW).forEach { row ->
            Row {
                row.forEach { i ->
                    Text(
                        GameTheme.reaction(i),
                        Modifier
                            .clip(GameTheme.tagShape)
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
                            .clip(GameTheme.tagShape)
                            .background(GameTheme.panel)
                            .clickable { onPick(firstPhrase + p) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * A heartbeat for whatever wants attention, from 0 to 1 and back. It's the State, not the Float, that
 * gets passed around: read as a Float it landed in the caller's composition scope, so every seat
 * (and the card fan under it) recomposed on every animation frame.
 */
@Composable
private fun rememberPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "pulsePhase",
    )
}

private val LocalPulse = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Scales up to [peak] on [pulse]'s beat, in the draw phase, so nothing recomposes while it beats. */
private fun Modifier.pulsing(pulse: State<Float>, peak: Float = 1.08f): Modifier = graphicsLayer {
    val scale = 1f + (peak - 1f) * pulse.value
    scaleX = scale
    scaleY = scale
}

private fun PhaseKind.isLive() = this != PhaseKind.ROUND_OVER && this != PhaseKind.GAME_OVER

/** Hand cards lean around a point this far down their height, below the card itself. */
private const val HAND_PIVOT = 1.2f
private const val REACTIONS_PER_ROW = 6
private val BUZZER_SIZE = 88.dp
private const val PHRASES_PER_ROW = 4
private const val FINAL_SECONDS = 5
private val REACTION_BUTTON_SIZE = 46.dp
private val REACTION_TRAY_MAX_HEIGHT = 320.dp
private val ACTION_BAR_HEIGHT = 60.dp
private val HAND_HEIGHT = HAND_CARD_WIDTH * CARD_ASPECT + 48.dp
private val PILE_SHAPE = RoundedCornerShape(TABLE_CARD_WIDTH * 0.11f)
