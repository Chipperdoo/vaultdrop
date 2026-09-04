package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkScanTest {

    /** `|` marks the caret, so the cases read the way they look in the editor. */
    private fun query(withCaret: String): String? {
        val caret = withCaret.indexOf('|')
        return LinkScan.queryAt(withCaret.replace("|", ""), caret)
    }

    @Test
    fun `an open bracket raises the strip`() {
        assertEquals("", query("see [[|"))
        assertEquals("Q3", query("see [[Q3|"))
        assertEquals("Q3 Ret", query("see [[Q3 Ret|"))
    }

    @Test
    fun `a closed link does not`() {
        assertNull(query("see [[Q3 Retro]]|"))
        assertNull(query("see [[Q3 Retro]] and more|"))
    }

    @Test
    fun `the nearest open bracket wins`() {
        assertEquals("b", query("[[a]] then [[b|"))
    }

    @Test
    fun `a caret before the brackets sees nothing`() {
        assertNull(query("|see [[Q3"))
        assertNull(query("see [|[Q3"))
        assertEquals("", query("see [[|Q3"))
    }

    @Test
    fun `text with no brackets at all`() {
        assertNull(query("just a note|"))
        assertNull(query("|"))
        assertNull(query("[|"))
    }

    /** The caret can outrun the text between a keystroke and the scan. */
    @Test
    fun `an out-of-range caret is clamped`() {
        assertNull(LinkScan.queryAt("short", 999))
        assertNull(LinkScan.queryAt("short", -3))
        assertEquals("x", LinkScan.queryAt("[[x", 3))
    }

    @Test
    fun `openBracketBefore points at the brackets`() {
        assertEquals(4, LinkScan.openBracketBefore("see [[Q3", 8))
        assertEquals(-1, LinkScan.openBracketBefore("see [[Q3]]", 10))
    }
}
