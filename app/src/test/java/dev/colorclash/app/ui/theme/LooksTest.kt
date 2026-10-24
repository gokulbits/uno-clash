package dev.colorclash.app.ui.theme

import dev.colorclash.protocol.AUTO_AVATAR_COLOR
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
    fun `the first table and card back are the classic look`() {
        assertEquals(GameTheme.tableTop, GameTheme.tables.first().top)
        assertEquals(GameTheme.cardBackOval, GameTheme.cardBacks.first().oval)
    }
}
