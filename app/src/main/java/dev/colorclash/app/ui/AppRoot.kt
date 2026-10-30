package dev.colorclash.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colorclash.app.ColorClashApp
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.session.Permissions
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.GameDialog
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.game.LocalCardMeasurer
import dev.colorclash.app.ui.home.HomeScreen
import dev.colorclash.app.ui.join.JoinScreen
import dev.colorclash.app.ui.join.ScanScreen
import dev.colorclash.app.ui.room.RoomScreen
import dev.colorclash.app.ui.settings.SettingsScreen
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalCardBack
import dev.colorclash.app.ui.theme.LocalTableStyle
import dev.colorclash.app.ui.update.UpdateDialog
import dev.colorclash.protocol.JoinLink
import kotlinx.coroutines.launch

/** [depth] is how far in from the home screen, which decides which way a change of screen slides. */
private enum class Screen(val depth: Int) { HOME(0), JOIN(1), SCAN(2), SETTINGS(1) }

/** What fills the window: a menu screen, or the room we're in. */
private sealed interface Destination {
    data class Menu(val screen: Screen) : Destination
    data class Room(val session: Session) : Destination
}

private const val PRACTICE_BOTS = 3

@Composable
fun AppRoot(app: ColorClashApp, incomingLink: JoinLink?, onLinkHandled: () -> Unit) {
    val context = LocalContext.current
    val sessions = app.sessions
    val session by sessions.session.collectAsStateWithLifecycle()
    val profile by app.prefs.profile.collectAsStateWithLifecycle()
    val feedback by app.prefs.feedback.collectAsStateWithLifecycle()
    val play by app.prefs.play.collectAsStateWithLifecycle()
    val stats by app.prefs.stats.collectAsStateWithLifecycle()
    val looks by app.prefs.looks.collectAsStateWithLifecycle()
    val update by app.updater.state.collectAsStateWithLifecycle()
    val table = GameTheme.table(looks.table)
    val rejoin by produceState<SavedSession?>(null, session) { value = sessions.rejoinable() }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var offeredLink by remember { mutableStateOf<JoinLink?>(null) }
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

    // A newer build on GitHub is offered once per launch, never in the middle of a game.
    LaunchedEffect(Unit) { app.updater.checkOnce() }

    // Anything can open one of these links, a web page included, so joining waits for a yes.
    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        onLinkHandled()
        when {
            session != null -> Unit
            profile.name.isBlank() -> message = "Pick a name first, then scan the code again."
            else -> offeredLink = link
        }
    }

    CompositionLocalProvider(
        LocalTableStyle provides table,
        LocalCardBack provides GameTheme.cardBack(looks.cardBack),
        LocalCardMeasurer provides rememberTextMeasurer(cacheSize = CARD_LABELS_CACHED),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(table.brush)
                .safeDrawingPadding(),
        ) {
            val current = session
            if (current == null) {
                UpdateDialog(update, onUpdate = app.updater::download, onDismiss = app.updater::dismiss)
            }
            AnimatedContent(
                targetState = current?.let { Destination.Room(it) } ?: Destination.Menu(screen),
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val from = initialState
                    val to = targetState
                    val change = if (from is Destination.Menu && to is Destination.Menu) {
                        // A short slide the way you're heading; just enough to feel like going somewhere.
                        val direction = if (to.screen.depth >= from.screen.depth) 1 else -1
                        (slideInHorizontally(tween(SCREEN_MILLIS)) { direction * it / 10 } + fadeIn(tween(SCREEN_MILLIS))) togetherWith
                            (slideOutHorizontally(tween(SCREEN_MILLIS)) { -direction * it / 10 } + fadeOut(tween(SCREEN_MILLIS / 2)))
                    } else {
                        // No exit: a room that's closing has nothing worth showing on its way out.
                        fadeIn(tween(SCREEN_MILLIS)) togetherWith ExitTransition.None
                    }
                    // Unclipped, so the table's overlays can dim the system bars too.
                    change.using(SizeTransform(clip = false))
                },
                label = "screen",
            ) { destination ->
                when (destination) {
                    is Destination.Room -> RoomScreen(destination.session, onLeave = {
                        sessions.leave()
                        screen = Screen.HOME
                    })
                    is Destination.Menu -> when (destination.screen) {
                        Screen.HOME -> HomeScreen(
                            profile = profile,
                            onProfileChange = app.prefs::updateProfile,
                            rejoin = rejoin,
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
                            val rooms by remember { sessions.discovery.rooms() }.collectAsStateWithLifecycle(emptyList())
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

        offeredLink?.let { link ->
            GameDialog(
                title = "Join room ${link.roomCode}?",
                onDismiss = { offeredLink = null },
                actions = {
                    BigButton("Not now", ButtonTone.QUIET, { offeredLink = null }, Modifier.weight(1f))
                    BigButton(
                        "Join",
                        ButtonTone.PRIMARY,
                        {
                            offeredLink = null
                            screen = Screen.JOIN
                            withPermissions(Permissions.network()) { sessions.join(link) }
                        },
                        Modifier.weight(1f),
                    )
                },
            ) {
                HintText("Everyone in the room at ${link.hosts.first()} will see your name and avatar.", textAlign = TextAlign.Center)
            }
        }
    }
}

private const val SCREEN_MILLIS = 220
// Plenty for the digits and +2/+4 at the few sizes a card gets drawn.
private const val CARD_LABELS_CACHED = 64
