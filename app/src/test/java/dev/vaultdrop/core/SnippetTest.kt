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

/**
 * The scan's matcher. It replaced `text.foldCase().indexOf(q)`, so what has to
 * hold is that it agrees with that expression on every input — including the
 * offset, which a snippet window is cut at.
 */
class IndexOfFoldedTest {

    private fun assertAgrees(text: String, query: String) {
        val needle = query.foldCase()
        assertEquals(
            "text=$text query=$query",
            text.foldCase().indexOf(needle),
            indexOfFolded(text, needle),
        )
    }

    @Test
    fun `it agrees with folding the whole text first`() {
        val texts = listOf(
            "", "needle", "a needle here", "NEEDLE", "the Needle and the needle",
            "nee", "nneedle", "needlneedle", "İstanbul", "ǅungla", "Ω omega",
        )
        val queries = listOf("needle", "NEEDLE", "Needle", "n", "e", "", "nope", "İstanbul", "Ω")
        for (text in texts) for (query in queries) assertAgrees(text, query)
    }

    @Test
    fun `a needle longer than the text is not found`() {
        assertEquals(-1, indexOfFolded("no", "needle"))
    }

    @Test
    fun `the offset lands on the original text, not a folded copy`() {
        val text = "Bought NEEDLES at the shop"
        val at = indexOfFolded(text, "needles")
        assertEquals(7, at)
        assertEquals("NEEDLES", text.substring(at, at + 7))
    }

    /** A near miss must not stop the scan short of a real hit further on. */
    @Test
    fun `a partial match keeps looking`() {
        assertEquals(9, indexOfFolded("needneed needle", "needle"))
    }
}
