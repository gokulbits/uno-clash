package dev.colorclash.app.ui.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.GameDialog
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.update.Release
import dev.colorclash.app.update.UpdateState
import dev.colorclash.app.update.installedVersion
import dev.colorclash.app.update.sizeLabel

/** Offered at launch when GitHub has a newer build: update now, or carry on with this one. */
@Composable
fun UpdateDialog(state: UpdateState, onUpdate: (Release) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        is UpdateState.Idle -> Unit

        is UpdateState.Available -> {
            val context = LocalContext.current
            // Asks the package manager, so once rather than every time this recomposes.
            val installed = remember { installedVersion(context) }
            UpdateSheet(
                title = "Update available",
                onDismiss = onDismiss,
                confirm = "Update" to { onUpdate(state.release) },
                dismiss = "Not now",
            ) {
                Text(
                    "$GAME_TITLE ${state.release.version} is out." + (installed?.let { " You're on $it." } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (state.release.notes.isNotEmpty()) {
                    Text(state.release.notes, Modifier.fillMaxWidth(), color = GameTheme.mutedText, style = MaterialTheme.typography.bodySmall)
                }
                val size = sizeLabel(state.release.apkBytes)
                if (size.isNotEmpty()) HintText("Downloads $size over Wi-Fi or data.", textAlign = TextAlign.Center)
            }
        }

        is UpdateState.Downloading -> UpdateSheet(
            title = "Downloading ${state.release.version}",
            onDismiss = onDismiss,
            dismiss = "Cancel",
        ) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = GameTheme.highlight,
                trackColor = GameTheme.panelStrong,
            )
            HintText("${state.percent}%")
        }

        is UpdateState.Installing -> UpdateSheet(
            title = "Ready to install",
            onDismiss = onDismiss,
            dismiss = "Close",
        ) {
            HintText("Android will ask you to confirm the install.", textAlign = TextAlign.Center)
        }

        is UpdateState.Failed -> UpdateSheet(
            title = "Update failed",
            onDismiss = onDismiss,
            confirm = "Try again" to { onUpdate(state.release) },
            dismiss = "Not now",
        ) {
            HintText(state.message, textAlign = TextAlign.Center)
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
    GameDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            BigButton(dismiss, ButtonTone.QUIET, onDismiss, Modifier.weight(1f))
            confirm?.let { (label, action) -> BigButton(label, ButtonTone.PRIMARY, action, Modifier.weight(1f)) }
        },
    ) {
        content()
    }
}
