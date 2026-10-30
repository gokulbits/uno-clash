package dev.colorclash.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinLinkTest {

    @Test
    fun `round trips`() {
        val link = JoinLink(listOf("192.168.1.20", "10.0.0.5"), 47474, "ABCD")
        assertEquals("colorclash://join?c=ABCD&p=47474&h=192.168.1.20,10.0.0.5", link.toUri())
        assertEquals(link, JoinLink.parse(link.toUri()))
    }

    @Test
    fun `lowercase room codes are normalised`() {
        assertEquals("ABCD", JoinLink.parse("colorclash://join?c=abcd&p=1&h=10.2.3.4")?.roomCode)
    }

    @Test
    fun `bad hosts are dropped and a link with none is rejected`() {
        assertEquals(listOf("10.2.3.4"), JoinLink.parse("colorclash://join?c=ABCD&p=1&h=nope,10.2.3.4,300.1.1.1")?.hosts)
        assertNull(JoinLink.parse("colorclash://join?c=ABCD&p=1&h=nope"))
    }

    @Test
    fun `a link can't send this phone's name and photo off the local network`() {
        assertEquals(listOf("192.168.1.2"), JoinLink.parse("colorclash://join?c=ABCD&p=1&h=8.8.8.8,192.168.1.2")?.hosts)
        assertNull(JoinLink.parse("colorclash://join?c=ABCD&p=1&h=203.0.113.9"))
        assertNull(JoinLink.parse("colorclash://join?c=ABCD&p=1&h=172.32.0.1"))
    }

    @Test
    fun `a long list of hosts is cut short`() {
        val hosts = (1..50).joinToString(",") { "10.0.0.$it" }
        assertEquals(8, JoinLink.parse("colorclash://join?c=ABCD&p=1&h=$hosts")?.hosts?.size)
    }

    @Test
    fun `other qr codes are ignored`() {
        assertNull(JoinLink.parse("https://example.com"))
        assertNull(JoinLink.parse("colorclash://join?c=AB&p=1&h=10.2.3.4"))
        assertNull(JoinLink.parse("colorclash://join?c=ABCD&p=99999&h=10.2.3.4"))
        assertNull(JoinLink.parse(""))
    }

    @Test
    fun `validators`() {
        assertTrue(JoinLink.isIpv4("192.168.43.1"))
        assertFalse(JoinLink.isIpv4("192.168.43"))
        assertFalse(JoinLink.isIpv4("256.1.1.1"))
        assertTrue(JoinLink.isRoomCode("k7mq"))
        assertFalse(JoinLink.isRoomCode("ab"))
    }
}
