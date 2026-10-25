package dev.colorclash.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.colorclash.app.data.Profile
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.FrostedPill
import dev.colorclash.app.ui.components.Logo
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.gearIcon
import dev.colorclash.app.ui.settings.ProfileEditor
import dev.colorclash.app.ui.theme.GameTheme

@Composable
fun HomeScreen(
    profile: Profile,
    onProfileChange: (Profile) -> Unit,
    rejoin: SavedSession?,
    busy: Boolean,
    message: String?,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onPractice: () -> Unit,
    onRejoin: (SavedSession) -> Unit,
    onSettings: () -> Unit,
) {
    val nameOk = profile.name.isNotBlank()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FrostedPill("Settings", onSettings, Modifier.align(Alignment.End)) { gearIcon() }
        Logo()
        Spacer(Modifier.height(12.dp))

        Panel {
            SectionTitle("Who's playing?")
            ProfileEditor(profile, onProfileChange)
        }

        if (!nameOk) {
            Text("Pick a name to start", color = GameTheme.mutedText, style = MaterialTheme.typography.bodyMedium)
        }

        rejoin?.let {
            BigButton("Rejoin game ${it.roomCode}", GameTheme.accent, { onRejoin(it) }, enabled = nameOk && !busy)
        }
        BigButton("Host a game", GameTheme.red, onHost, enabled = nameOk && !busy)
        BigButton("Join a game", GameTheme.blue, onJoin, enabled = nameOk && !busy)
        BigButton("Practice vs bots", GameTheme.green, onPractice, enabled = nameOk && !busy)

        message?.let {
            Text(it, color = GameTheme.yellow, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
