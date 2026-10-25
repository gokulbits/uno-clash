package dev.colorclash.app.ui.room

import dev.colorclash.app.ui.theme.GAME_TITLE
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomNameTest {
    @Test
    fun `a room is named after its host`() {
        assertEquals("Gokul's Clash", clashName("Gokul"))
        assertEquals("Gokul's Clash", clashName(" Gokul "))
        assertEquals(GAME_TITLE, clashName(""))
    }
}
