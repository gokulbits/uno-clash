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
            val hosts = params["h"].orEmpty().split(",").map { it.trim() }.filter(::isIpv4)
            if (hosts.isEmpty()) return null
            return JoinLink(hosts, port, code)
        }

        fun isIpv4(text: String): Boolean =
            ipv4.matchEntire(text.trim())?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true

        fun isRoomCode(text: String): Boolean = roomCodePattern.matches(text.trim().uppercase())
    }
}
