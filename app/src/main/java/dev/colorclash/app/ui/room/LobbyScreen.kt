package dev.colorclash.app.ui.room

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.colorclash.app.net.LocalNetwork
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.RoundIconButton
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.NoticeBar
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.QrCode
import dev.colorclash.app.ui.components.RoomCodeTiles
import dev.colorclash.app.ui.components.ScreenHeader
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.SegmentedPicker
import dev.colorclash.app.ui.components.TextLink
import dev.colorclash.app.ui.components.checkIcon
import dev.colorclash.app.ui.components.closeIcon
import dev.colorclash.app.ui.components.exitIcon
import dev.colorclash.app.ui.components.lockIcon
import dev.colorclash.app.ui.components.plusIcon
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.JoinLink
import dev.colorclash.protocol.LobbyView
import dev.colorclash.protocol.SeatInfo
import dev.colorclash.rules.BotLevel
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameMode
import dev.colorclash.rules.RuleSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What only the host can do in the lobby. The screen doesn't need to know there's a server behind it. */
class LobbyHostControls(
    val port: Int,
    val start: suspend () -> String?,
    val addBot: () -> Unit,
    val removeBot: (seat: Int) -> Unit,
)

@Composable
fun LobbyScreen(
    lobby: LobbyView,
    hosting: LobbyHostControls?,
    startError: String?,
    onStartError: (String?) -> Unit,
    onReady: (Boolean) -> Unit,
    onSettingsChange: (GameSettings) -> Unit,
    onLeave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Everyone reads the rules on the way in: guests as they ready up, the host as they start.
    var rulesFor by remember { mutableStateOf<RulesGate?>(null) }
    // The settings this player last readied up for, so they hear about it when the host changes them.
    var acceptedSettings by remember { mutableStateOf<GameSettings?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(GameTheme.sectionGap),
        ) {
            ScreenHeader("Lobby", onLeave, backLabel = "Leave", icon = { exitIcon(it) }) {
                RoomCodeBox(lobby.roomCode)
            }

            if (hosting != null) HostInvite(lobby.roomCode, hosting.port, lobby.seats.firstOrNull { it.isHost }?.name.orEmpty())

            Panel {
                SectionTitle("Players ${lobby.seats.size}/${GameEngine.MAX_PLAYERS}")
                PlayerGrid(lobby, hosting)
            }

            RulesPanel(lobby.settings, editable = hosting != null, onSettingsChange, onHowToPlay = { rulesFor = RulesGate.LOOK })
        }
        val me = lobby.seats.getOrNull(lobby.you)
        val rulesChanged = hosting == null && me?.ready == false && acceptedSettings.let { it != null && it != lobby.settings }
        LobbyActions(
            lobby,
            hosting,
            startError,
            notice = "The host changed the rules. Have a look, then ready up again.".takeIf { rulesChanged },
            onStart = { rulesFor = RulesGate.START },
            onReady = { ready -> if (ready) rulesFor = RulesGate.READY else onReady(false) },
        )
    }

    rulesFor?.let { gate ->
        val close = { rulesFor = null }
        RulesDialog(lobby.settings, onDismiss = close) {
            when (gate) {
                RulesGate.LOOK -> BigButton("Got it", ButtonTone.PRIMARY, close, Modifier.weight(1f))
                RulesGate.READY -> {
                    BigButton("Not yet", ButtonTone.QUIET, close, Modifier.weight(1f))
                    BigButton("I'm ready", ButtonTone.PRIMARY, {
                        close()
                        acceptedSettings = lobby.settings
                        onReady(true)
                    }, Modifier.weight(1f))
                }
                RulesGate.START -> {
                    BigButton("Back", ButtonTone.QUIET, close, Modifier.weight(1f))
                    BigButton("Start", ButtonTone.PRIMARY, {
                        close()
                        scope.launch { onStartError(hosting?.start()) }
                    }, Modifier.weight(1f), enabled = lobby.canStart)
                }
            }
        }
    }
}

/** Why the rules are up: just looking, or on the way to readying up or starting. */
private enum class RulesGate { LOOK, READY, START }

