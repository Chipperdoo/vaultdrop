package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SharingTest {

    /** Applies the edit and marks where the caret landed with a `|`. */
    private fun append(text: String, insert: String, block: Boolean = false): String {
        val edit = Sharing.append(text, insert, block)
        val result = text.substring(0, edit.start) + edit.text + text.substring(edit.end)
        return result.substring(0, edit.caret) + "|" + result.substring(edit.caret)
    }

    // ── The blank line ───────────────────────────────────────────────────────

    @Test
    fun `a note without a trailing newline gets a blank line before the share`() {
        assertEquals("went out\n\nhello|", append("went out", "hello"))
    }

    @Test
    fun `a trailing newline counts toward the blank line`() {
        assertEquals("went out\n\nhello|", append("went out\n", "hello"))
    }

    @Test
    fun `a note already ending in a blank line gets nothing more`() {
        assertEquals("went out\n\nhello|", append("went out\n\n", "hello"))
    }

    @Test
    fun `extra blank lines are left alone, not taken away`() {
        assertEquals("went out\n\n\n\nhello|", append("went out\n\n\n\n", "hello"))
    }

    @Test
    fun `an empty note takes the share as it is`() {
        assertEquals("hello|", append("", "hello"))
        assertEquals("\nhello|", append("\n", "hello"))
    }

    @Test
    fun `a line of only spaces counts as blank`() {
        assertEquals("went out\n  \nhello|", append("went out\n  ", "hello"))
    }

    @Test
    fun `spaces at the end of the last line are not a line break`() {
        assertEquals("went out  \n\nhello|", append("went out  ", "hello"))
    }

    @Test
    fun `a note of only whitespace just needs a fresh line`() {
        assertEquals("  \nhello|", append("  ", "hello"))
    }

    // ── Line endings ─────────────────────────────────────────────────────────

    @Test
    fun `a CRLF note gets CRLF breaks`() {
        assertEquals("went out\r\n\r\nhello|", append("went out\r\n", "hello"))
        assertEquals("a\r\nwent out\r\n\r\nhello|", append("a\r\nwent out", "hello"))
        assertEquals("went out\r\n\r\nhello|", append("went out\r\n\r\n", "hello"))
    }

    @Test
    fun `shared text takes the note's line breaks`() {
        assertEquals("a\r\n\r\none\r\ntwo|", append("a\r\n", "one\ntwo"))
        assertEquals("a\n\none\ntwo|", append("a\n", "one\r\ntwo"))
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun `a block ends its line and leaves the caret below it`() {
        assertEquals("went out\n\n![[a.jpg]]\n|", append("went out\n", "![[a.jpg]]", block = true))
        assertEquals("![[a.jpg]]\n|", append("", "![[a.jpg]]", block = true))
    }

    @Test
    fun `a block in a CRLF note ends in CRLF`() {
        assertEquals("went out\r\n\r\n![[a.jpg]]\r\n|", append("went out\r\n", "![[a.jpg]]", block = true))
    }

    @Test
    fun `what follows a share at the caret goes on the next line`() {
        val first = Sharing.append("went out\n", "look at this", block = false)
        val text = "went out\n" + first.text
        val next = splice(text, first.caret, "![[a.jpg]]", block = true)
        val result = text.substring(0, next.start) + next.text + text.substring(next.end)
        assertEquals("went out\n\nlook at this\n![[a.jpg]]\n", result)
    }

    @Test
    fun `the edit only ever inserts, at the end`() {
        val edit = Sharing.append("went out", "hello", block = false)
        assertEquals(8, edit.start)
        assertEquals(edit.start, edit.end)
    }

    @Test
    fun `nothing to append is no edit`() {
        assertEquals(Edit(8, 8, "", 8), Sharing.append("went out", "", block = false))
    }

    // ── Separator on its own ─────────────────────────────────────────────────

    @Test
    fun `the separator is what joins a template to a share typed into the gap`() {
        assertEquals("", Sharing.separator(""))
        assertEquals("\n\n", Sharing.separator("## Log"))
        assertEquals("\n", Sharing.separator("## Log\n"))
        assertEquals("", Sharing.separator("## Log\n\n"))
    }

    // ── Cleaning shared text ─────────────────────────────────────────────────

    @Test
    fun `blank lines before and whitespace after are dropped`() {
        assertEquals("hello", Sharing.cleanText("\n\nhello\n\n"))
        assertEquals("hello", Sharing.cleanText("  \r\nhello  \r\n"))
    }

    @Test
    fun `indentation on the first line stays`() {
        assertEquals("    code()", Sharing.cleanText("\n    code()\n"))
    }

    @Test
    fun `blank lines inside the text stay`() {
        assertEquals("one\n\ntwo", Sharing.cleanText("one\n\ntwo\n"))
    }

    @Test
    fun `nothing but whitespace is nothing`() {
        assertNull(Sharing.cleanText(null))
        assertNull(Sharing.cleanText(""))
        assertNull(Sharing.cleanText(" \n\t\r\n "))
    }

    // ── Naming a shared file ─────────────────────────────────────────────────

    private val now = LocalDateTime.of(2026, 10, 11, 14, 30, 5)

    private val table = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "audio/mp4" to "m4a",
        "audio/mpeg" to "mp3",
    )

    private fun name(displayName: String?, mime: String?): Sharing.FileName =
        Sharing.fileName(displayName, mime, now) { table[it] }

    @Test
    fun `a shared file keeps its own name and extension`() {
        assertEquals(Sharing.FileName("IMG_20261011_143005", "jpg"), name("IMG_20261011_143005.jpg", "image/jpeg"))
        assertEquals(Sharing.FileName("Voice memo 3", "m4a"), name("Voice memo 3.m4a", "audio/mp4"))
    }

    @Test
    fun `the extension keeps its case`() {
        assertEquals(Sharing.FileName("DSC0001", "JPG"), name("DSC0001.JPG", "image/jpeg"))
    }

    @Test
    fun `the name's extension wins over the MIME's`() {
        assertEquals(Sharing.FileName("photo", "jpeg"), name("photo.jpeg", "image/jpeg"))
        assertEquals(Sharing.FileName("memo", "m4a"), name("memo.m4a", "audio/x-m4a"))
    }

    @Test
    fun `a name without an extension takes the MIME's`() {
        assertEquals(Sharing.FileName("photo", "jpg"), name("photo", "image/jpeg"))
    }

    @Test
    fun `digits after a dot are part of the name, not an extension`() {
        assertEquals(Sharing.FileName("Screenshot 2026.10.11", "png"), name("Screenshot 2026.10.11", "image/png"))
    }

    @Test
    fun `an extension can carry digits`() {
        assertEquals(Sharing.FileName("clip", "3gp"), name("clip.3gp", "audio/3gpp"))
    }

    @Test
    fun `a path in the display name is cut to the file name`() {
        assertEquals(Sharing.FileName("IMG_1", "jpg"), name("/storage/emulated/0/DCIM/IMG_1.jpg", "image/jpeg"))
    }

    @Test
    fun `a trailing dot does not double up with the MIME's extension`() {
        assertEquals(Sharing.FileName("notes", "png"), name("notes.", "image/png"))
    }

    @Test
    fun `an unknown MIME and no extension leaves the extension empty`() {
        assertEquals(Sharing.FileName("photo", ""), name("photo", "image/*"))
        assertEquals(Sharing.FileName("photo", ""), name("photo", null))
    }

    @Test
    fun `a nameless image is a pasted image, as Obsidian names one`() {
        assertEquals(Sharing.FileName("Pasted image 20261011143005", "png"), name(null, "image/png"))
        assertEquals(Sharing.FileName("Pasted image 20261011143005", "jpg"), name("  ", "image/jpeg"))
    }

    @Test
    fun `nameless audio is a recording, as Obsidian's recorder names one`() {
        assertEquals(Sharing.FileName("Recording 20261011143005", "m4a"), name(null, "audio/mp4"))
    }

    @Test
    fun `nameless anything else is an attachment`() {
        assertEquals(Sharing.FileName("Attachment 20261011143005", ""), name(null, "text/plain"))
        assertEquals(Sharing.FileName("Attachment 20261011143005", ""), name(null, null))
    }

    @Test
    fun `a name that is only an extension falls back to the timestamp`() {
        assertEquals(Sharing.FileName("Pasted image 20261011143005", "jpg"), name(".jpg", "image/jpeg"))
    }
}
