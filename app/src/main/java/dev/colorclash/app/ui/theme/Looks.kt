package dev.colorclash.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** [from] is the film or series a character comes from; the glyph stands for them, it's not a likeness. */
data class AvatarArt(val glyph: String, val name: String, val color: Color, val from: String? = null)

/** A tab of avatars in the picker. Indexes run on from one group to the next, so they never move. */
data class AvatarGroup(val title: String, val avatars: List<AvatarArt>)

enum class BackPattern { PLAIN, DIAMONDS, STRIPES, DOTS, WAVES }

/** A card back design. The two words are drawn in white and [accent], outlined in [ink]. */
data class CardBackStyle(
    val name: String,
    val glow: Color,
    val deep: Color,
    val oval: Color,
    val ovalDeep: Color,
    val ink: Color,
    val accent: Color,
    val pattern: BackPattern,
)

/**
 * A table, and the surfaces that sit on it. The panels are see-through white over the table, except
 * on [GameTheme.oledTable], where they stay close to off so the screen's black really is black.
 * [quiet] and [disabled] fill the buttons that back out and the ones that can't be pressed yet.
 */
data class TableStyle(
    val name: String,
    val top: Color,
    val bottom: Color,
    val panel: Color = Color(0x26FFFFFF),
    val panelStrong: Color = Color(0x40FFFFFF),
    val edge: Color = Color(0x33FFFFFF),
    val quiet: Color = lerp(top, Color.White, 0.2f),
    val disabled: Color = lerp(top, Color.Black, 0.3f),
) {
    val brush: Brush = Brush.verticalGradient(listOf(top, bottom))
}

/** This phone's picks; nobody else sees them. */
val LocalCardBack = staticCompositionLocalOf { GameTheme.cardBacks.first() }
val LocalTableStyle = staticCompositionLocalOf { GameTheme.tables.first() }
