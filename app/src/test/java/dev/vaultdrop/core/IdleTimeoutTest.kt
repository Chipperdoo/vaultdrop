package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTimeoutTest {

    private val limit = 180_000L
    private val every = 5_000L

    private fun timeout() = IdleTimeout(limit, every)

    /** Checks every [every] from [from] to [to], the way the client's timer does. True if any ran out. */
    private fun IdleTimeout.checkUntil(from: Long, to: Long): Boolean =
        (from..to step every).any { check(it) }

    @Test
    fun `nothing runs out while the mic is still open`() {
        val idle = timeout()
        idle.heard(now = 5_000)
        assertNull(idle.remaining(now = 10 * 60_000))
        assertFalse(idle.checkUntil(0, 10 * 60_000))
    }

    @Test
    fun `the clock starts at Stop, not at the last thing heard before it`() {
        val idle = timeout()
        idle.heard(now = 1_000)
        idle.waiting(now = 90_000)
        assertEquals(limit, idle.remaining(now = 90_000))
        assertEquals(30_000L, idle.remaining(now = 240_000))
    }

    @Test
    fun `a callback after Stop starts the clock again`() {
        val idle = timeout()
        idle.waiting(now = 0)
        idle.heard(now = 100_000)
        assertEquals(limit, idle.remaining(now = 100_000))
        assertEquals(80_000L, idle.remaining(now = 200_000))
    }

    @Test
    fun `a long transcription that keeps reporting never runs out`() {
        val idle = timeout()
        idle.waiting(now = 0)
        var t = 0L
        while (t < 30 * 60_000) {
            assertFalse(idle.checkUntil(t, t + 60_000))
            t += 60_000
            idle.heard(now = t)
        }
    }

    @Test
    fun `a quiet engine runs out exactly at the limit, and stays out`() {
        val idle = timeout()
        idle.waiting(now = 0)
        assertFalse(idle.checkUntil(0, limit - every))
        assertTrue(idle.check(now = limit))
        assertTrue(idle.check(now = limit + every))
    }

    @Test
    fun `pressing Stop twice does not buy more time`() {
        val idle = timeout()
        idle.waiting(now = 0)
        idle.waiting(now = 60_000)
        assertEquals(limit - 60_000, idle.remaining(now = 60_000))
    }

    @Test
    fun `a gap where no check ran is not counted against the engine`() {
        val idle = timeout()
        idle.waiting(now = 0)
        assertFalse(idle.checkUntil(0, 60_000))
        // Frozen in the background for ten minutes: the next check is far overdue.
        val back = 60_000L + 10 * 60_000
        assertFalse(idle.check(now = back))
        assertEquals(limit, idle.remaining(now = back))
        assertTrue(idle.checkUntil(back, back + limit))
    }

    @Test
    fun `a check that is only a little late still counts the time`() {
        val idle = timeout()
        idle.waiting(now = 0)
        assertFalse(idle.check(now = 5_000))
        assertFalse(idle.check(now = 5_000 + every * 3))
        assertEquals(limit - (5_000 + every * 3), idle.remaining(now = 5_000 + every * 3))
    }
}
