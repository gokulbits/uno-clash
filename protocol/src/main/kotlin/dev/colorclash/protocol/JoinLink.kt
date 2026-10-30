package dev.colorclash.protocol

/**
 * What the lobby QR encodes: `colorclash://join?c=ABCD&p=47474&h=192.168.1.20,10.0.0.5`.
 * A host can have several usable addresses (router Wi-Fi and its own hotspot), so all are listed
 * and the joining phone tries each.
 */
data class JoinLink(val hosts: List<String>, val port: Int, val roomCode: String) {

    fun toUri(): String = "$SCHEME://$HOST?c=$roomCode&p=$port&h=${hosts.joinToString(",")}"

    companion object {
        const val SCHEME = "colorclash"
        private const val HOST = "join"
        // Each is probed in turn with a timeout, so a long list would tie up joining for a good while.
        private const val MAX_HOSTS = 8
        private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
        private val roomCodePattern = Regex("^[A-Z0-9]{4,6}$")

        /** Null for anything that isn't a valid join link, e.g. some random QR code. */
        fun parse(text: String): JoinLink? {
            val prefix = "$SCHEME://$HOST?"
            if (!text.trim().startsWith(prefix, ignoreCase = true)) return null
            val params = text.trim().substring(prefix.length).split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()

            val code = params["c"]?.uppercase()?.takeIf { roomCodePattern.matches(it) } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            // This parses links from outside the app too (any web page can open one), and joining sends
            // the player's name and photo. Hosts are always on the LAN, so nothing else gets through.
            val hosts = params["h"].orEmpty().split(",").map { it.trim() }.filter(::isPrivateIpv4).take(MAX_HOSTS)
            if (hosts.isEmpty()) return null
            return JoinLink(hosts, port, code)
        }

        fun isIpv4(text: String): Boolean =
            ipv4.matchEntire(text.trim())?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true

        /** RFC 1918 only. Total on purpose: an address we can't read is simply not a private one. */
        fun isPrivateIpv4(text: String): Boolean {
            if (!isIpv4(text)) return false
            val (a, b) = text.trim().split(".").map { it.toInt() }
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        }

        fun isRoomCode(text: String): Boolean = roomCodePattern.matches(text.trim().uppercase())
    }
}
