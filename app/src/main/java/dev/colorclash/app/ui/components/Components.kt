package dev.colorclash.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.ui.theme.LocalTableStyle

/** The title, one little tilted card per letter, cycling through the card colors. */
@Composable
fun Logo(modifier: Modifier = Modifier, letterSize: Dp = 34.dp) {
    val words = GAME_TITLE.split(" ")
    var colorIndex = 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (word in words) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                word.forEachIndexed { i, letter ->
                    val color = GameTheme.cardColors[colorIndex++ % GameTheme.cardColors.size]
                    Box(
                        Modifier
                            .rotate(if (i % 2 == 0) -6f else 6f)
                            .size(letterSize, letterSize * 1.3f)
                            .shadow(6.dp, GameTheme.tagShape)
                            .clip(GameTheme.tagShape)
                            .background(color)
                            .border(2.dp, GameTheme.text, GameTheme.tagShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // White with a dark edge, like the corner marks on the cards, so it holds up on yellow.
                        Text(
                            letter.uppercase(),
                            color = GameTheme.text,
                            fontWeight = FontWeight.Black,
                            fontSize = (letterSize.value * 0.7f).sp,
                            fontFamily = GameTheme.displayFont,
                            style = TextStyle(shadow = Shadow(GameTheme.shadow.copy(alpha = 0.55f), Offset(0f, 3f), blurRadius = 4f)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a button means, which is what decides its color. PRIMARY moves things forward, ACTION is
 * another way on, HIGHLIGHT is the one thing worth a look, DANGER costs something, QUIET backs out.
 */
enum class ButtonTone { PRIMARY, ACTION, HIGHLIGHT, DANGER, QUIET }

@Composable
private fun ButtonTone.fill(): Color = when (this) {
    ButtonTone.PRIMARY -> GameTheme.green
    ButtonTone.ACTION -> GameTheme.blue
    ButtonTone.HIGHLIGHT -> GameTheme.highlight
    ButtonTone.DANGER -> GameTheme.danger
    ButtonTone.QUIET -> quietFill()
}

/** Quiet buttons are a lighter shade of the table, so they belong on every table color. */
@Composable
private fun quietFill(): Color = lerp(LocalTableStyle.current.top, GameTheme.text, 0.2f)

@Composable
private fun disabledFill(): Color = lerp(LocalTableStyle.current.top, GameTheme.shadow, 0.3f)

/**
 * The look every button shares, in the manner of today's big mobile games: a thin outline in a deeper
 * shade of its own color, a small lip underneath, a saturated face with a crisp line of light along
 * its top, and a soft sheen fading out by the middle. [sink] from 0 to 1 presses the face down onto
 * the lip. Read while drawing, so a press redraws without recomposing.
 */
private fun Modifier.chunky(fill: Color, corner: Dp, lip: Dp, sink: State<Float>): Modifier = drawWithCache {
    val edge = BUTTON_OUTLINE.toPx()
    val lipPx = lip.toPx()
    val rimPx = BUTTON_RIM.toPx()
    val faceSize = Size(size.width - edge * 2, size.height - edge * 2 - lipPx)
    val inner = CornerRadius((corner.toPx() - edge).coerceAtLeast(0f))
    val rim = lerp(fill, GameTheme.text, 0.6f)
    val face = Brush.verticalGradient(
        listOf(lerp(fill, GameTheme.text, 0.16f), fill, lerp(fill, GameTheme.shadow, 0.08f)),
        startY = rimPx,
        endY = faceSize.height,
    )
    val sheen = Brush.verticalGradient(
        listOf(GameTheme.text.copy(alpha = 0.2f), GameTheme.text.copy(alpha = 0f)),
        startY = rimPx,
        endY = faceSize.height * 0.55f,
    )
    val lipColor = lerp(fill, GameTheme.shadow, 0.3f)
    val outline = lerp(fill, GameTheme.shadow, 0.45f)
    onDrawBehind {
        val down = lipPx * sink.value.coerceIn(0f, 1f)
        drawRoundRect(outline, cornerRadius = CornerRadius(corner.toPx()))
        drawRoundRect(lipColor, Offset(edge, edge), Size(size.width - edge * 2, size.height - edge * 2), inner)
        translate(edge, edge + down) {
            // The rim is the face's own shape showing above it.
            drawRoundRect(rim, size = faceSize, cornerRadius = inner)
            translate(0f, rimPx) {
                val below = Size(faceSize.width, faceSize.height - rimPx)
                drawRoundRect(face, size = below, cornerRadius = inner)
                drawRoundRect(sheen, size = below, cornerRadius = inner)
            }
        }
    }
}

/** Pressed: down onto the lip, with a little squash and bounce on the way back. */
@Composable
private fun rememberSink(interaction: MutableInteractionSource, enabled: Boolean): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        if (pressed && enabled) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
}

private fun Modifier.squash(sink: State<Float>): Modifier = graphicsLayer {
    val press = 1f - 0.03f * sink.value.coerceIn(0f, 1f)
    scaleX = press
    scaleY = press
}

/** A button's words. White ones get the faintest soft shadow to lift them off the face; dark ones need none. */
@Composable
fun ButtonLabel(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge, fill: Color = GameTheme.text) {
    val lift = with(LocalDensity.current) { 1.dp.toPx() }
    val shadow = if (fill == GameTheme.text) Shadow(GameTheme.shadow.copy(alpha = 0.25f), Offset(0f, lift), blurRadius = lift * 2) else Shadow.None
    Text(text, modifier, style = style.copy(color = fill, shadow = shadow), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ArcadeButton(
    text: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    height: Dp,
    style: TextStyle,
    modifier: Modifier,
    enabled: Boolean,
    icon: (DrawScope.(Color) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val sink = rememberSink(interaction, enabled)
    val fill = if (enabled) tone.fill() else disabledFill()
    val tint = if (enabled) GameTheme.contentOn(fill) else GameTheme.mutedText
    Box(
        modifier
            .height(height)
            .squash(sink)
            .chunky(fill, BUTTON_CORNER, BUTTON_LIP, sink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        propagateMinConstraints = true,
    ) {
        Row(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = BUTTON_OUTLINE, bottom = BUTTON_OUTLINE + BUTTON_LIP)
                .graphicsLayer { translationY = BUTTON_LIP.toPx() * sink.value.coerceIn(0f, 1f) },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Canvas(Modifier.size(20.dp)) { icon(tint) }
            ButtonLabel(text, style = style, fill = tint)
        }
    }
}

/** Takes the whole width it's given. The main choices on a screen. */
@Composable
fun BigButton(
    text: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (DrawScope.(Color) -> Unit)? = null,
) = ArcadeButton(text, tone, onClick, 58.dp, MaterialTheme.typography.labelLarge, modifier.fillMaxWidth(), enabled, icon)

/** Only as wide as its label. For acting in the moment, like at the table. */
@Composable
fun SmallButton(
    text: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (DrawScope.(Color) -> Unit)? = null,
) = ArcadeButton(text, tone, onClick, 46.dp, MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), modifier, enabled, icon)

/** A round button with a drawn icon. [label] is what a screen reader says. */
@Composable
fun RoundIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ROUND_BUTTON_SIZE,
    enabled: Boolean = true,
    selected: Boolean = false,
    icon: DrawScope.(Color) -> Unit,
) = RoundButton(label, onClick, modifier, size, enabled, selected) { tint ->
    Canvas(Modifier.size(size * 0.46f)) { icon(tint) }
}

/** The same button with anything inside it. */
@Composable
fun RoundButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ROUND_BUTTON_SIZE,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable (tint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val sink = rememberSink(interaction, enabled)
    val lip = size * 0.07f
    val fill = when {
        !enabled -> disabledFill()
        selected -> GameTheme.highlight
        else -> quietFill()
    }
    Box(
        modifier
            .size(size)
            .squash(sink)
            .chunky(fill, size / 2, lip, sink)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(bottom = lip)
                .graphicsLayer { translationY = lip.toPx() * sink.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            content(if (enabled) GameTheme.contentOn(fill) else GameTheme.mutedText)
        }
    }
}

/** Every screen past the home screen starts with this. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    icon: DrawScope.(Color) -> Unit = { backIcon(it) },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIconButton(backLabel, onBack, icon = icon)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(GameTheme.panelShape)
            .background(GameTheme.panel)
            .border(1.dp, GameTheme.glassEdge, GameTheme.panelShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Anything that floats over the screen: dialogs, pickers, the scoreboard. Takes the table's color. */
@Composable
fun SheetSurface(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(GameTheme.sheetShape)
            .background(LocalTableStyle.current.top)
            .border(1.dp, GameTheme.glassEdge, GameTheme.sheetShape),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (onClose != null) {
            RoundIconButton("Close", onClose, Modifier.align(Alignment.TopEnd).padding(10.dp), size = 38.dp) { closeIcon(it) }
        }
    }
}

/** A question in its own window. Pass the choices as [actions], each with `Modifier.weight(1f)`. */
@Composable
fun GameDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetSurface {
            SheetTitle(title)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
        }
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = GameTheme.mutedText)
}

/** Supporting text under or beside a control. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Unspecified) {
    Text(text, modifier, color = GameTheme.mutedText, style = MaterialTheme.typography.bodySmall, textAlign = textAlign)
}

/** A row in a list you might tap, like a room or a player to swap with. */
@Composable
fun ListRow(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(GameTheme.tileShape)
            .background(GameTheme.panelStrong)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A short label on a colored chip. */
@Composable
fun Tag(text: String, fill: Color, modifier: Modifier = Modifier, color: Color = GameTheme.contentOn(fill), style: TextStyle = MaterialTheme.typography.labelSmall) {
    Text(
        text,
        modifier
            .clip(GameTheme.tagShape)
            .background(fill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Something on or off in a list of settings. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GameTheme.ink,
                checkedTrackColor = GameTheme.highlight,
                uncheckedThumbColor = GameTheme.mutedText,
                uncheckedTrackColor = GameTheme.panel,
                uncheckedBorderColor = GameTheme.glassEdge,
            ),
        )
    }
}

/** A pill that's picked or not. */
@Composable
fun ChoicePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (selected) Modifier.chunky(GameTheme.highlight, 20.dp, PICKED_LIP, NOT_PRESSED) else Modifier.clip(GameTheme.pillShape).background(GameTheme.panelStrong))
            .clip(GameTheme.pillShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp + PICKED_LIP),
        contentAlignment = Alignment.Center,
    ) {
        PickLabel(text, selected)
    }
}

