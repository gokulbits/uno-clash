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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.game.GameScreen
import dev.colorclash.app.ui.game.describe
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.net.client.ClientStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val LOG_LINES = 3

/** Everything once you're in a room: connecting, lobby, game, and the ways it can end. */
@Composable
fun RoomScreen(session: Session, onLeave: () -> Unit) {
    val status by session.client.status.collectAsState()
    val lobby by session.client.lobby.collectAsState()
    val game by session.client.game.collectAsState()
    var confirmLeave by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Collected here rather than in the game screen so the first updates of a round aren't missed.
    LaunchedEffect(session) {
        session.client.updates.collect { update ->
            val lines = update.events.mapNotNull { describe(it, update.view) }
            if (lines.isNotEmpty()) log = (log + lines).takeLast(LOG_LINES)
        }
    }
    LaunchedEffect(session) {
        session.client.notices.collectLatest {
            notice = it
            delay(2_000)
            notice = null
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { confirmLeave = true }

    Box(Modifier.fillMaxSize()) {
        when (val s = status) {
            is ClientStatus.Failed -> EndCard("Couldn't stay connected", s.message, onLeave)
            is ClientStatus.Closed -> EndCard("Room closed", s.reason, onLeave)
            else -> when {
                game != null -> GameScreen(
                    view = game!!,
                    log = log,
                    notice = notice,
                    isHost = session.isHost,
                    onIntent = session.client::send,
                    onNextRound = { session.hosting?.host?.startNextRound() },
                    onBackToLobby = { session.hosting?.host?.returnToLobby() },
                    onLeave = { confirmLeave = true },
                )
                lobby != null -> LobbyScreen(
                    lobby = lobby!!,
                    hosting = session.hosting,
                    startError = startError,
                    onStartError = { startError = it },
                    onReady = { session.client.setReady(it) },
                    onLeave = { confirmLeave = true },
                )
                else -> Connecting()
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
