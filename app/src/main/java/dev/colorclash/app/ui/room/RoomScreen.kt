package dev.colorclash.app.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.LocalSeatPhotos
import dev.colorclash.app.ui.components.rememberSeatPhotos
import dev.colorclash.app.colorClash
import dev.colorclash.app.ui.game.GameScreen
import dev.colorclash.app.feedback.Buzz
import dev.colorclash.app.feedback.Sfx
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.net.client.ClientStatus

/** Everything once you're in a room: connecting, lobby, game, and the ways it can end. */
@Composable
fun RoomScreen(session: Session, onLeave: () -> Unit) {
    val app = LocalContext.current.colorClash
    // Owns the log, history, stats and the FX queue, and outlives this composition.
    val presenter = remember(session) { app.presenterFor(session) }
    val fx = presenter.fx

    val status by session.client.status.collectAsState()
    val lobby by session.client.lobby.collectAsState()
    val game by session.client.game.collectAsState()
    val photoData by session.client.photos.collectAsState()
    val photos = rememberSeatPhotos(photoData)
    val log by presenter.log.collectAsState()
    val history by presenter.history.collectAsState()
    val recentPlays by presenter.recentPlays.collectAsState()
    val notice by presenter.notice.collectAsState()
    val feedbackSettings by app.prefs.feedback.collectAsState()
    val playSettings by app.prefs.play.collectAsState()
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
                            hosting = session.hosting,
                            startError = startError,
                            onStartError = { startError = it },
                            onReady = { session.client.setReady(it) },
                            onSettingsChange = { settings ->
                                session.hosting?.host?.updateSettings(settings)
                                app.prefs.lobbySettings = settings
                            },
                            onLeave = { confirmLeave = true },
                        )
                        else -> Connecting()
                    }
                }
            }

            if (status is ClientStatus.Reconnecting) {
                Text(
                    "Connection lost, reconnecting…",
                    Modifier
                        .fillMaxWidth()
                        .background(GameTheme.danger)
                        .padding(10.dp)
                        .align(Alignment.TopCenter),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (session.isHost) "Close the room?" else "Leave the room?") },
            text = { Text(if (session.isHost) "This ends the game for everyone." else "A bot will take your seat if a game is running.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Leave", color = GameTheme.danger) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Connecting() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = GameTheme.yellow)
        Text("Connecting to the room…", Modifier.padding(16.dp))
    }
}

@Composable
private fun EndCard(title: String, message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(message, color = GameTheme.mutedText, textAlign = TextAlign.Center)
        BigButton("Back to menu", GameTheme.blue, onBack)
    }
}
