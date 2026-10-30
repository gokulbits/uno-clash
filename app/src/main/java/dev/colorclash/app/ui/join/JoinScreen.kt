package dev.colorclash.app.ui.join

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.colorclash.app.net.FoundRoom
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.BusyRow
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.GameDialog
import dev.colorclash.app.ui.components.GameTextField
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.ListRow
import dev.colorclash.app.ui.components.NoticeBar
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.RoomCodeField
import dev.colorclash.app.ui.components.ScreenHeader
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.TextLink
import dev.colorclash.app.ui.components.cameraIcon
import dev.colorclash.app.ui.components.chevronIcon
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
            .padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GameTheme.sectionGap),
    ) {
        ScreenHeader("Join a game", onBack)

        if (busy) BusyRow("Connecting…")
        message?.let { NoticeBar(it) }

        Panel {
            SectionTitle("Rooms nearby")
            if (rooms.isEmpty()) {
                BusyRow("Looking for games on this Wi-Fi…")
            }
            rooms.forEach { room ->
                ListRow(onClick = { codeFor = room }, enabled = !busy) {
                    Text(clashName(room.hostName), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HintText("Enter code")
                    Canvas(Modifier.size(14.dp)) { chevronIcon(GameTheme.mutedText) }
                }
            }
        }

        BigButton("Scan the host's QR", ButtonTone.ACTION, onScan, enabled = !busy, icon = { cameraIcon(it) })

        Panel {
            SectionTitle("Got a code?")
            // Lined up with the field's box, not its floating label.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoomCodeField(code, { code = it }, Modifier.weight(1f))
                BigButton("Join", ButtonTone.PRIMARY, { onJoinCode(code) }, Modifier.width(96.dp), enabled = !busy && JoinLink.isRoomCode(code))
            }
            HintText("On the host's hotspot? The code is all you need.")
        }

        TextLink(if (showManual) "Hide manual connection" else "Type the host's IP instead", { showManual = !showManual })
        if (showManual) {
            Panel {
                SectionTitle("Manual connection")
                GameTextField(
                    value = ip,
                    onValueChange = { ip = it.trim() },
                    label = "Host IP",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "192.168.1.20",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = "Port",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    RoomCodeField(code, { code = it }, Modifier.weight(1f))
                }
                val portNumber = port.toIntOrNull()
                BigButton(
                    "Connect",
                    ButtonTone.PRIMARY,
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
    GameDialog(
        title = clashName(room.hostName),
        onDismiss = onDismiss,
        actions = {
            BigButton("Cancel", ButtonTone.QUIET, onDismiss, Modifier.weight(1f))
            BigButton("Join", ButtonTone.PRIMARY, { onJoin(typed) }, Modifier.weight(1f), enabled = JoinLink.isRoomCode(typed))
        },
    ) {
        HintText("Enter the room code shown on ${room.hostName}'s phone.", textAlign = TextAlign.Center)
        RoomCodeField(typed, { typed = it }, Modifier.fillMaxWidth())
    }
}
