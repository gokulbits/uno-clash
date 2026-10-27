package dev.colorclash.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.colorclash.app.update.Release
import dev.colorclash.app.update.UpdateState
import dev.colorclash.app.update.installedVersion
import dev.colorclash.app.update.sizeLabel
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme

/** Offered at launch when GitHub has a newer build: update now, or carry on with this one. */
@Composable
fun UpdateDialog(state: UpdateState, onUpdate: (Release) -> Unit, onDismiss: () -> Unit) {
    val installed = installedVersion(LocalContext.current)
    when (state) {
        is UpdateState.Idle -> Unit

        is UpdateState.Available -> UpdateSheet(
            title = "Update available",
            onDismiss = onDismiss,
            confirm = "Update" to { onUpdate(state.release) },
            dismiss = "Keep playing",
        ) {
            Text("$GAME_TITLE ${state.release.version} is out." + (installed?.let { " You're on $it." } ?: ""))
            if (state.release.notes.isNotEmpty()) {
                Text(state.release.notes, color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
            }
            val size = sizeLabel(state.release.apkBytes)
            if (size.isNotEmpty()) {
                Text("Downloads $size over Wi-Fi or data.", color = GameTheme.mutedText, style = MaterialTheme.typography.bodySmall)
            }
        }

        is UpdateState.Downloading -> UpdateSheet(
            title = "Downloading ${state.release.version}",
            onDismiss = onDismiss,
            dismiss = "Cancel",
        ) {
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${state.percent}%", color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateState.Installing -> UpdateSheet(
            title = "Ready to install",
            onDismiss = onDismiss,
            dismiss = "Close",
        ) {
            Text("Android will ask you to confirm the install.", color = GameTheme.mutedText)
        }

        is UpdateState.Failed -> UpdateSheet(
            title = "Update failed",
            onDismiss = onDismiss,
            confirm = "Try again" to { onUpdate(state.release) },
            dismiss = "Keep playing",
        ) {
            Text(state.message, color = GameTheme.mutedText)
        }
    }
}

@Composable
private fun UpdateSheet(
    title: String,
    onDismiss: () -> Unit,
    dismiss: String,
    confirm: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = {
            confirm?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}
