package dev.colorclash.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.colorclash.app.data.Profile
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.Logo
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
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
) {
    val nameOk = profile.name.isNotBlank()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Logo()
        Spacer(Modifier.height(12.dp))

        Panel {
            SectionTitle("Who's playing?")
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
                        index = i,
                        size = 52.dp,
                        ring = if (i == profile.avatar) Color.White else null,
                        modifier = Modifier.clickable { onProfileChange(profile.copy(avatar = i)) },
                    )
                }
            }
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
