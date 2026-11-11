package dev.colorclash.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.colorclash.protocol.AUTO_AVATAR_COLOR
import dev.colorclash.rules.CardColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LooksTest {
    @Test
    fun `saved avatar picks still point at the same animals`() {
        val before = listOf("🦊", "🐼", "🐸", "🐙", "🦄", "🐯", "🐵", "🐧", "🐲", "🐝", "🦁", "🐨")
        assertEquals(before, GameTheme.avatars.take(before.size).map { it.glyph })
    }

    @Test
    fun `every avatar has its own name and every character says where it's from`() {
        val all = GameTheme.avatars
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertTrue(all.all { it.name.isNotBlank() && it.glyph.isNotBlank() })
        GameTheme.avatarGroups.drop(1).flatMap { it.avatars }.forEach { assertNotNull(it.from, it.name) }
        assertEquals(listOf("Animals", "Malayalam", "Tamil", "English"), GameTheme.avatarGroups.map { it.title })
    }

    @Test
    fun `out of range picks from another phone wrap instead of crashing`() {
        val n = GameTheme.avatars.size
        assertEquals(GameTheme.avatars[1], GameTheme.avatar(n + 1))
        assertEquals(GameTheme.avatars[n - 1], GameTheme.avatar(-1))
        assertEquals(GameTheme.avatarFrames.size - 1, GameTheme.avatarFrame(-1))
        assertEquals(GameTheme.tables.first(), GameTheme.table(GameTheme.tables.size))
        assertEquals(GameTheme.cardBacks.first(), GameTheme.cardBack(GameTheme.cardBacks.size))
    }

    @Test
    fun `auto color is the avatar's own and a pick overrides it`() {
        assertEquals(GameTheme.avatar(15).color, GameTheme.avatarBackground(15, AUTO_AVATAR_COLOR))
        assertEquals(GameTheme.avatarColors[2], GameTheme.avatarBackground(15, 2))
        assertEquals(GameTheme.avatarColors[0], GameTheme.avatarBackground(15, GameTheme.avatarColors.size))
    }

    @Test
    fun `text on any game color is readable`() {
        val fills = GameTheme.cardColors + GameTheme.darkCardColors + GameTheme.danger + GameTheme.highlight + GameTheme.wild
        fills.forEach { fill ->
            assertTrue(GameTheme.contrast(GameTheme.contentOn(fill), fill) >= 3f, "$fill")
        }
    }

    @Test
    fun `every card color has a color of its own, in the deck's order`() {
        assertEquals(CardColor.entries.size, CardColor.entries.map { GameTheme.cardColor(it) }.toSet().size)
        assertEquals(GameTheme.cardColors, CardColor.light.map { GameTheme.cardColor(it) })
        assertEquals(GameTheme.darkCardColors, CardColor.dark.map { GameTheme.cardColor(it) })
    }

    @Test
    fun `a flip deck's dark side is black stock the color still shows on`() {
        // Black stock, so a dark card never reads as a light one in another color.
        assertTrue(GameTheme.contrast(GameTheme.cardPaper, GameTheme.darkCardPaper) > 8f)
        // The color rings the oval and the marks are white inside it: both have to carry.
        GameTheme.darkCardColors.forEach { assertTrue(GameTheme.contrast(it, GameTheme.darkCardOval) >= 3f, "$it") }
        assertTrue(GameTheme.contrast(GameTheme.cardPaper, GameTheme.darkCardOval) >= 7f)
    }

    @Test
    fun `the oled table is true black and text stays readable on its buttons`() {
        val oled = GameTheme.oledTable
        assertEquals(Color.Black, oled.top)
        assertEquals(Color.Black, oled.bottom)
        assertEquals(Color.Black, GameTheme.oledCardBack.deep)
        assertTrue(GameTheme.contrast(GameTheme.contentOn(oled.quiet), oled.quiet) >= 3f)
        // Still has to show as a button on black, with its words muted but legible.
        assertTrue(GameTheme.contrast(oled.disabled, oled.top) > 1f)
        assertTrue(GameTheme.contrast(GameTheme.mutedText.compositeOver(oled.disabled), oled.disabled) >= 3f)
    }

    @Test
    fun `only the oled table dims the button rim`() {
        assertTrue(GameTheme.oledTable.rim != Color.White)
        // Still has to draw a button on black: the rim sits between the table and the quiet fill.
        assertTrue(GameTheme.contrast(GameTheme.oledTable.rim, GameTheme.oledTable.top) > 2f)
        assertTrue(GameTheme.tables.filter { it !== GameTheme.oledTable }.all { it.rim == Color.White })
    }

    @Test
    fun `picks saved before the oled look still mean the same table and card back`() {
        assertEquals(listOf("Classic", "Felt", "Ocean", "Ruby", "Teal", "Midnight"), GameTheme.tables.take(6).map { it.name })
        assertEquals(listOf("Classic", "Midnight", "Ocean", "Jungle", "Sunset", "Royal"), GameTheme.cardBacks.take(6).map { it.name })
    }

    @Test
    fun `the first table and card back are the classic look`() {
        assertEquals(GameTheme.tableTop, GameTheme.tables.first().top)
        assertEquals(GameTheme.cardBackOval, GameTheme.cardBacks.first().oval)
    }
}
