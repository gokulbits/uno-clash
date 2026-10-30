package dev.colorclash.net

import dev.colorclash.net.host.ReactionLimiter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactionLimiterTest {

    private fun limiter() = ReactionLimiter(burst = 3, windowMillis = 8_000, gapMillis = 2_000)

    @Test
    fun `a burst gets through, spaced out`() {
        val limiter = limiter()
        assertTrue(limiter.allow(0))
        assertTrue(limiter.allow(2_000))
        assertTrue(limiter.allow(4_000))
    }

    @Test
    fun `mashing the same button is dropped`() {
        val limiter = limiter()
        assertTrue(limiter.allow(0))
        assertFalse(limiter.allow(100))
        assertFalse(limiter.allow(1_999))
        assertTrue(limiter.allow(2_000))
    }

    @Test
    fun `the burst runs out even when every gap is respected`() {
        val limiter = limiter()
        assertTrue(limiter.allow(0))
        assertTrue(limiter.allow(2_000))
        assertTrue(limiter.allow(4_000))
        assertFalse(limiter.allow(6_000))
    }

    @Test
    fun `waiting for the window to pass lets them react again`() {
        val limiter = limiter()
        assertTrue(limiter.allow(0))
        assertTrue(limiter.allow(2_000))
        assertTrue(limiter.allow(4_000))
        assertFalse(limiter.allow(7_900))
        assertTrue(limiter.allow(8_000))
    }

    @Test
    fun `asking whether the burst is spent doesn't spend it`() {
        val limiter = ReactionLimiter(burst = 2, windowMillis = 1_000, gapMillis = 0)
        repeat(5) { assertFalse(limiter.exhausted(0)) }
        assertTrue(limiter.allow(0))
        assertTrue(limiter.allow(0))
        assertTrue(limiter.exhausted(999))
        assertFalse(limiter.exhausted(1_000))
    }

    @Test
    fun `a quiet player is never held back`() {
        val limiter = limiter()
        repeat(10) { i -> assertTrue(limiter.allow(i * 30_000L)) }
    }
}