/** Start or ready up, pinned under the scrolling lobby so nobody has to go looking for it. */
@Composable
private fun LobbyActions(
    lobby: LobbyView,
    hosting: LobbyHostControls?,
    startError: String?,
    notice: String?,
    onStart: () -> Unit,
    onReady: (Boolean) -> Unit,
) {
    val me = lobby.seats.getOrNull(lobby.you)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = GameTheme.CORNER_SHEET, topEnd = GameTheme.CORNER_SHEET))
            .background(GameTheme.panel)
            .padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        startError?.let { NoticeBar(it) }
        notice?.let { NoticeBar(it, error = false) }
        if (hosting != null) {
            BigButton(startLabel(lobby), ButtonTone.PRIMARY, onStart, enabled = lobby.canStart)
        } else if (me != null) {
            if (me.ready) {
                BigButton("Ready! Tap to undo", ButtonTone.QUIET, { onReady(false) }, icon = { checkIcon(it) })
            } else {
                // Nudges until they tap it. Read in the layer: as a plain value this recomposed every frame.
                val transition = rememberInfiniteTransition(label = "ready")
                val nudge = transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "readyScale")
                BigButton("I'm ready", ButtonTone.PRIMARY, { onReady(true) }, Modifier.nudging(nudge))
            }
        }
    }
}

private fun Modifier.nudging(scale: State<Float>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

/** The room code, small and boxed, in the header. */
@Composable
private fun RoomCodeBox(code: String) {
    Column(
        Modifier
            .clip(GameTheme.tileShape)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, GameTheme.tileShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ROOM CODE", color = GameTheme.mutedText, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(code, style = MaterialTheme.typography.titleMedium, letterSpacing = 3.sp)
    }
}

/** A small QR beside the ways in; tap it for a big one to scan from across the table. */
@Composable
private fun HostInvite(roomCode: String, port: Int, hostName: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var bigQr by remember { mutableStateOf(false) }
    // Addresses change when the hotspot is switched on after the room opened, so keep checking.
    // Only while it can be seen: a host's process stays up with the screen off.
    val addresses by produceState(emptyList<String>(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = withContext(Dispatchers.IO) { LocalNetwork.hostAddresses() }
                delay(3_000)
            }
        }
    }
    val link = JoinLink(addresses, port, roomCode).toUri()
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (addresses.isNotEmpty()) {
                QrCode(link, Modifier.size(112.dp).clickable(onClickLabel = "Show a bigger QR code") { bigQr = true })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(clashName(hostName), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (addresses.isEmpty()) {
                    NoticeBar("No Wi-Fi yet. Join a Wi-Fi network or turn on your hotspot so friends can connect.", error = false)
                } else {
                    HintText("Scan with the game or any camera app (tap to enlarge), or enter the room code.")
                }
                TextLink("No Wi-Fi? Turn on your hotspot", { openHotspotSettings(context) })
            }
        }
    }
    if (bigQr && addresses.isNotEmpty()) {
        Dialog(onDismissRequest = { bigQr = false }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(link, Modifier.fillMaxWidth().clickable { bigQr = false })
                RoomCodeTiles(roomCode)
                // Still here for anyone joining by typing the host's address.
                HintText(addresses.joinToString("  ·  ") { "$it:$port" }, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Everyone at the table as tiles, three to a row, with room for a bot in the next free seat. */
@Composable
private fun PlayerGrid(lobby: LobbyView, hosting: LobbyHostControls?) {
    val canAddBot = hosting != null && lobby.seats.size < GameEngine.MAX_PLAYERS
    val tiles: List<SeatInfo?> = lobby.seats + if (canAddBot) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(PLAYERS_PER_ROW).forEach { row ->
            // Intrinsic height, so an add-bot tile lines up with the taller player tiles next to it.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { seat ->
                    val tile = Modifier.weight(1f).fillMaxHeight()
                    if (seat == null) {
                        AddBotTile(tile) { hosting?.addBot?.invoke() }
                    } else {
                        PlayerTile(
                            seat,
                            isYou = seat.seat == lobby.you,
                            onRemove = hosting?.takeIf { seat.isBot }?.let { { it.removeBot(seat.seat) } },
                            modifier = tile,
                        )
                    }
                }
                repeat(PLAYERS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayerTile(seat: SeatInfo, isYou: Boolean, onRemove: (() -> Unit)?, modifier: Modifier) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(GameTheme.tileShape)
                .background(GameTheme.panelStrong)
                .then(if (isYou) Modifier.border(2.dp, GameTheme.highlight, GameTheme.tileShape) else Modifier)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                AvatarBadge(seat, size = 42.dp)
                // Ready shows as a tick on the avatar.
                if (seat.ready) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(GameTheme.green),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(11.dp)) { checkIcon(GameTheme.contentOn(GameTheme.green)) }
                    }
                }
            }
            Text(seat.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (tag, color) = when {
                !seat.connected -> "Reconnecting…" to GameTheme.highlight
                !seat.ready -> "Not ready" to GameTheme.highlight
                isYou -> "You" to GameTheme.mutedText
                seat.isHost -> "Host" to GameTheme.mutedText
                seat.isBot -> "Bot" to GameTheme.mutedText
                else -> "Ready" to GameTheme.green
            }
            Text(tag, color = color, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, maxLines = 1)
        }
        onRemove?.let {
            RoundIconButton("Remove bot", it, Modifier.align(Alignment.TopEnd).padding(2.dp), size = 28.dp) { tint -> closeIcon(tint) }
        }
    }
}

