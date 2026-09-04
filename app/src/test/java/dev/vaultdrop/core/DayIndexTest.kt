package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class DayIndexTest {

    private val today = LocalDate.of(2026, 9, 3)

    private fun index(
        vararg paths: String,
        format: String = "YYYY-MM-DD",
        folder: String = "",
        span: Int = 30,
    ) = DayIndex.build(VaultConfig(format = format, folder = folder), paths.toSet(), today, span)

    @Test
    fun `keeps only the days that have a note`() {
        val days = index("2026-09-03.md", "2026-08-30.md", "not-a-daily-note.md").days
        assertEquals(listOf(LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 3)), days)
    }

    @Test
    fun `a step skips the whole gap`() {
        val days = index("2026-09-03.md", "2026-08-30.md")
        assertEquals(LocalDate.of(2026, 8, 30), days.before(today))
        assertEquals(today, days.after(LocalDate.of(2026, 8, 30)))
    }

    @Test
    fun `steps from a day with no note of its own`() {
        val days = index("2026-09-03.md", "2026-08-30.md")
        val between = LocalDate.of(2026, 9, 1)
        assertEquals(LocalDate.of(2026, 8, 30), days.before(between))
        assertEquals(today, days.after(between))
    }

    @Test
    fun `both edges are dead at the ends`() {
        val days = index("2026-09-03.md", "2026-08-30.md")
        assertNull(days.before(LocalDate.of(2026, 8, 30)))
        assertNull(days.after(today))
    }

    @Test
    fun `an empty vault has no edges at all`() {
        val days = index()
        assertNull(days.before(today))
        assertNull(days.after(LocalDate.of(2026, 1, 1)))
        assertEquals(0, days.size)
    }

    @Test
    fun `days outside the span are not reachable`() {
        val days = index("2026-09-03.md", "2026-08-01.md", span = 7)
        assertEquals(1, days.size)
        assertNull(days.before(today))
    }

    @Test
    fun `a nested format resolves through its directories`() {
        val days = index(
            "Journal/2026/09/03.md",
            "Journal/2026/08/31.md",
            format = "YYYY/MM/DD",
            folder = "Journal",
        )
        assertEquals(LocalDate.of(2026, 8, 31), days.before(today))
    }

    @Test
    fun `a note the walk has not seen yet is reachable once recorded`() {
        val days = index("2026-09-03.md").plus(LocalDate.of(2026, 9, 1))
        assertTrue(days.contains(LocalDate.of(2026, 9, 1)))
        assertEquals(LocalDate.of(2026, 9, 1), days.before(today))
    }

    @Test
    fun `a note deleted under the walk drops back out`() {
        val days = index("2026-09-03.md", "2026-09-01.md").minus(LocalDate.of(2026, 9, 1))
        assertFalse(days.contains(LocalDate.of(2026, 9, 1)))
        assertNull(days.before(today))
    }

    @Test
    fun `adding a day already present changes nothing`() {
        val days = index("2026-09-03.md").plus(today)
        assertEquals(listOf(today), days.days)
    }

    @Test
    fun `changes apply in both directions at once`() {
        val days = index("2026-09-03.md", "2026-09-02.md").with(
            mapOf(LocalDate.of(2026, 9, 2) to false, LocalDate.of(2026, 8, 28) to true),
        )
        assertEquals(listOf(LocalDate.of(2026, 8, 28), today), days.days)
    }
}
