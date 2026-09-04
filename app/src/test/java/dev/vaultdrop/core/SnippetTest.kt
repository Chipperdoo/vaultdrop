package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure half of [VaultSearch]: the window a result row quotes. */
class SnippetTest {

    private fun around(text: String, query: String): String {
        val at = text.foldCase().indexOf(query.foldCase())
        assertTrue("$query is not in the text", at != -1)
        return Snippet.around(text, at, query.length)
    }

    @Test
    fun `a hit at the start is not elided on the left`() {
        val window = around("Groceries and then some more of the note", "Groceries")
        assertTrue(window, window.startsWith("Groceries"))
        assertFalse(window, window.startsWith("…"))
    }

    @Test
    fun `a hit in the middle of a long note is elided on both sides`() {
        val text = "x".repeat(400) + " needle " + "y".repeat(400)
        val window = around(text, "needle")
        assertTrue(window, window.startsWith("…"))
        assertTrue(window, window.endsWith("…"))
        assertTrue(window, window.contains("needle"))
    }

    @Test
    fun `a hit at the very end is not elided on the right`() {
        val text = "y".repeat(400) + " needle"
        val window = around(text, "needle")
        assertTrue(window, window.endsWith("needle"))
    }

    /** The row marks the hit by finding it again, so it has to survive intact. */
    @Test
    fun `the query is still findable in the window`() {
        val text = "a".repeat(200) + "\n\n  Shopping  list\n" + "b".repeat(200)
        val window = around(text, "Shopping")
        assertTrue(window, window.indexOf("Shopping", ignoreCase = true) != -1)
    }

    @Test
    fun `newlines and runs of spaces collapse to one space`() {
        val window = around("one\n\n\ttwo   three\r\nfour", "two")
        assertEquals("one two three four", window)
    }

    @Test
    fun `the window carries no leading or trailing whitespace`() {
        val window = around("   \n  needle  \n   ", "needle")
        assertEquals("needle", window)
    }

    /** A window that runs past the end of the text must clamp, not throw. */
    @Test
    fun `a hit right at the read cap clamps`() {
        val text = "z".repeat(50) + "needle"
        val window = around(text, "needle")
        assertTrue(window, window.endsWith("needle"))
    }

    @Test
    fun `case is ignored when finding the hit`() {
        val window = around("Bought NEEDLES at the shop", "needles")
        assertTrue(window, window.contains("NEEDLES"))
    }
}
