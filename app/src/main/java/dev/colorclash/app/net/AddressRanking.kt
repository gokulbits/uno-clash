package dev.colorclash.app.net

import dev.colorclash.protocol.JoinLink

data class LocalAddress(val interfaceName: String, val ip: String)

/** Picks which of this phone's addresses other phones could reach. Pure so it can be unit tested. */
object AddressRanking {
    // Mobile data, VPN and other virtual interfaces that friends on the same Wi-Fi can't reach.
    private val unreachablePrefixes = listOf("rmnet", "ccmni", "pdp", "v4-", "clat", "tun", "ppp", "dummy", "ims", "seth", "lo")

    fun rank(addresses: List<LocalAddress>): List<String> = addresses
        .filter { a -> unreachablePrefixes.none { a.interfaceName.startsWith(it) } }
        .filter { JoinLink.isIpv4(it.ip) && isPrivate(it.ip) }
        .sortedBy { priority(it.interfaceName) }
        .map { it.ip }
        .distinct()

    private fun priority(name: String) = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 1
        name.startsWith("eth") -> 2
        else -> 3
    }

    /** RFC 1918 only. Total on purpose: an address we can't read is simply not a private one. */
    private fun isPrivate(ip: String): Boolean {
        val parts = ip.trim().split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }
}
