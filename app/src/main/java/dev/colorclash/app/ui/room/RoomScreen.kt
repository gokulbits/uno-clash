package dev.colorclash.app.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colorclash.app.colorClash
import dev.colorclash.app.feedback.Buzz
import dev.colorclash.app.feedback.Sfx
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.BusyRow
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.GameDialog
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.LocalSeatPhotos
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SheetTitle
import dev.colorclash.app.ui.components.Toast
import dev.colorclash.app.ui.components.rememberSeatPhotos
import dev.colorclash.app.ui.game.GameScreen
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.net.client.ClientStatus

/** Everything once you're in a room: connecting, lobby, game, and the ways it can end. */
@Composable
fun RoomScreen(session: Session, onLeave: () -> Unit) {
    val app = LocalContext.current.colorClash
    // Owns the log, history, stats and the FX queue, and outlives this composition.
    val presenter = remember(session) { app.presenterFor(session) }
    val fx = presenter.fx
    val hostControls = remember(session) {
        session.hosting?.let { hosting ->
            LobbyHostControls(
                port = hosting.port,
                start = { hosting.host.startGame() },
                addBot = { hosting.host.addBot() },
                removeBot = { hosting.host.removeBot(it) },
            )
        }
    }

    val status by session.client.status.collectAsStateWithLifecycle()
    val lobby by session.client.lobby.collectAsStateWithLifecycle()
    val game by session.client.game.collectAsStateWithLifecycle()
    val photoData by session.client.photos.collectAsStateWithLifecycle()
    val photos = rememberSeatPhotos(photoData)
    val log by presenter.log.collectAsStateWithLifecycle()
    val history by presenter.history.collectAsStateWithLifecycle()
    val recentPlays by presenter.recentPlays.collectAsStateWithLifecycle()
    val notice by presenter.notice.collectAsStateWithLifecycle()
    val feedbackSettings by app.prefs.feedback.collectAsStateWithLifecycle()
    val playSettings by app.prefs.play.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }

    // Started from composition because Animatable needs a frame clock, which only composition has.
    LaunchedEffect(fx) { fx.run(app.feedback) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { confirmLeave = true }

    CompositionLocalProvider(LocalSeatPhotos provides photos) {
        Box(Modifier.fillMaxSize()) {
            when (val s = status) {
                is ClientStatus.Failed -> EndCard("Couldn't stay connected", s.message, onLeave)
                is ClientStatus.Closed -> EndCard("Room closed", s.reason, onLeave)
                else -> {
                    val current = game
                    // The animated view lags the latest one by an effect or two; fall back to it
                    // until the first lands.
                    val playing = if (current == null) null else fx.displayed ?: current
                    val waiting = lobby
                    when {
                        playing != null -> GameScreen(
                            view = playing,
                            fx = fx,
                            log = log,
                            history = history,
                            notice = notice,
                            isHost = session.isHost,
                            feedback = feedbackSettings,
                            onFeedbackChange = app.prefs::updateFeedback,
                            play = playSettings,
                            onPlayChange = app.prefs::updatePlay,
                            recentPlays = recentPlays,
                            onTick = {
                                app.feedback.sfx(Sfx.TICK, 0.6f)
                                app.feedback.buzz(Buzz.TICK)
                            },
                            onColorTap = {
                                app.feedback.sfx(Sfx.POP)
                                app.feedback.buzz(Buzz.BUMP)
                            },
                            onIntent = session.client::send,
                            onReact = session.client::react,
                            onNextRound = { session.hosting?.host?.startNextRound() },
                            onRematch = { session.hosting?.host?.rematch() },
                            onResume = session.client::resume,
                            onBackToLobby = { session.hosting?.host?.returnToLobby() },
                            onLeave = { confirmLeave = true },
                        )
                        waiting != null -> LobbyScreen(
                            lobby = waiting,
                            hosting = hostControls,
                            startError = startError,
                            onStartError = { startError = it },
                            onReady = { session.client.setReady(it) },
                            onSettingsChange = { settings ->
                                session.hosting?.host?.updateSettings(settings)
                                app.prefs.lobbySettings = settings
                            },
                            onLeave = { confirmLeave = true },
                        )
                        else -> BusyRow("Connecting to the room…", Modifier.align(Alignment.Center))
                    }
                }
            }

            if (status is ClientStatus.Reconnecting) {
                Toast("Connection lost, reconnecting…", Modifier.align(Alignment.TopCenter).padding(top = 60.dp), fill = GameTheme.danger, busy = true)
            }
        }
    }

    if (confirmLeave) {
        GameDialog(
            title = if (session.isHost) "Close the room?" else "Leave the room?",
            onDismiss = { confirmLeave = false },
            actions = {
                BigButton("Stay", ButtonTone.QUIET, { confirmLeave = false }, Modifier.weight(1f))
                BigButton(
                    if (session.isHost) "Close it" else "Leave",
                    ButtonTone.DANGER,
                    {
                        confirmLeave = false
                        onLeave()
                    },
                    Modifier.weight(1f),
                )
            },
        ) {
            HintText(if (session.isHost) "This ends the game for everyone." else "A bot will take your seat if a game is running.", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EndCard(title: String, message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(GameTheme.screenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle(title)
                HintText(message, textAlign = TextAlign.Center)
                BigButton("Back to menu", ButtonTone.PRIMARY, onBack)
            }
        }
    }
}
