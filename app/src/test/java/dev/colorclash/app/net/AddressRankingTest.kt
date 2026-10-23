package dev.colorclash.app.net

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressRankingTest {

    @Test
    fun `wifi first, hotspot next, mobile data and vpn dropped`() {
        val ranked = AddressRanking.rank(
            listOf(
                LocalAddress("rmnet_data0", "10.45.3.2"),
                LocalAddress("ap0", "192.168.43.1"),
                LocalAddress("tun0", "10.8.0.2"),
                LocalAddress("wlan0", "192.168.1.23"),
            ),
        )
        assertEquals(listOf("192.168.1.23", "192.168.43.1"), ranked)
    }

    @Test
    fun `public and duplicate addresses are ignored`() {
        val ranked = AddressRanking.rank(
            listOf(
                LocalAddress("wlan0", "8.8.8.8"),
                LocalAddress("wlan0", "172.20.10.1"),
                LocalAddress("wlan1", "172.20.10.1"),
                LocalAddress("swlan0", "10.12.0.1"),
            ),
        )
        assertEquals(listOf("172.20.10.1", "10.12.0.1"), ranked)
    }
}
