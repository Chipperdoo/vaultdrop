package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import java.time.LocalDateTime
import org.junit.Test

class MomentFormatTest {

    private val moment = LocalDateTime.of(2026, 9, 2, 14, 32, 45)

    private fun render(pattern: String): String =
        MomentFormat.formatter(pattern).format(moment)

    /** The brief's translation table, token by token. */
    @Test
    fun `translates every token in the brief`() {
        assertEquals("2026", render("YYYY"))
        assertEquals("26", render("YY"))
        assertEquals("09", render("MM"))
        assertEquals("9", render("M"))
        assertEquals("02", render("DD"))
        assertEquals("2", render("D"))
        assertEquals("Wed", render("ddd"))
        assertEquals("Wednesday", render("dddd"))
        assertEquals("14", render("HH"))
        assertEquals("14", render("H"))
        assertEquals("32", render("mm"))
        assertEquals("32", render("m"))
        assertEquals("45", render("ss"))
        assertEquals("PM", render("A"))
    }

    @Test
    fun `handles the formats Obsidian actually ships`() {
        assertEquals("2026-09-02 Wed", render("YYYY-MM-DD ddd"))
        assertEquals("2026-09-02", render("YYYY-MM-DD"))
        assertEquals("2026/09/02", render("YYYY/MM/DD"))
    }

    @Test
    fun `longer tokens win over their prefixes`() {
        assertEquals("Wednesday", render("dddd"))
        assertEquals("2026", render("YYYY"))
        assertEquals("September", render("MMMM"))
    }

    @Test
    fun `separators and words survive as literals`() {
        assertEquals("Daily 2026", render("[Daily] YYYY"))
        assertEquals("2026 at 14:32", render("YYYY [at] HH:mm"))
    }

    /** An unknown token must degrade to text, never throw and lose the note. */
    @Test
    fun `unknown tokens fall through as literal text`() {
        assertEquals("2026 Do", render("YYYY Do"))
    }

    @Test
    fun `quotes in a literal are escaped`() {
        assertEquals("today's 2026", render("[today's] YYYY"))
    }
}
