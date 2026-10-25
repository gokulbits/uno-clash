package dev.colorclash.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.colorclash.app.ColorClashApp
import dev.colorclash.app.session.Permissions
import dev.colorclash.app.ui.home.HomeScreen
import dev.colorclash.app.ui.join.JoinScreen
import dev.colorclash.app.ui.join.ScanScreen
import dev.colorclash.app.ui.room.RoomScreen
import dev.colorclash.app.ui.settings.SettingsScreen
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalCardBack
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.protocol.JoinLink
import kotlinx.coroutines.launch

private enum class Screen { HOME, JOIN, SCAN, SETTINGS }

private const val PRACTICE_BOTS = 3

@Composable
fun AppRoot(app: ColorClashApp, incomingLink: JoinLink?, onLinkHandled: () -> Unit) {
    val context = LocalContext.current
    val sessions = app.sessions
    val session by sessions.session.collectAsState()
    val profile by app.prefs.profile.collectAsState()
    val feedback by app.prefs.feedback.collectAsState()
    val play by app.prefs.play.collectAsState()
    val stats by app.prefs.stats.collectAsState()
    val looks by app.prefs.looks.collectAsState()
    val table = GameTheme.table(looks.table)
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> String?) {
        scope.launch {
            busy = true
            message = null
            message = action()
            busy = false
        }
    }

    // Ask for whatever's missing first, then carry on with the action that needed it.
    var pending by remember { mutableStateOf<(suspend () -> String?)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val action = pending
        pending = null
        if (results[Manifest.permission.ACCESS_LOCAL_NETWORK] == false) {
            message = "$GAME_TITLE needs local network access to play with phones around you."
        } else if (action != null) {
            run(action)
        }
    }
    fun withPermissions(permissions: List<String>, action: suspend () -> String?) {
        val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            run(action)
        } else {
            pending = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        onLinkHandled()
        if (session == null && profile.name.isNotBlank()) {
            screen = Screen.JOIN
            withPermissions(Permissions.network()) { sessions.join(link) }
        }
    }

    CompositionLocalProvider(LocalTableStyle provides table, LocalCardBack provides GameTheme.cardBack(looks.cardBack)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(table.brush)
                .safeDrawingPadding(),
        ) {
            val current = session
            if (current != null) {
                RoomScreen(current, onLeave = {
                    sessions.leave()
                    screen = Screen.HOME
                })
                return@Box
            }
            when (screen) {
                Screen.HOME -> HomeScreen(
                    profile = profile,
                    onProfileChange = app.prefs::updateProfile,
                    rejoin = remember(session) { sessions.rejoinable() },
                    busy = busy,
                    message = message,
                    onHost = { withPermissions(Permissions.hosting()) { sessions.host() } },
                    onJoin = {
                        message = null
                        screen = Screen.JOIN
                    },
                    onPractice = { withPermissions(Permissions.hosting()) { sessions.host(bots = PRACTICE_BOTS) } },
                    onRejoin = { saved -> withPermissions(Permissions.network()) { sessions.rejoin(saved) } },
                    onSettings = { screen = Screen.SETTINGS },
                )
                Screen.JOIN -> {
                    BackHandler { screen = Screen.HOME }
                    val rooms by remember { sessions.discovery.rooms() }.collectAsState(emptyList())
                    JoinScreen(
                        rooms = rooms,
                        busy = busy,
                        message = message,
                        onBack = { screen = Screen.HOME },
                        onPickRoom = { room, code -> withPermissions(Permissions.network()) { sessions.joinRoom(room, code) } },
                        onScan = { screen = Screen.SCAN },
                        onJoinCode = { code -> withPermissions(Permissions.network()) { sessions.joinByCode(code) } },
                        onJoinAddress = { ip, port, code -> withPermissions(Permissions.network()) { sessions.joinAddress(ip, port, code) } },
                    )
                }
                Screen.SETTINGS -> {
                    BackHandler { screen = Screen.HOME }
                    SettingsScreen(
                        profile = profile,
                        onProfileChange = app.prefs::updateProfile,
                        feedback = feedback,
                        onFeedbackChange = app.prefs::updateFeedback,
                        play = play,
                        onPlayChange = app.prefs::updatePlay,
                        looks = looks,
                        onLooksChange = app.prefs::updateLooks,
                        stats = stats,
                        onBack = { screen = Screen.HOME },
                    )
                }
                Screen.SCAN -> {
                    BackHandler { screen = Screen.JOIN }
                    ScanScreen(
                        onBack = { screen = Screen.JOIN },
                        onScanned = { link ->
                            screen = Screen.JOIN
                            withPermissions(Permissions.network()) { sessions.join(link) }
                        },
                    )
                }
            }
        }
    }
}
