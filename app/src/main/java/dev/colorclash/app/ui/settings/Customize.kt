package dev.colorclash.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.colorclash.app.data.LookSettings
import dev.colorclash.app.data.Profile
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.ButtonTone
import dev.colorclash.app.ui.components.ChoicePill
import dev.colorclash.app.ui.components.GameTextField
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.SheetSurface
import dev.colorclash.app.ui.components.SheetTitle
import dev.colorclash.app.ui.components.SmallButton
import dev.colorclash.app.ui.components.checkIcon
import dev.colorclash.app.ui.components.rememberPhoto
import dev.colorclash.app.ui.game.CardBack
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.AUTO_AVATAR_COLOR
import dev.colorclash.protocol.clipped

/** Name and avatar, with the way into the avatar picker. */
@Composable
fun ProfileEditor(profile: Profile, onProfileChange: (Profile) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = "Change avatar", role = Role.Button) { picking = true },
        ) {
            AvatarBadge(profile.avatar, size = 68.dp, color = profile.avatarColor, frame = profile.avatarFrame, photo = rememberPhoto(profile.photo))
        }
        GameTextField(
            value = profile.name,
            onValueChange = { onProfileChange(profile.copy(name = it.clipped())) },
            label = "Your name",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
    }
    SmallButton("Change avatar, color and frame", ButtonTone.QUIET, { picking = true }, Modifier.fillMaxWidth())
    if (picking) AvatarStudio(profile, onProfileChange, onDismiss = { picking = false })
}

/** Pick an avatar or photo, then a color and frame. */
@Composable
fun AvatarStudio(profile: Profile, onChange: (Profile) -> Unit, onDismiss: () -> Unit) {
    val groups = GameTheme.avatarGroups
    val starts = remember { groups.runningFold(0) { at, group -> at + group.avatars.size } }
    val chosen = profile.avatar.mod(GameTheme.avatars.size)
    val chosenGroup = groups.indices.last { starts[it] <= chosen }
    // Tab 0 is your own photo; the avatar groups follow it.
    var tab by remember { mutableIntStateOf(if (profile.photo != null) 0 else chosenGroup + 1) }
    val art = GameTheme.avatar(chosen)
    val photo = rememberPhoto(profile.photo)
    // "Malayalam · Aadu", or just "Malayalam" when the film has the character's name.
    val title = if (photo != null) "Your photo" else art.name
    val subtitle = if (photo != null) "Photo" else listOfNotNull(groups[chosenGroup].title, art.from?.takeIf { it != art.name }).joinToString(" · ")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetSurface {
            AvatarBadge(chosen, size = 104.dp, color = profile.avatarColor, frame = profile.avatarFrame, photo = photo)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SheetTitle(title)
                HintText(subtitle, textAlign = TextAlign.Center)
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("Photo") + groups.map { it.title }).forEachIndexed { i, name -> ChoicePill(name, selected = i == tab) { tab = i } }
            }
            if (tab == 0) {
                PhotoTab(profile, onChange)
            } else {
                AvatarGrid(
                    first = starts[tab - 1],
                    count = groups[tab - 1].avatars.size,
                    rows = (groups.maxOf { it.avatars.size } + PER_ROW - 1) / PER_ROW,
                    profile = profile,
                    // Picking an avatar switches off the photo.
                    onPick = { onChange(profile.copy(avatar = it, photo = null)) },
                )
            }

            // A photo covers the whole circle, so the color only matters for avatars.
            if (photo == null) {
                Label("Color")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatch(art.color, selected = profile.avatarColor < 0, label = "Auto") { onChange(profile.copy(avatarColor = AUTO_AVATAR_COLOR)) }
                    GameTheme.avatarColors.forEachIndexed { i, color ->
                        Swatch(color, selected = profile.avatarColor == i) { onChange(profile.copy(avatarColor = i)) }
                    }
                }
            }

            Label("Frame")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameTheme.avatarFrames.forEachIndexed { i, name ->
                    Choice(name, selected = GameTheme.avatarFrame(profile.avatarFrame) == i, onClick = { onChange(profile.copy(avatarFrame = i)) }) {
                        AvatarBadge(chosen, size = 56.dp, color = profile.avatarColor, frame = i, photo = photo)
                    }
                }
            }

            HintText("Everyone at the table sees your avatar or photo, and its frame.", textAlign = TextAlign.Center)
            BigButton("Done", ButtonTone.PRIMARY, onDismiss)
        }
    }
}

/** One tab's avatars, always [rows] tall so switching tabs doesn't resize the picker. */
@Composable
private fun AvatarGrid(first: Int, count: Int, rows: Int, profile: Profile, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val cell = ((maxWidth - gap * (PER_ROW - 1)) / PER_ROW).coerceAtMost(60.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until PER_ROW) {
                        val i = row * PER_ROW + col
                        if (i >= count) {
                            Spacer(Modifier.size(cell))
                            continue
                        }
                        val index = first + i
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClickLabel = GameTheme.avatar(index).name, role = Role.Button) { onPick(index) },
                        ) {
                            AvatarBadge(
                                index,
                                size = cell,
                                color = profile.avatarColor,
                                ring = if (profile.photo == null && index == profile.avatar.mod(GameTheme.avatars.size)) GameTheme.highlight else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Only this phone sees these. */
@Composable
fun LooksPicker(looks: LookSettings, onChange: (LookSettings) -> Unit) {
    Label("Table")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameTheme.tables.forEachIndexed { i, table ->
            Choice(table.name, selected = looks.table.mod(GameTheme.tables.size) == i, onClick = { onChange(looks.copy(table = i)) }) {
                Box(
                    Modifier
                        .size(60.dp, 42.dp)
                        .clip(GameTheme.tagShape)
                        .background(table.brush)
                        .border(1.dp, GameTheme.glassEdge, GameTheme.tagShape),
                )
            }
        }
    }
    Label("Card backs")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameTheme.cardBacks.forEachIndexed { i, back ->
            Choice(back.name, selected = looks.cardBack.mod(GameTheme.cardBacks.size) == i, onClick = { onChange(looks.copy(cardBack = i)) }) {
                CardBack(width = 50.dp, style = back)
            }
        }
    }
    HintText("Only you see these.")
}

/** Something to pick, with its name under it. */
@Composable
private fun Choice(name: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(GameTheme.tileShape)
            .then(
                if (selected) {
                    Modifier.background(GameTheme.panelStrong).border(2.dp, GameTheme.highlight, GameTheme.tileShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClickLabel = name, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
        Text(
            name,
            color = if (selected) GameTheme.text else GameTheme.mutedText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** A color to pick. The ring stays white because it has to show on the yellow swatches too. */
@Composable
private fun Swatch(color: Color, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    val content = GameTheme.contentOn(color)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) GameTheme.text else GameTheme.glassEdge, CircleShape)
            .clickable(onClickLabel = label ?: "Color", role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null -> Text(label, color = content, style = MaterialTheme.typography.labelSmall)
            selected -> Canvas(Modifier.size(18.dp)) { checkIcon(content) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
}

private const val PER_ROW = 5
