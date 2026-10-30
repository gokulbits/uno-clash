package dev.colorclash.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.colorclash.app.data.Profile
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.BusyRow
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.RoundIconButton
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.Logo
import dev.colorclash.app.ui.components.NoticeBar
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.gearIcon
import dev.colorclash.app.ui.components.robotIcon
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
            .padding(horizontal = GameTheme.screenPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameTheme.sectionGap),
    ) {
        RoundIconButton("Settings", onSettings, Modifier.align(Alignment.End)) { gearIcon(it) }
        Logo()

        Panel(Modifier.padding(top = 4.dp)) {
            SectionTitle("Who's playing?")
            ProfileEditor(profile, onProfileChange)
            if (!nameOk) HintText("Pick a name to start")
        }

        // Above the buttons, where it's seen without scrolling.
        message?.let { NoticeBar(it) }
        if (busy) BusyRow("Getting the room ready…")

        rejoin?.let {
            BigButton("Rejoin game ${it.roomCode}", ButtonTone.HIGHLIGHT, { onRejoin(it) }, enabled = nameOk && !busy)
        }
        BigButton("Host a game", ButtonTone.PRIMARY, onHost, enabled = nameOk && !busy)
        BigButton("Join a game", ButtonTone.ACTION, onJoin, enabled = nameOk && !busy)
        BigButton("Practice vs bots", ButtonTone.QUIET, onPractice, enabled = nameOk && !busy, icon = { robotIcon(it) })
    }
}
