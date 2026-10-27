package dev.colorclash.net.host

/**
 * Rations one seat's reactions: [burst] of them inside [windowMillis], and never two closer
 * together than [gapMillis]. Time comes in from the caller, so this stays easy to test.
 */
class ReactionLimiter(private val burst: Int, private val windowMillis: Long, private val gapMillis: Long) {
    private val recent = ArrayDeque<Long>()

    fun allow(nowMillis: Long): Boolean {
        while (recent.isNotEmpty() && nowMillis - recent.first() >= windowMillis) recent.removeFirst()
        val last = recent.lastOrNull()
        if (last != null && nowMillis - last < gapMillis) return false
        if (recent.size >= burst) return false
        recent.addLast(nowMillis)
        return true
    }
}
