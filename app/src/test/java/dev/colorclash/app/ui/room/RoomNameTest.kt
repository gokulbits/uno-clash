package dev.colorclash.app.ui.room

import dev.colorclash.app.ui.theme.GAME_TITLE
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomNameTest {
    @Test
    fun `a room is named after its host`() {
        assertEquals("Sam's Clash", clashName("Sam"))
        assertEquals("Sam's Clash", clashName(" Sam "))
        assertEquals(GAME_TITLE, clashName(""))
    }
}
