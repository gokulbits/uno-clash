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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.colorclash.app.net.LocalNetwork
import dev.colorclash.app.session.Hosting
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.FrostedPill
import dev.colorclash.app.ui.components.QrCode
import dev.colorclash.app.ui.components.RoomCodeTiles
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.exitIcon
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.JoinLink
import dev.colorclash.protocol.LobbyView
import dev.colorclash.protocol.SeatInfo
import dev.colorclash.rules.BotLevel
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.RuleSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LobbyScreen(
    lobby: LobbyView,
    hosting: Hosting?,
    startError: String?,
    onStartError: (String?) -> Unit,
    onReady: (Boolean) -> Unit,
    onSettingsChange: (GameSettings) -> Unit,
    onLeave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FrostedPill("Leave", onLeave) { exitIcon() }
                Text("Lobby", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                RoomCodeBox(lobby.roomCode)
            }

            if (hosting != null) HostInvite(lobby.roomCode, hosting.port, lobby.seats.firstOrNull { it.isHost }?.name.orEmpty())

            LobbyCard {
                SectionTitle("Players ${lobby.seats.size}/${GameEngine.MAX_PLAYERS}")
                PlayerGrid(lobby, hosting)
            }

            RulesPanel(lobby.settings, editable = hosting != null, onSettingsChange)
        }
        LobbyActions(lobby, hosting, startError, onStartError, onReady)
    }
}

/** Start or ready up, pinned under the scrolling lobby so nobody has to go looking for it. */
@Composable
private fun LobbyActions(
    lobby: LobbyView,
    hosting: Hosting?,
    startError: String?,
    onStartError: (String?) -> Unit,
    onReady: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val me = lobby.seats.getOrNull(lobby.you)
    val needsReady = hosting == null && me?.ready == false
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(GameTheme.panel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        startError?.let { Text(it, color = GameTheme.yellow, textAlign = TextAlign.Center) }
        if (hosting != null) {
            BigButton(
                if (lobby.canStart) "Start game" else "Waiting for players…",
                GameTheme.red,
                { scope.launch { onStartError(hosting.host.startGame()) } },
                enabled = lobby.canStart,
            )
        } else if (me != null) {
            // Nudges until they tap it.
            val nudge = if (needsReady) {
                val pulse = rememberInfiniteTransition(label = "ready")
                pulse.animateFloat(1f, 1.04f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "readyScale").value
            } else {
                1f
            }
            BigButton(
                if (me.ready) "Ready! (tap to undo)" else "I'm ready",
                if (me.ready) GameTheme.green else GameTheme.blue,
                { onReady(!me.ready) },
                Modifier.scale(nudge),
            )
        }
    }
}

/** The room code, small and boxed, in the header. */
@Composable
private fun RoomCodeBox(code: String) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ROOM CODE", color = GameTheme.mutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(code, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    }
}