@Composable
private fun PickLabel(text: String, selected: Boolean) {
    // Dark on the gold when picked: at this size an outline just smudges.
    Text(
        text,
        color = if (selected) GameTheme.ink else GameTheme.mutedText,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

/** One of a few options, side by side in a track. */
@Composable
fun <T> SegmentedPicker(options: List<T>, selected: T, enabled: Boolean, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    val lockedPick = quietFill()
    Row(
        modifier
            .clip(GameTheme.pillShape)
            .background(GameTheme.panel)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val picked = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .then(if (picked) Modifier.chunky(if (enabled) GameTheme.highlight else lockedPick, 18.dp, PICKED_LIP, NOT_PRESSED) else Modifier)
                    .clip(GameTheme.pillShape)
                    .clickable(enabled = enabled && !picked, role = Role.RadioButton) { onPick(option) }
                    .padding(top = 7.dp, bottom = 7.dp + PICKED_LIP),
                contentAlignment = Alignment.Center,
            ) {
                PickLabel(label(option), picked)
            }
        }
    }
}

/** Text that does something. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(GameTheme.tagShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        color = GameTheme.highlight,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** Every text box in the game. */
@Composable
fun GameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier,
        shape = GameTheme.tileShape,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(letterSpacing = letterSpacing),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = GameTheme.text,
            unfocusedTextColor = GameTheme.text,
            focusedContainerColor = GameTheme.panel,
            unfocusedContainerColor = GameTheme.panel,
            focusedBorderColor = GameTheme.highlight,
            unfocusedBorderColor = GameTheme.glassEdge,
            focusedLabelColor = GameTheme.highlight,
            unfocusedLabelColor = GameTheme.mutedText,
            focusedPlaceholderColor = GameTheme.mutedText,
            unfocusedPlaceholderColor = GameTheme.mutedText,
            cursorColor = GameTheme.highlight,
        ),
    )
}

