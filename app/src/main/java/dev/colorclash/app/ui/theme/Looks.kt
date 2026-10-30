package dev.colorclash.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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

data class TableStyle(val name: String, val top: Color, val bottom: Color) {
    val brush: Brush = Brush.verticalGradient(listOf(top, bottom))
}

/** This phone's picks; nobody else sees them. */
val LocalCardBack = staticCompositionLocalOf { GameTheme.cardBacks.first() }
val LocalTableStyle = staticCompositionLocalOf { GameTheme.tables.first() }
