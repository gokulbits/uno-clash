package dev.colorclash.net.host

/**
 * Rations one seat's reactions: [burst] of them inside [windowMillis], and never two closer
 * together than [gapMillis]. Time comes in from the caller, so this stays easy to test.
 */
class ReactionLimiter(private val burst: Int, private val windowMillis: Long, private val gapMillis: Long) {
    private val recent = ArrayDeque<Long>()

    fun allow(nowMillis: Long): Boolean {
        forgetOld(nowMillis)
        val last = recent.lastOrNull()
        if (last != null && nowMillis - last < gapMillis) return false
        if (recent.size >= burst) return false
        recent.addLast(nowMillis)
        return true
    }

    /** Whether the burst is used up right now, without counting this as another try. */
    fun exhausted(nowMillis: Long): Boolean {
        forgetOld(nowMillis)
        return recent.size >= burst
    }

    private fun forgetOld(nowMillis: Long) {
        while (recent.isNotEmpty() && nowMillis - recent.first() >= windowMillis) recent.removeFirst()
    }
}
