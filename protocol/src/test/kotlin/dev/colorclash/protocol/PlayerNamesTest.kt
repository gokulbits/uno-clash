package dev.colorclash.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerNamesTest {

    @Test
    fun `control characters and direction overrides go`() {
        assertEquals("evil", cleanPlayerName("‮ev\u0000il\n"))
    }

    @Test
    fun `an emoji is never cut in half`() {
        val name = "a".repeat(MAX_NAME_CHARS - 1) + "🦊"
        assertEquals("a".repeat(MAX_NAME_CHARS - 1), name.clipped())
        assertEquals("🦊", "🦊".clipped())
    }

    @Test
    fun `emoji built with joiners survive`() {
        assertEquals("🏴‍☠️", cleanPlayerName("  🏴‍☠️ "))
    }

    @Test
    fun `nothing printable leaves a blank name for the host to replace`() {
        assertEquals("", cleanPlayerName(" ‮\t "))
    }
}
