package dev.colorclash.app.ui.theme

import dev.colorclash.protocol.REACTION_COUNT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionsTest {
    @Test
    fun `there's an emoji or a phrase for every reaction the host accepts`() {
        val all = GameTheme.reactions + GameTheme.phrases
        assertEquals(REACTION_COUNT, all.size)
        assertEquals(REACTION_COUNT, all.toSet().size)
        assertEquals("😂", GameTheme.reaction(0))
        assertEquals("Nice one!", GameTheme.reaction(GameTheme.reactions.size))
        assertTrue(GameTheme.isPhrase(REACTION_COUNT - 1))
    }
}
