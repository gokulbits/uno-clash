package dev.colorclash.app.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.colorclash.app.net.FoundRoom
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.room.clashName
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.DEFAULT_PORT
import dev.colorclash.protocol.JoinLink

@Composable
fun JoinScreen(
    rooms: List<FoundRoom>,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onPickRoom: (FoundRoom, code: String) -> Unit,
    onScan: () -> Unit,
    onJoinCode: (String) -> Unit,
    onJoinAddress: (ip: String, port: Int, code: String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(DEFAULT_PORT.toString()) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var codeFor by remember { mutableStateOf<FoundRoom?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = GameTheme.text) }
            Text("Join a game", style = MaterialTheme.typography.headlineMedium)
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), color = GameTheme.yellow, strokeWidth = 3.dp)
                Text("Connecting…")
            }
        }
        message?.let { Text(it, color = GameTheme.yellow) }

        Panel {
            SectionTitle("Rooms nearby")
            if (rooms.isEmpty()) {
                Text("Looking for games on this Wi-Fi…", color = GameTheme.mutedText)
            }
            rooms.forEachIndexed { i, room ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GameTheme.cardColors[i % 4].copy(alpha = 0.85f))
                        .clickable(enabled = !busy) { codeFor = room }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(clashName(room.hostName), fontWeight = FontWeight.Bold)
                    }
                    Text("Enter code ›", fontWeight = FontWeight.Bold)
                }
            }
        }

        BigButton("📷  Scan the host's QR", GameTheme.green, onScan, enabled = !busy)

        Panel {
            SectionTitle("Room code")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text("e.g. K7MQ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer12()
                BigButton("Join", GameTheme.blue, { onJoinCode(code) }, Modifier.width(96.dp), enabled = !busy && JoinLink.isRoomCode(code))
            }
            Text("On the host's hotspot? The code is all you need.", color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        }

        TextButton(onClick = { showManual = !showManual }) {
            Text(if (showManual) "Hide manual connection" else "Type the host's IP instead", color = GameTheme.yellow)
        }
        if (showManual) {
            Panel {
                SectionTitle("Manual connection")
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it.trim() }, label = { Text("Host IP, e.g. 192.168.1.20") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer12()
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                        label = { Text("Room code") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
                val portNumber = port.toIntOrNull()
                BigButton(
                    "Connect", GameTheme.blue,
                    { onJoinAddress(ip, portNumber ?: DEFAULT_PORT, code) },
                    enabled = !busy && JoinLink.isIpv4(ip) && JoinLink.isRoomCode(code) && portNumber in 1..65535,
                )
            }
        }
    }

    codeFor?.let { room ->
        RoomCodeDialog(
            room,
            onJoin = { typed ->
                codeFor = null
                onPickRoom(room, typed)
            },
            onDismiss = { codeFor = null },
        )
    }
}

/** A nearby room still needs its code, read off the host's screen. */
@Composable
private fun RoomCodeDialog(room: FoundRoom, onJoin: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(clashName(room.hostName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the room code shown on ${room.hostName}'s phone.", color = GameTheme.mutedText)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text("Room code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onJoin(typed) }, enabled = JoinLink.isRoomCode(typed)) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
