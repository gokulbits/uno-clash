package dev.colorclash.app.ui.room

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.colorclash.app.net.LocalNetwork
import dev.colorclash.app.session.Hosting
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.QrCode
import dev.colorclash.app.ui.components.RoomCodeTiles
import dev.colorclash.app.ui.components.SectionTitle
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
    onLeave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val me = lobby.seats.getOrNull(lobby.you)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLeave) { Text("‹ Leave", color = GameTheme.text) }
            Text("Lobby", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }

        Text("Room code", color = GameTheme.mutedText)
        RoomCodeTiles(lobby.roomCode)

        if (hosting != null) HostInvite(lobby.roomCode, hosting.port)

        Panel {
            SectionTitle("Players ${lobby.seats.size}/${GameEngine.MAX_PLAYERS}")
            lobby.seats.forEach { seat ->
                PlayerRow(seat, isYou = seat.seat == lobby.you, onRemove = hosting?.takeIf { seat.isBot }?.let { { it.host.removeBot(seat.seat) } })
            }
            if (hosting != null && lobby.seats.size < GameEngine.MAX_PLAYERS) {
                TextButton(onClick = { hosting.host.addBot() }) { Text("+ Add a bot", color = GameTheme.green) }
            }
        }

        RulesPanel(lobby.settings, editable = hosting != null) { hosting?.host?.updateSettings(it) }

        startError?.let { Text(it, color = GameTheme.yellow, textAlign = TextAlign.Center) }

        if (hosting != null) {
            BigButton(
                if (lobby.canStart) "Start game" else "Waiting for players…",
                GameTheme.red,
                { scope.launch { onStartError(hosting.host.startGame()) } },
                enabled = lobby.canStart,
            )
        } else if (me != null) {
            BigButton(
                if (me.ready) "Ready! (tap to undo)" else "I'm ready",
                if (me.ready) GameTheme.green else GameTheme.blue,
                { onReady(!me.ready) },
            )
            Text("The host starts the game once everyone is ready.", color = GameTheme.mutedText, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HostInvite(roomCode: String, port: Int) {
    val context = LocalContext.current
    // Addresses change when the hotspot is switched on after the room opened, so keep checking.
    val addresses by produceState(emptyList<String>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { LocalNetwork.hostAddresses() }
            delay(3_000)
        }
    }
    Panel {
        SectionTitle("Invite friends")
        if (addresses.isEmpty()) {
            Text(
                "No Wi-Fi yet. Join a Wi-Fi network or turn on your hotspot so friends can connect.",
                color = GameTheme.yellow,
            )
        } else {
            QrCode(JoinLink(addresses, port, roomCode).toUri(), Modifier.fillMaxWidth(0.75f).align(Alignment.CenterHorizontally))
            Text(
                "Scan with the game or your camera app, or enter the room code.",
                color = GameTheme.mutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "IP: " + addresses.joinToString("  ·  ") { "$it:$port" },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
        TextButton(onClick = { openHotspotSettings(context) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("No Wi-Fi around? Turn on your hotspot ›", color = GameTheme.yellow)
        }
    }
}

@Composable
private fun PlayerRow(seat: SeatInfo, isYou: Boolean, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isYou) GameTheme.panelStrong else Color.Transparent)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarBadge(seat, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(seat.name + if (isYou) " (you)" else "", fontWeight = FontWeight.Bold)
            val tag = when {
                seat.isHost -> "Host"
                seat.isBot -> "Bot"
                !seat.connected -> "Reconnecting…"
                else -> null
            }
            tag?.let { Text(it, color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium) }
        }
        Text(
            if (seat.ready) "✔ Ready" else "Waiting",
            color = if (seat.ready) GameTheme.green else GameTheme.mutedText,
            fontWeight = FontWeight.Bold,
        )
        onRemove?.let { TextButton(onClick = it) { Text("✕", color = GameTheme.danger) } }
    }
}

@Composable
private fun RulesPanel(settings: GameSettings, editable: Boolean, onChange: (GameSettings) -> Unit) {
    val rules = settings.rules
    fun update(block: RuleSet.() -> RuleSet) = onChange(settings.copy(rules = rules.block()))

    Panel {
        SectionTitle(if (editable) "House rules" else "House rules (set by host)")
        RuleSwitch("Stack +2 and +4", "Pass the pain along instead of drawing", rules.stackDraws, editable) { update { copy(stackDraws = it) } }
        RuleSwitch("Jump in", "Play an identical card out of turn", rules.jumpIn, editable) { update { copy(jumpIn = it) } }
        RuleSwitch("7-0", "7 swaps hands, 0 passes every hand along", rules.sevenZero, editable) { update { copy(sevenZero = it) } }
        RuleSwitch("Draw until playable", "Keep drawing until you can play", rules.drawUntilPlayable, editable) { update { copy(drawUntilPlayable = it) } }
        RuleSwitch("Challenge +4", "Call out an illegal Wild Draw 4", rules.challengeDrawFour, editable) { update { copy(challengeDrawFour = it) } }


        Text("Play to", fontWeight = FontWeight.Bold)
        ChoiceChips(listOf(0, 100, 250, 500), rules.targetScore, editable, { if (it == 0) "1 round" else "$it pts" }) { update { copy(targetScore = it) } }
        Text("Finishing on a power card", fontWeight = FontWeight.Bold)
        Text("Going out on a Skip, Reverse, +2 or Wild: the card still works, but you draw instead of winning", color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        ChoiceChips(listOf(0, 2, 4), rules.powerFinishPenalty, editable, { if (it == 0) "Allowed" else "Draw $it" }) {
            update { copy(powerFinishPenalty = it) }
        }
        Text("Bot skill", fontWeight = FontWeight.Bold)
        ChoiceChips(BotLevel.entries, settings.botLevel, editable, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) {
            onChange(settings.copy(botLevel = it))
        }
        Text("Turn timer", fontWeight = FontWeight.Bold)
        ChoiceChips(listOf<Int?>(null, 15, 30, 60), settings.turnTimerSeconds, editable, { it?.let { s -> "${s}s" } ?: "Off" }) {
            onChange(settings.copy(turnTimerSeconds = it))
        }
    }
}

@Composable
private fun RuleSwitch(title: String, detail: String, checked: Boolean, editable: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = editable,
            colors = SwitchDefaults.colors(checkedTrackColor = GameTheme.green, disabledCheckedTrackColor = GameTheme.green.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, editable: Boolean, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, option ->
            FilterChip(
                selected = option == selected,
                onClick = { onPick(option) },
                enabled = editable,
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GameTheme.cardColors[i % 4],
                    selectedLabelColor = Color.White,
                    disabledSelectedContainerColor = GameTheme.cardColors[i % 4].copy(alpha = 0.6f),
                ),
            )
        }
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