@Composable
private fun AddBotTile(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier
            .clip(GameTheme.tileShape)
            .border(1.dp, GameTheme.glassEdge, GameTheme.tileShape)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) { plusIcon(GameTheme.mutedText) }
        }
        Text("Add a bot", color = GameTheme.mutedText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RulesPanel(settings: GameSettings, editable: Boolean, onChange: (GameSettings) -> Unit, onHowToPlay: () -> Unit) {
    val rules = settings.rules
    val noMercy = rules.noMercy
    val flip = rules.flip
    // No Mercy decides these itself; the Flip deck has no card for 7-0 or the swap.
    val houseRules = editable && !noMercy && !flip
    val drawRules = editable && !noMercy
    fun update(block: RuleSet.() -> RuleSet) = onChange(settings.copy(rules = rules.block()))
    // No Mercy brings its own rules, so the ones it decides are shown on and locked.
    val toggles = listOf(
        RuleToggle(
            if (flip) "Light side stacks" else "Stack draws",
            if (flip) "The dark side always does" else "Pass the pain along",
            rules.stacking,
            drawRules,
        ) { update { copy(stackDraws = it) } },
        RuleToggle("Jump in", "Same card, out of turn", rules.jumpIn, editable) { update { copy(jumpIn = it) } },
        RuleToggle("7-0", "7 swaps, 0 passes all", rules.swaps, houseRules) { update { copy(sevenZero = it) } },
        RuleToggle("Swap hands", "A wild that takes a hand", rules.swapHands, houseRules) {
            update { copy(swapHandsWild = it) }
        },
        RuleToggle("Draw till playable", "Draw until you can play", rules.drawsUntilPlayable, drawRules) {
            update { copy(drawUntilPlayable = it) }
        },
        RuleToggle(
            if (flip) "Challenge wild" else "Challenge +4",
            if (flip) "Call out an illegal wild" else "Call out an illegal +4",
            rules.canChallenge,
            editable && !noMercy,
        ) {
            update { copy(challengeDrawFour = it) }
        },
    )

    Panel {
        SectionTitle(if (editable) "Game mode" else "Game mode (set by host)")
        SettingRow("Mode", GameMode.entries, rules.mode, editable, ::modeLabel) { onChange(settings.copy(rules = rules.switchTo(it))) }
        HintText(
            when (rules.mode) {
                GameMode.NO_MERCY ->
                    "168 cards with +4, +6, +10, Skip Everyone, Discard All and Color Roulette. " +
                        "Reach ${rules.mercyLimit} cards and you're out of the round; the winner gets ${rules.knockoutBonus} for each."
                GameMode.FLIP ->
                    "112 double-sided cards. A Flip card turns every hand and both piles over to their other side: " +
                        "new colors, and harder cards on the dark one — +5s that stack, Skip Everyone and a wild that draws until your " +
                        "color shows. Facing a stack? Flip it onto the next player."
                GameMode.CLASSIC ->
                    "The 108-card game. Match by color or number, first to empty their hand wins the round."
            },
        )
        TextLink("How to play with these rules", onHowToPlay)
    }

    Panel {
        SectionTitle(if (editable) "House rules" else "House rules (set by host)")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            toggles.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { rule ->
                        RuleTile(rule.title, rule.detail, rule.on, rule.editable, Modifier.weight(1f).fillMaxHeight()) {
                            rule.set(!rule.on)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        SettingRow("Play to", targetScores(rules.mode), rules.targetScore, editable, { if (it == 0) "1 round" else "$it" }) {
            update { copy(targetScore = it) }
        }
        if (!noMercy) {
            SettingRow("Power finish", listOf(0, 2, 4), rules.powerFinishPenalty, editable, { if (it == 0) "Allowed" else "Draw $it" }) {
                update { copy(powerFinishPenalty = it) }
            }
            HintText("Going out on a Skip, Reverse, +2 or Wild: the card still works, but you draw instead of winning")
        }
        SettingRow("Bots", BotLevel.entries, settings.botLevel, editable, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
            onChange(settings.copy(botLevel = it))
        }
        SettingRow("Turn timer", listOf<Int?>(null, 15, 30, 60), settings.turnTimerSeconds, editable, { it?.let { s -> "${s}s" } ?: "Off" }) {
            onChange(settings.copy(turnTimerSeconds = it))
        }
    }
}

private class RuleToggle(
    val title: String,
    val detail: String,
    val on: Boolean,
    val editable: Boolean,
    val set: (Boolean) -> Unit,
)

private fun modeLabel(mode: GameMode) = when (mode) {
    GameMode.NO_MERCY -> "No Mercy"
    GameMode.FLIP -> "Flip"
    GameMode.CLASSIC -> "Classic"
}

/**
 * A No Mercy round pays out about five times a Classic one — hands run to the mercy limit and every
 * knockout is worth 250 — so it plays to bigger numbers. At six players 1000 is a round or two and
 * [NO_MERCY_TARGET] is the one that gives a game the shape Classic has.
 */
private fun targetScores(mode: GameMode) = when (mode) {
    GameMode.NO_MERCY -> listOf(0, 1000, NO_MERCY_TARGET, 5000)
    // Flip pays more per round than Classic: the dark side alone is worth up to 60 a card.
    GameMode.FLIP -> listOf(0, 250, CLASSIC_TARGET, 1000)
    GameMode.CLASSIC -> listOf(0, 100, 250, CLASSIC_TARGET)
}

private const val CLASSIC_TARGET = 500
private const val NO_MERCY_TARGET = 3000

/** Switching mode moves the target score onto the other mode's scale. One round means one round in both. */
private fun RuleSet.switchTo(mode: GameMode): RuleSet = when {
    mode == this.mode -> this
    targetScore == 0 -> copy(mode = mode)
    mode == GameMode.NO_MERCY -> copy(mode = mode, targetScore = NO_MERCY_TARGET)
    else -> copy(mode = mode, targetScore = CLASSIC_TARGET)
}

/**
 * A house rule as a tile you tap on and off; outlined with a tick when it's on. One you can't change
 * — the mode forces it, or you're not the host — is greyed out and carries a padlock instead, so an
 * off rule that's locked off never reads as one you could turn on.
 */
@Composable
private fun RuleTile(title: String, detail: String, on: Boolean, editable: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val tint = if (editable) GameTheme.text else GameTheme.mutedText
    val mark = if (editable) GameTheme.highlight else GameTheme.mutedText
    Row(
        modifier
            .clip(GameTheme.tileShape)
            .background(if (on) GameTheme.panelStrong else GameTheme.panel)
            .then(if (on) Modifier.border(2.dp, mark.copy(alpha = if (editable) 1f else 0.5f), GameTheme.tileShape) else Modifier)
            .then(if (editable) Modifier else Modifier.alpha(LOCKED_TILE_ALPHA))
            .clickable(enabled = editable, role = Role.Switch, onClick = onToggle)
            .semantics { contentDescription = "$title, ${if (on) "on" else "off"}${if (editable) "" else ", locked"}" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HintText(detail)
        }
        Box(Modifier.size(14.dp)) {
            when {
                !editable -> Canvas(Modifier.fillMaxSize()) { lockIcon(mark) }
                on -> Canvas(Modifier.fillMaxSize()) { checkIcon(mark) }
            }
        }
    }
}

/** Enough to read as off limits without going so dim the words can't be read. */
private const val LOCKED_TILE_ALPHA = 0.55f

/** A setting on one line: its name, then the options side by side. */
@Composable
private fun <T> SettingRow(name: String, options: List<T>, selected: T, editable: Boolean, label: (T) -> String, onPick: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(86.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        SegmentedPicker(options, selected, editable, label, onPick, Modifier.weight(1f))
    }
}

private fun openHotspotSettings(context: Context) {
    val candidates = listOf(
        Intent("android.settings.TETHER_SETTINGS"),
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private const val PLAYERS_PER_ROW = 3