/** Takes what a room code can be and nothing else. */
@Composable
fun RoomCodeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    GameTextField(
        value = value,
        onValueChange = { onValueChange(it.uppercase().filter(Char::isLetterOrDigit).take(MAX_ROOM_CODE)) },
        label = "Room code",
        modifier = modifier,
        placeholder = "K7MQ",
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        letterSpacing = 2.sp,
    )
}

/** Why something didn't work, or what's needed before it will. */
@Composable
fun NoticeBar(text: String, modifier: Modifier = Modifier, error: Boolean = true) {
    val tone = if (error) GameTheme.danger else GameTheme.highlight
    Text(
        text,
        modifier
            .fillMaxWidth()
            .clip(GameTheme.tileShape)
            .background(tone.copy(alpha = 0.18f))
            .border(1.dp, tone.copy(alpha = 0.6f), GameTheme.tileShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/** Waiting on something that isn't a screen of its own. */
@Composable
fun BusyRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Spinner()
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    CircularProgressIndicator(modifier.size(size), color = GameTheme.highlight, strokeWidth = size * 0.14f, trackColor = GameTheme.panel)
}

/** A passing message over the table, or the connection trouble banner. */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, fill: Color = GameTheme.scrim, busy: Boolean = false) {
    Row(
        modifier
            .padding(horizontal = GameTheme.screenPadding)
            .clip(GameTheme.pillShape)
            .background(fill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = GameTheme.text, strokeWidth = 2.dp, trackColor = Color.Transparent)
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Room code as a row of colored tiles, easy to read out loud across a table. */
@Composable
fun RoomCodeTiles(code: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        code.forEachIndexed { i, c ->
            val fill = GameTheme.cardColors[i % GameTheme.cardColors.size]
            Box(
                Modifier
                    .size(46.dp, 56.dp)
                    .clip(GameTheme.tileShape)
                    .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.toString(),
                    color = GameTheme.contentOn(fill),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = GameTheme.displayFont,
                )
            }
        }
    }
}

private val BUTTON_OUTLINE = 1.5.dp
private val BUTTON_RIM = 1.5.dp
private val BUTTON_LIP = 4.dp
private val BUTTON_CORNER = 18.dp
private val PICKED_LIP = 3.dp
private val NOT_PRESSED: State<Float> = mutableFloatStateOf(0f)
private val ROUND_BUTTON_SIZE = 46.dp
private const val MAX_ROOM_CODE = 6
