package dev.colorclash.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val glassEdge = Color(0x33FFFFFF)
    val text = Color.White
    val mutedText = Color(0xB3FFFFFF)
    val danger = Color(0xFFFF4D6D)
    /** What's selected, switched on, or wants attention. One color for all of it. */
    val highlight = yellow
    /** Text and icons on bright fills, where white would wash out. */
    val ink = wild
    val scrim = Color(0x99000000)
    val shadow = Color.Black
    // Always this way round whatever the skin: scanners need dark on light.
    val qrPaper = Color.White
    val qrInk = Color.Black

    val cardColors = listOf(red, yellow, green, blue)

    // Every rounded thing in the chrome is one of these, smallest to largest.
    val tagShape = RoundedCornerShape(8.dp)
    val tileShape = RoundedCornerShape(14.dp)
    val panelShape = RoundedCornerShape(20.dp)
    val CORNER_SHEET = 28.dp
    val sheetShape = RoundedCornerShape(CORNER_SHEET)
    val pillShape = RoundedCornerShape(50)

    val screenPadding = 20.dp
    val sectionGap = 16.dp

    /** White unless it would fall under 3:1 on [fill], the floor for bold text at this size. */
    fun contentOn(fill: Color): Color = if (contrast(text, fill) >= MIN_CONTRAST) text else ink

    fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    private const val MIN_CONTRAST = 3f

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

    /** Table colors to pick from in settings. The first is the classic one. */
    val tables = listOf(
        TableStyle("Classic", tableTop, tableBottom),
        TableStyle("Felt", Color(0xFF1E7A46), Color(0xFF0A3320)),
        TableStyle("Ocean", Color(0xFF1D4E9E), Color(0xFF0A1838)),
        TableStyle("Ruby", Color(0xFF8E1C3C), Color(0xFF2E0814)),
        TableStyle("Teal", Color(0xFF0F7C7C), Color(0xFF062E33)),
        TableStyle("Midnight", Color(0xFF2C2C3E), Color(0xFF0B0B12)),
    )

    /** Card back designs to pick from in settings. The first is the classic one. */
    val cardBacks = listOf(
        CardBackStyle("Classic", cardBackGlow, cardBackDeep, cardBackOval, cardBackOvalDeep, cardBackInk, yellow, BackPattern.PLAIN),
        CardBackStyle(
            "Midnight", Color(0xFF34344A), Color(0xFF0A0A12), Color(0xFFF5C542), Color(0xFFB8860B), Color(0xFF3D2A00),
            Color(0xFFFFF3C4), BackPattern.DIAMONDS,
        ),
        CardBackStyle(
            "Ocean", Color(0xFF1F6FB2), Color(0xFF08254A), Color(0xFF2EE6D6), Color(0xFF0E9C9C), Color(0xFF003D3D),
            yellow, BackPattern.WAVES,
        ),
        CardBackStyle(
            "Jungle", Color(0xFF2E7D32), Color(0xFF0B2E12), Color(0xFFC6F432), Color(0xFF7CB518), Color(0xFF243B00),
            Color(0xFFFFF59D), BackPattern.DOTS,
        ),
        CardBackStyle(
            "Sunset", Color(0xFFC2185B), Color(0xFF3A0820), Color(0xFFFFB347), Color(0xFFFF6F00), Color(0xFF5A1A00),
            yellow, BackPattern.STRIPES,
        ),
        CardBackStyle(
            "Royal", Color(0xFF3949AB), Color(0xFF0D1440), Color(0xFFE0E6F0), Color(0xFF9AA5B8), Color(0xFF1A2340),
            Color(0xFFFFD54F), BackPattern.DIAMONDS,
        ),
    )

    fun table(index: Int): TableStyle = tables[index.mod(tables.size)]
    fun cardBack(index: Int): CardBackStyle = cardBacks[index.mod(cardBacks.size)]

    val displayFont: FontFamily = FontFamily.SansSerif
    val bodyFont: FontFamily = FontFamily.SansSerif

    private val animalColors = listOf(red, blue, green, yellow, Color(0xFFB04DFF), Color(0xFF00C2D1))

    /** Every avatar, in tabs. Only ever add to the end of a group that's last, or add a new group at the end: saved picks are indexes. */
    val avatarGroups = listOf(
        AvatarGroup(
            "Animals",
            listOf(
                "🦊" to "Fox", "🐼" to "Panda", "🐸" to "Frog", "🐙" to "Octopus", "🦄" to "Unicorn", "🐯" to "Tiger",
                "🐵" to "Monkey", "🐧" to "Penguin", "🐲" to "Dragon", "🐝" to "Bee", "🦁" to "Lion", "🐨" to "Koala",
            ).mapIndexed { i, (glyph, name) -> AvatarArt(glyph, name, animalColors[i % animalColors.size]) },
        ),
        AvatarGroup(
            "Malayalam",
            listOf(
                AvatarArt("⚡", "Minnal Murali", Color(0xFF1F4FD1), "Minnal Murali"),
                AvatarArt("🐐", "Shaji Pappan", Color(0xFF8E1B1B), "Aadu"),
                AvatarArt("🕵️", "Dasan & Vijayan", Color(0xFF8A6A3B), "Nadodikkattu"),
                AvatarArt("📺", "Georgekutty", Color(0xFF0F7C80), "Drishyam"),
                AvatarArt("😈", "Stephen Nedumpally", Color(0xFF3A1010), "Lucifer"),
                AvatarArt("🐅", "Pulimurugan", Color(0xFFE07A10), "Pulimurugan"),
                AvatarArt("🦋", "George", Color(0xFFE04E8A), "Premam"),
                AvatarArt("☕", "Faizi", Color(0xFF7A4A24), "Ustad Hotel"),
                AvatarArt("🗝️", "Nagavalli", Color(0xFF6B2D8F), "Manichitrathazhu"),
                AvatarArt("🕶️", "Aadu Thoma", Color(0xFFB3261E), "Spadikam"),
                AvatarArt("🕺", "Ranga", Color(0xFFD4A017), "Aavesham"),
                AvatarArt("🌧️", "Jayakrishnan", Color(0xFF3D6E9E), "Thoovanathumbikal"),
            ),
        ),
        AvatarGroup(
            "Tamil",
            listOf(
                AvatarArt("🤖", "Chitti", Color(0xFF8A97A8), "Enthiran"),
                AvatarArt("😎", "Muthuvel Pandian", Color(0xFF1F1F2E), "Jailer"),
                AvatarArt("🍛", "Dilli", Color(0xFFE08A1E), "Kaithi"),
                AvatarArt("👑", "Pulikesi", Color(0xFF6A1B9A), "Imsai Arasan 23am Pulikesi"),
                AvatarArt("✈️", "Maara", Color(0xFF2F8FD8), "Soorarai Pottru"),
                AvatarArt("📷", "Ram", Color(0xFF8E6BBF), "96"),
                AvatarArt("🎭", "Anniyan", Color(0xFFB71C3A), "Anniyan"),
                AvatarArt("⚖️", "Chandru", Color(0xFF233A75), "Jai Bhim"),
                AvatarArt("🎓", "JD", Color(0xFF2E7D32), "Master"),
                AvatarArt("🔥", "Leo Das", Color(0xFF3B2A20), "Leo"),
                AvatarArt("🤼", "Velu", Color(0xFFC0392B), "Ghilli"),
                AvatarArt("🛺", "Manikkam", Color(0xFF1B7F5A), "Baasha"),
            ),
        ),
        AvatarGroup(
            "English",
            listOf(
                AvatarArt("🕷️", "Spider-Man", Color(0xFFD32F2F), "Spider-Man"),
                AvatarArt("🦇", "Batman", Color(0xFF263238), "The Dark Knight"),
                AvatarArt("🛡️", "Captain America", Color(0xFF1E40AF), "The Avengers"),
                AvatarArt("🔨", "Thor", Color(0xFF5C6B7A), "Thor"),
                AvatarArt("💪", "Hulk", Color(0xFF2E9D3A), "The Avengers"),
                AvatarArt("🪄", "Harry Potter", Color(0xFF7F1D1D), "Harry Potter"),
                AvatarArt("🧙", "Gandalf", Color(0xFF6B7280), "The Lord of the Rings"),
                AvatarArt("🏴‍☠️", "Jack Sparrow", Color(0xFF7A5230), "Pirates of the Caribbean"),
                AvatarArt("🃏", "Joker", Color(0xFF6D28D9), "The Dark Knight"),
                AvatarArt("🔍", "Sherlock", Color(0xFF3F5E4A), "Sherlock"),
                AvatarArt("❄️", "Elsa", Color(0xFF5BB8E8), "Frozen"),
                AvatarArt("🧇", "Eleven", Color(0xFFB91C1C), "Stranger Things"),
                AvatarArt("🧪", "Heisenberg", Color(0xFF3F7D3A), "Breaking Bad"),
                AvatarArt("🐉", "Daenerys", Color(0xFF7F1D2D), "Game of Thrones"),
            ),
        ),
    )
    val avatars: List<AvatarArt> = avatarGroups.flatMap { it.avatars }

    /** Colors a player can put behind their avatar instead of its own. */
    val avatarColors = listOf(
        Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF7CB342), Color(0xFF2E9D5A), Color(0xFF00897B),
        Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF3949AB), Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFF6D4C41),
        Color(0xFF37474F),
    )

    /** Rings around an avatar, drawn in code. The first is none. */
    val avatarFrames = listOf("None", "Gold", "Silver", "Rainbow", "Neon", "Sparkle", "Flames")
    val frameGold = Color(0xFFFFE082)
    val frameGoldDeep = Color(0xFFB8860B)
    val frameSilver = Color(0xFFF5F7FA)
    val frameSilverDeep = Color(0xFF8C96A3)
    val frameNeon = Color(0xFF00E5FF)
    val frameFlame = Color(0xFFFF6D00)
    val frameFlameTip = Color(0xFFFFD54F)

    /** Numbered as the protocol's reactions, with [phrases] after them. */
    val reactions = listOf("😂", "😍", "😎", "😮", "😢", "😡", "🤯", "🥳", "👍", "👏", "🔥", "😴")
    /** Quick chat, numbered straight after the emoji. */
    val phrases = listOf(
        "Nice one!", "Hurry up!", "Noooo!", "Gotcha 😏", "Oops", "GG", "Watch out!", "So close!",
        FAAA, SUS, BRUH, EH_EH_EH, QUACK, PUNCH, RIZZ, SHOCKED, WOWW,
    )
    /** Phrases that play a clip instead of a pop; the bubble says what you're about to hear. */
    const val FAAA = "Faaa!"
    const val SUS = "Sus"
    const val BRUH = "Bruh"
    const val EH_EH_EH = "Eh eh ehhh"
    const val QUACK = "Quack"
    const val PUNCH = "Pow!"
    const val RIZZ = "Rizz"
    const val SHOCKED = "No way!"
    const val WOWW = "Wowww"

    fun isPhrase(index: Int): Boolean = index >= reactions.size

    fun reaction(index: Int): String =
        if (isPhrase(index)) phrases[(index - reactions.size).mod(phrases.size)] else reactions[index.mod(reactions.size)]

    fun avatar(index: Int): AvatarArt = avatars[index.mod(avatars.size)]

    /** [color] is an index into [avatarColors], or negative for the avatar's own color. */
    fun avatarBackground(index: Int, color: Int): Color = if (color < 0) avatar(index).color else avatarColors[color.mod(avatarColors.size)]

    fun avatarFrame(frame: Int): Int = frame.mod(avatarFrames.size)
}

@Composable
fun ColorClashTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = GameTheme.highlight,
        onPrimary = GameTheme.ink,
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
    // The whole type scale. Chrome picks from this rather than setting sizes of its own: screen and
    // sheet titles are headlineSmall, section titles titleMedium, buttons labelLarge, tags labelSmall.
    val typography = base.copy(
        displayLarge = TextStyle(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.Black, fontSize = 52.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleLarge = base.titleLarge.copy(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.Black, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = GameTheme.bodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = GameTheme.bodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = GameTheme.bodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = GameTheme.displayFont, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        labelSmall = base.labelSmall.copy(fontFamily = GameTheme.bodyFont, fontWeight = FontWeight.Black, fontSize = 11.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Most screens draw straight onto the table gradient rather than a Surface, so text
        // without a color of its own would otherwise come out black on purple.
        CompositionLocalProvider(LocalContentColor provides GameTheme.text, content = content)
    }
}
