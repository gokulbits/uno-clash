package dev.colorclash.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.colorclash.app.data.FeedbackSettings
import dev.colorclash.app.data.LookSettings
import dev.colorclash.app.data.PlaySettings
import dev.colorclash.app.data.Profile
import dev.colorclash.app.data.Stats
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.GameDialog
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.ScreenHeader
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.SwitchRow
import dev.colorclash.app.ui.components.TextLink
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.update.installedVersion

@Composable
fun SettingsScreen(
    profile: Profile,
    onProfileChange: (Profile) -> Unit,
    feedback: FeedbackSettings,
    onFeedbackChange: (FeedbackSettings) -> Unit,
    play: PlaySettings,
    onPlayChange: (PlaySettings) -> Unit,
    looks: LookSettings,
    onLooksChange: (LookSettings) -> Unit,
    stats: Stats,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(GameTheme.sectionGap),
    ) {
        ScreenHeader("Settings", onBack)

        Panel {
            SectionTitle("You")
            ProfileEditor(profile, onProfileChange)
            HintText("Name and avatar changes apply the next time you join a room.")
        }

        Panel {
            SectionTitle("Table and cards")
            LooksPicker(looks, onLooksChange)
        }

        Panel {
            SectionTitle("Sound and feel")
            FeedbackToggles(feedback, onFeedbackChange)
        }

        Panel {
            SectionTitle("Playing")
            PlayToggles(play, onPlayChange)
        }

        Panel {
            SectionTitle("Your stats")
            StatsSummary(stats)
        }

        Panel {
            SectionTitle("About")
            val context = LocalContext.current
            val version = remember { installedVersion(context) ?: "?" }
            Text("$GAME_TITLE $version", style = MaterialTheme.typography.titleSmall)
            val links = LocalUriHandler.current
            TextLink("Made by gokulbits", { runCatching { links.openUri(AUTHOR_URL) } })
            HintText("Sounds and music are made for this game and free to reuse (CC0). See ASSETS.md.")
        }
    }
}

@Composable
fun FeedbackToggles(feedback: FeedbackSettings, onChange: (FeedbackSettings) -> Unit) {
    SwitchRow("Sound effects", feedback.sound) { onChange(feedback.copy(sound = it)) }
    SwitchRow("Music", feedback.music) { onChange(feedback.copy(music = it)) }
    SwitchRow("Vibration", feedback.haptics) { onChange(feedback.copy(haptics = it)) }
    SwitchRow("Announce cards out loud", feedback.voice) { onChange(feedback.copy(voice = it)) }
}

@Composable
fun PlayToggles(play: PlaySettings, onChange: (PlaySettings) -> Unit) {
    SwitchRow("Tap a card twice to play it", play.tapToSelect) { onChange(play.copy(tapToSelect = it)) }
    SwitchRow("Shapes for each color (colorblind)", play.colorShapes) { onChange(play.copy(colorShapes = it)) }
}

@Composable
private fun StatsSummary(stats: Stats) {
    val winRate = if (stats.gamesPlayed == 0) "–" else "${stats.gamesWon * 100 / stats.gamesPlayed}%"
    listOf(
        "Games played" to "${stats.gamesPlayed}",
        "Games won" to "${stats.gamesWon} ($winRate)",
        "Rounds won" to "${stats.roundsWon}",
        "Best round" to "${stats.bestRound} pts",
    ).forEach { (label, value) ->
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** The quick version for the game screen: just the switches, no leaving the table. */
@Composable
fun QuickSettingsDialog(
    feedback: FeedbackSettings,
    onChange: (FeedbackSettings) -> Unit,
    play: PlaySettings,
    onPlayChange: (PlaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    GameDialog(
        title = "Settings",
        onDismiss = onDismiss,
        actions = { BigButton("Done", ButtonTone.PRIMARY, onDismiss) },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FeedbackToggles(feedback, onChange)
            PlayToggles(play, onPlayChange)
        }
    }
}

private const val AUTHOR_URL = "https://github.com/gokulbits"
