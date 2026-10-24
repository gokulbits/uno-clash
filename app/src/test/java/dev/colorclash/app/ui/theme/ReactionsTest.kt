package dev.colorclash.app.ui.theme

import dev.colorclash.protocol.REACTION_COUNT
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionsTest {
    @Test
    fun `there's an emoji for every reaction the host accepts`() {
        assertEquals(REACTION_COUNT, GameTheme.reactions.size)
        assertEquals(REACTION_COUNT, GameTheme.reactions.toSet().size)
    }
}
