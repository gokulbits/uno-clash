package dev.colorclash.app.net

import dev.colorclash.protocol.JoinLink

data class LocalAddress(val interfaceName: String, val ip: String)

/** Picks which of this phone's addresses other phones could reach. Pure so it can be unit tested. */
object AddressRanking {
    // Mobile data, VPN and other virtual interfaces that friends on the same Wi-Fi can't reach.
    private val unreachablePrefixes = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "tun", "ppp", "dummy", "ims", "seth", "lo")

    fun rank(addresses: List<LocalAddress>): List<String> = addresses
        .filter { a -> unreachablePrefixes.none { a.interfaceName.startsWith(it) } }
        .filter { JoinLink.isPrivateIpv4(it.ip) }
        .sortedBy { priority(it.interfaceName) }
        .map { it.ip }
        .distinct()

    private fun priority(name: String) = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 1
        name.startsWith("eth") -> 2
        else -> 3
    }
}