/** A small QR beside the ways in; tap it for a big one to scan from across the table. */
@Composable
private fun HostInvite(roomCode: String, port: Int, hostName: String) {
    val context = LocalContext.current
    var bigQr by remember { mutableStateOf(false) }
    // Addresses change when the hotspot is switched on after the room opened, so keep checking.
    val addresses by produceState(emptyList<String>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { LocalNetwork.hostAddresses() }
            delay(3_000)
        }
    }
    val link = JoinLink(addresses, port, roomCode).toUri()
    LobbyCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (addresses.isNotEmpty()) {
                QrCode(link, Modifier.size(112.dp).clickable(onClickLabel = "Show a bigger QR code") { bigQr = true })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(clashName(hostName), fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (addresses.isEmpty()) {
                    Text("No Wi-Fi yet. Join a Wi-Fi network or turn on your hotspot so friends can connect.", color = GameTheme.yellow, fontSize = 13.sp)
                } else {
                    Text("Scan with the game or any camera app (tap to enlarge), or enter the room code.", color = GameTheme.mutedText, fontSize = 13.sp)
                }
                Text(
                    "No Wi-Fi? Turn on your hotspot ›",
                    Modifier.clickable(role = Role.Button) { openHotspotSettings(context) }.padding(vertical = 4.dp),
                    color = GameTheme.yellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    if (bigQr && addresses.isNotEmpty()) {
        Dialog(onDismissRequest = { bigQr = false }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(link, Modifier.fillMaxWidth().clickable { bigQr = false })
                RoomCodeTiles(roomCode)
                // Still here for anyone joining by typing the host's address.
                Text(addresses.joinToString("  ·  ") { "$it:$port" }, color = GameTheme.mutedText, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Everyone at the table as tiles, three to a row, with room for a bot in the next free seat. */
@Composable
private fun PlayerGrid(lobby: LobbyView, hosting: Hosting?) {
    val canAddBot = hosting != null && lobby.seats.size < GameEngine.MAX_PLAYERS
    val tiles: List<SeatInfo?> = lobby.seats + if (canAddBot) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(PLAYERS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { seat ->
                    val tile = Modifier.weight(1f)
                    if (seat == null) {
                        AddBotTile(tile) { hosting?.host?.addBot() }
                    } else {
                        PlayerTile(
                            seat,
                            isYou = seat.seat == lobby.you,
                            onRemove = hosting?.takeIf { seat.isBot }?.let { { it.host.removeBot(seat.seat) } },
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
    val shape = RoundedCornerShape(14.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (isYou) GameTheme.panelStrong else GameTheme.panel)
                .border(if (isYou) 1.5.dp else 0.dp, if (isYou) GameTheme.text else Color.Transparent, shape)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                AvatarBadge(seat, size = 42.dp)
                // Ready shows as a tick on the avatar.
                if (seat.ready) {
                    Text(
                        "✓",
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(GameTheme.green),
                        color = GameTheme.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(seat.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (tag, color) = when {
                !seat.connected -> "Reconnecting…" to GameTheme.yellow
                !seat.ready -> "Not ready" to GameTheme.yellow
                isYou -> "You" to GameTheme.mutedText
                seat.isHost -> "Host" to GameTheme.mutedText
                seat.isBot -> "Bot" to GameTheme.mutedText
                else -> "Ready" to GameTheme.green
            }
            Text(tag, color = color, fontSize = 11.sp, maxLines = 1)
        }
        onRemove?.let {
            Text(
                "✕",
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(GameTheme.danger)
                    .clickable(onClickLabel = "Remove bot", role = Role.Button, onClick = it),
                color = GameTheme.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AddBotTile(modifier: Modifier, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .border(1.5.dp, GameTheme.green.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Text("+", color = GameTheme.green, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Text("Add a bot", color = GameTheme.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(" ", fontSize = 11.sp)
    }
}

@Composable
private fun RulesPanel(settings: GameSettings, editable: Boolean, onChange: (GameSettings) -> Unit) {
    val rules = settings.rules
    fun update(block: RuleSet.() -> RuleSet) = onChange(settings.copy(rules = rules.block()))
    val toggles = listOf(
        Triple("Stack +2 / +4", "Pass the pain along", rules.stackDraws) to { on: Boolean -> update { copy(stackDraws = on) } },
        Triple("Jump in", "Same card, out of turn", rules.jumpIn) to { on: Boolean -> update { copy(jumpIn = on) } },
        Triple("7-0", "7 swaps, 0 passes all", rules.sevenZero) to { on: Boolean -> update { copy(sevenZero = on) } },
        Triple("Draw till playable", "Draw until you can play", rules.drawUntilPlayable) to { on: Boolean -> update { copy(drawUntilPlayable = on) } },
        Triple("Challenge +4", "Call out an illegal +4", rules.challengeDrawFour) to { on: Boolean -> update { copy(challengeDrawFour = on) } },
    )

    LobbyCard {
        SectionTitle(if (editable) "House rules" else "House rules (set by host)")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            toggles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (rule, onToggle) ->
                        RuleTile(rule.first, rule.second, rule.third, editable, Modifier.weight(1f)) { onToggle(!rule.third) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        SettingRow("Play to", listOf(0, 100, 250, 500), rules.targetScore, editable, { if (it == 0) "1 round" else "$it" }) {
            update { copy(targetScore = it) }
        }
        SettingRow("Power finish", listOf(0, 2, 4), rules.powerFinishPenalty, editable, { if (it == 0) "Allowed" else "Draw $it" }) {
            update { copy(powerFinishPenalty = it) }
        }
        Text(
            "Going out on a Skip, Reverse, +2 or Wild: the card still works, but you draw instead of winning",
            color = GameTheme.mutedText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
        SettingRow("Bots", BotLevel.entries, settings.botLevel, editable, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
            onChange(settings.copy(botLevel = it))
        }
        SettingRow("Turn timer", listOf<Int?>(null, 15, 30, 60), settings.turnTimerSeconds, editable, { it?.let { s -> "${s}s" } ?: "Off" }) {
            onChange(settings.copy(turnTimerSeconds = it))
        }
    }
}

/** A house rule as a tile you tap on and off; green when it's on. */
@Composable
private fun RuleTile(title: String, detail: String, on: Boolean, editable: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (on) GameTheme.green.copy(alpha = if (editable) 0.85f else 0.55f) else GameTheme.panel)
            .clickable(enabled = editable, role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = GameTheme.text.copy(alpha = 0.75f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
        }
        Text(if (on) "✓" else "", fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.width(12.dp))
    }
}

/** A setting on one line: its name, then the options side by side. */
@Composable
private fun <T> SettingRow(name: String, options: List<T>, selected: T, editable: Boolean, label: (T) -> String, onPick: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(86.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(GameTheme.panel)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { i, option ->
                val picked = option == selected
                val color = GameTheme.cardColors[i % GameTheme.cardColors.size]
                Text(
                    label(option),
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (picked) color.copy(alpha = if (editable) 1f else 0.6f) else Color.Transparent)
                        .clickable(enabled = editable && !picked, role = Role.RadioButton) { onPick(option) }
                        .padding(vertical = 7.dp),
                    color = if (picked) GameTheme.text else GameTheme.mutedText,
                    fontWeight = if (picked) FontWeight.Black else FontWeight.Medium,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The lobby's cards: like [Panel], with less padding so more fits on one screen. */
@Composable
private fun LobbyCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GameTheme.panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
