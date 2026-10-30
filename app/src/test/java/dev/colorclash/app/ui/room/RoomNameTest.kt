package dev.colorclash.app.ui.room

import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.LobbyView
import dev.colorclash.protocol.SeatInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomNameTest {
    @Test
    fun `a room is named after its host`() {
        assertEquals("Sam's Clash", clashName("Sam"))
        assertEquals("Sam's Clash", clashName(" Sam "))
        assertEquals(GAME_TITLE, clashName(""))
    }

    @Test
    fun `the start button says what it's waiting for`() {
        val host = SeatInfo(0, "Me", 0, isHost = true, ready = true)
        val guest = SeatInfo(1, "Sam", 0)
        fun lobby(vararg seats: SeatInfo, canStart: Boolean = false) = LobbyView("ABCD", 0, seats.toList(), GameSettings(), canStart)

        assertEquals("Add a bot or wait for a friend", startLabel(lobby(host)))
        assertEquals("Waiting for everyone to be ready", startLabel(lobby(host, guest)))
        assertEquals("Start game", startLabel(lobby(host, guest, canStart = true)))
    }
}
