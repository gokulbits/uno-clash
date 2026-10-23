package dev.colorclash.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.colorclash.app.data.FeedbackSettings
import dev.colorclash.app.data.Profile
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme

@Composable
fun SettingsScreen(
    profile: Profile,
    onProfileChange: (Profile) -> Unit,
    feedback: FeedbackSettings,
    onFeedbackChange: (FeedbackSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = GameTheme.text) }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Panel {
            SectionTitle("You")
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onProfileChange(profile.copy(name = it.take(16))) },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(GameTheme.avatars) { i, _ ->
                    AvatarBadge(
                        index = i, size = 52.dp,
                        ring = if (i == profile.avatar) Color.White else null,
                        modifier = Modifier.clickable { onProfileChange(profile.copy(avatar = i)) },
                    )
                }
            }
            Text("Name and avatar changes apply the next time you join a room.", color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        }

        Panel {
            SectionTitle("Sound and feel")
            FeedbackToggles(feedback, onFeedbackChange)
        }

        Panel {
            SectionTitle("About")
            Text(GAME_TITLE, fontWeight = FontWeight.Bold)
            Text("Version ${appVersion()}", color = GameTheme.mutedText)
            Text("Sounds and music are made for this game and free to reuse (CC0). See ASSETS.md.", color = GameTheme.mutedText)
        }
    }
}

@Composable
fun FeedbackToggles(feedback: FeedbackSettings, onChange: (FeedbackSettings) -> Unit) {
    Toggle("Sound effects", feedback.sound) { onChange(feedback.copy(sound = it)) }
    Toggle("Music", feedback.music) { onChange(feedback.copy(music = it)) }
    Toggle("Vibration", feedback.haptics) { onChange(feedback.copy(haptics = it)) }
}

/** The quick version for the game screen: just the switches, no leaving the table. */
@Composable
fun QuickSettingsDialog(feedback: FeedbackSettings, onChange: (FeedbackSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { FeedbackToggles(feedback, onChange) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = GameTheme.green))
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
