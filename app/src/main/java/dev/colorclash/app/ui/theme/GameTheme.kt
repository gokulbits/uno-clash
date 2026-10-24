package dev.colorclash.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.colorclash.rules.CardColor

const val GAME_TITLE = "Color Clash"

/** Every color, font and avatar the game draws with. Change this one object to reskin the game. */
object GameTheme {
    val red = Color(0xFFFF3B5C)
    val yellow = Color(0xFFFFC61A)
    val green = Color(0xFF1FD15C)
    val blue = Color(0xFF3B5BFF)
    val wild = Color(0xFF1C1530)

    val tableTop = Color(0xFF3A1F8C)
    val tableBottom = Color(0xFF140A33)
    val panel = Color(0x26FFFFFF)
    val panelStrong = Color(0x40FFFFFF)
    val accent = Color(0xFFFF8A00)
    val text = Color.White
    val mutedText = Color(0xB3FFFFFF)
    val danger = Color(0xFFFF4D6D)

    val cardColors = listOf(red, yellow, green, blue)

    // Card stock: the paper frame, the oval, and the back.
    val cardPaper = Color.White
    val cardPaperEdge = Color(0xFFE9EBF2)
    val cardOvalEdge = Color(0xFFE4E7F0)
    val cardBackGlow = Color(0xFF3A2A78)
    val cardBackDeep = Color(0xFF120C2C)
    val cardBackOval = Color(0xFFFF5A74)
    val cardBackOvalDeep = Color(0xFFD0183A)
    val cardBackInk = Color(0xFF5A0016)

    // The UNO buzzer: a glossy red dome on a dark rim.
    val buzzerShine = Color(0xFFFF6B82)
    val buzzerPressed = Color(0xFFC01836)
    val buzzerDeep = Color(0xFF8E0A22)
    val buzzerSide = Color(0xFF7A0A1E)
    val buzzerRim = Color(0xFF4A4166)
    val buzzerRimDeep = Color(0xFF1A1530)

    fun cardColor(color: CardColor?): Color = when (color) {
        CardColor.RED -> red
        CardColor.YELLOW -> yellow
        CardColor.GREEN -> green
        CardColor.BLUE -> blue
        null -> wild
    }

    val tableBrush: Brush get() = Brush.verticalGradient(listOf(tableTop, tableBottom))

    val displayFont: FontFamily = FontFamily.SansSerif
    val bodyFont: FontFamily = FontFamily.SansSerif

    val avatars = listOf("🦊", "🐼", "🐸", "🐙", "🦄", "🐯", "🐵", "🐧", "🐲", "🐝", "🦁", "🐨")
    val avatarBackgrounds = listOf(red, blue, green, yellow, Color(0xFFB04DFF), Color(0xFF00C2D1))

    /** Mood emoji players can send. With [phrases] after them, these are numbered exactly as the protocol's reactions. */
    val reactions = listOf("😂", "😍", "😎", "😮", "😢", "😡", "🤯", "🥳", "👍", "👏", "🔥", "😴")
    /** Quick chat, numbered straight after the emoji. Short enough for a bubble over a seat. */
    val phrases = listOf("Nice one!", "Hurry up!", "Noooo!", "Gotcha 😏", "Oops", "GG", "Watch out!", "So close!")

    fun isPhrase(index: Int): Boolean = index >= reactions.size

    fun reaction(index: Int): String =
        if (isPhrase(index)) phrases[(index - reactions.size).mod(phrases.size)] else reactions[index.mod(reactions.size)]

    fun avatar(index: Int): String = avatars[index.mod(avatars.size)]
    fun avatarBackground(index: Int): Color = avatarBackgrounds[index.mod(avatarBackgrounds.size)]
}

@Composable
fun ColorClashTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = GameTheme.accent,
        onPrimary = Color.White,
        secondary = GameTheme.blue,
        onSecondary = Color.White,
        tertiary = GameTheme.green,
        background = GameTheme.tableBottom,
        onBackground = GameTheme.text,
        surface = Color(0xFF261458),
        onSurface = GameTheme.text,
        surfaceVariant = Color(0xFF32206B),
        onSurfaceVariant = GameTheme.mutedText,
        error = GameTheme.danger,
    )
    val base = Typography()
    val typography = base.copy(
        displayLarge = TextStyle(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.Black, fontSize = 52.sp),
        headlineMedium = TextStyle(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
        titleLarge = base.titleLarge.copy(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = GameTheme.bodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = GameTheme.bodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Most screens draw straight onto the table gradient rather than a Surface, so text
        // without a color of its own would otherwise come out black on purple.
        CompositionLocalProvider(LocalContentColor provides GameTheme.text, content = content)
    }
}
