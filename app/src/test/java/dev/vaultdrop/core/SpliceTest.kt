package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SpliceTest {

    /** Applies the edit and marks where the caret landed with a `|`. */
    private fun apply(text: String, caret: Int, insert: String, block: Boolean): String {
        val edit = splice(text, caret, insert, block)
        val result = text.substring(0, edit.start) + edit.text + text.substring(edit.end)
        return result.substring(0, edit.caret) + "|" + result.substring(edit.caret)
    }

    /** [text] with a `|` where the caret is, so a case reads the way it looks. */
    private fun at(marked: String, insert: String, block: Boolean): String {
        val caret = marked.indexOf('|')
        return apply(marked.removeRange(caret, caret + 1), caret, insert, block)
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun `a block at the end of a line gets a line of its own`() {
        assertEquals("went out\n![[a.jpg]]\n|", at("went out|", "![[a.jpg]]", block = true))
    }

    @Test
    fun `a block at the start of a line needs no newline before it`() {
        assertEquals("went out\n![[a.jpg]]\n|", at("went out\n|", "![[a.jpg]]", block = true))
        assertEquals("![[a.jpg]]\n|", at("|", "![[a.jpg]]", block = true))
    }

    @Test
    fun `a block before a line break uses that one, and the caret steps over it`() {
        assertEquals("went out\n![[a.jpg]]\n|then home", at("went out|\nthen home", "![[a.jpg]]", block = true))
    }

    @Test
    fun `a block in the middle of a line splits it`() {
        assertEquals("went\n![[a.jpg]]\n| out", at("went| out", "![[a.jpg]]", block = true))
    }

    @Test
    fun `several blocks in a row are one per line, in order`() {
        var text = "went out"
        var caret = text.length
        for (name in listOf("a", "b", "c")) {
            val edit = splice(text, caret, "![[$name.jpg]]", block = true)
            text = text.substring(0, edit.start) + edit.text + text.substring(edit.end)
            caret = edit.caret
        }
        assertEquals("went out\n![[a.jpg]]\n![[b.jpg]]\n![[c.jpg]]\n", text)
        assertEquals(text.length, caret)
    }

    @Test
    fun `a CRLF line break counts as one`() {
        assertEquals("went out\n![[a.jpg]]\r\n|then", at("went out|\r\nthen", "![[a.jpg]]", block = true))
        assertEquals("a\r\n![[a.jpg]]\n|", at("a\r\n|", "![[a.jpg]]", block = true))
    }

    // ── Prose ────────────────────────────────────────────────────────────────

    @Test
    fun `prose after a word gets a space`() {
        assertEquals("went out and back|", at("went out|", "and back", block = false))
    }

    @Test
    fun `prose after whitespace does not`() {
        assertEquals("went out and back|", at("went out |", "and back", block = false))
        assertEquals("went out\nand back|", at("went out\n|", "and back", block = false))
        assertEquals("and back|", at("|", "and back", block = false))
    }

    @Test
    fun `prose that starts with a space brings its own`() {
        assertEquals("went out and back|", at("went out|", " and back", block = false))
    }

    @Test
    fun `prose mid-sentence leaves the rest where it was`() {
        assertEquals("went out and back| today", at("went out| today", "and back", block = false))
    }

    // ── Edges ────────────────────────────────────────────────────────────────

    @Test
    fun `nothing to insert is no edit`() {
        assertEquals(Edit(3, 3, "", 3), splice("abc", 3, "", block = true))
    }

    @Test
    fun `a caret out of range is pulled back in`() {
        assertEquals(Edit(3, 3, " d", 5), splice("abc", 99, "d", block = false))
        assertEquals(Edit(0, 0, "d", 1), splice("abc", -4, "d", block = false))
    }

    @Test
    fun `the edit only ever inserts`() {
        val edit = splice("went out", 4, "![[a.jpg]]", block = true)
        assertEquals(edit.start, edit.end)
    }
}
