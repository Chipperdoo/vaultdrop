package dev.vaultdrop.core

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fence has to be found whatever wrote the note.
 *
 * A vault synced from Obsidian on Windows is CRLF throughout, and the fence
 * check used to look for exactly `---` followed by LF. It missed, the note read
 * as having no frontmatter at all, and the first autosave minted a second fence
 * above the first — demoting the real one, tags and all, to body text.
 */
class FrontmatterLineEndingTest {

    private val bom = "﻿"

    private val documents = listOf(
        "LF" to "---\ndate: 2026-09-02\n---\n\n## Log\n- one\n",
        "CRLF" to "---\r\ndate: 2026-09-02\r\n---\r\n\r\n## Log\r\n- one\r\n",
        "CR" to "---\rdate: 2026-09-02\r---\r\r## Log\r- one\r",
        "BOM + LF" to "$bom---\ndate: 2026-09-02\n---\n\n## Log\n",
        "BOM + CRLF" to "$bom---\r\ndate: 2026-09-02\r\n---\r\n\r\n## Log\r\n",
        "trailing space on the fences" to "--- \ndate: 2026-09-02\n--- \n\n## Log\n",
        "tab after the fence" to "---\t\ndate: 2026-09-02\n---\n\n## Log\n",
        "CRLF, no gap below the fence" to "---\r\ndate: 2026-09-02\r\n---\r\n## Log\r\n",
        "CRLF, three blank lines below" to "---\r\ndate: 2026-09-02\r\n---\r\n\r\n\r\n\r\n# Log\r\n",
        "closing fence ends the file" to "---\r\ndate: 2026-09-02\r\n---",
    )

    /** The read-first promise, restated for every ending: opening cannot change a note. */
    @Test
    fun `split then join is lossless whatever the line ending`() {
        for ((name, doc) in documents) {
            val (fm, body) = Frontmatter.split(doc)
            assertEquals(name, doc, Frontmatter.join(fm, body))
        }
    }

    @Test
    fun `the fence is found whatever the line ending`() {
        for ((name, doc) in documents) {
            val (fm, _) = Frontmatter.split(doc)
            assertTrue("$name: fence not recognised", fm.isNotEmpty())
            assertTrue("$name: frontmatter lost its date", fm.contains("date: 2026-09-02"))
        }
    }

    @Test
    fun `a CRLF fence splits at the same place an LF one would`() {
        val (fm, body) = Frontmatter.split("---\r\ndate: 2026-09-02\r\n---\r\n\r\n## Log\r\n")
        assertEquals("---\r\ndate: 2026-09-02\r\n---\r\n\r\n", fm)
        assertEquals("## Log\r\n", body)
    }

    @Test
    fun `a byte-order mark stays at the front of the frontmatter`() {
        val (fm, body) = Frontmatter.split("$bom---\na: 1\n---\n\n# Log\n")
        assertEquals("$bom---\na: 1\n---\n\n", fm)
        assertEquals("# Log\n", body)
    }

    /** `----` is a horizontal rule, `--- x` is prose, and frontmatter starts on line one. */
    @Test
    fun `what is not a fence is still not a fence`() {
        for (doc in listOf("----\na: 1\n---\n\n", "--- x\na: 1\n---\n\n", "\n---\na: 1\n---\n\n")) {
            assertEquals("" to doc, Frontmatter.split(doc))
        }
    }

    // ── Editing in place ─────────────────────────────────────────────────────

    @Test
    fun `upsert into a CRLF block keeps every carriage return`() {
        val fm = "---\r\ntags: [daily]\r\ndate modified: 2026-09-01 10:00\r\n---\r\n\r\n"
        assertEquals(
            "---\r\ntags: [daily]\r\ndate modified: 2026-09-03 08:41\r\n---\r\n\r\n",
            Frontmatter.upsert(fm, "date modified", "2026-09-03 08:41"),
        )
    }

    @Test
    fun `a line appended to a CRLF block is itself CRLF`() {
        val fm = "---\r\ntags: [daily]\r\n---\r\n\r\n"
        assertEquals(
            "---\r\ntags: [daily]\r\ndate modified: 2026-09-03 08:41\r\n---\r\n\r\n",
            Frontmatter.upsert(fm, "date modified", "2026-09-03 08:41"),
        )
    }

    @Test
    fun `has finds a top-level key across every ending`() {
        assertTrue(Frontmatter.has("---\r\ndate created: X\r\n---\r\n\r\n", "date created"))
        assertTrue(Frontmatter.has("$bom---\ndate created: X\n---\n\n", "date created"))
        assertTrue(Frontmatter.has("---\rdate created: X\r---\r\r", "date created"))
        // Still top-level only: nested stays invisible, and so stays undisturbed.
        assertFalse(Frontmatter.has("---\r\nmeta:\r\n  date created: X\r\n---\r\n\r\n", "date created"))
    }

    @Test
    fun `a minted fence follows the body it is going above`() {
        assertEquals(
            "---\r\ndate modified: X\r\n---\r\n\r\n",
            Frontmatter.upsert("", "date modified", "X", Frontmatter.newlineOf("# Log\r\n- one\r\n")),
        )
        assertEquals(
            "---\ndate modified: X\n---\n\n",
            Frontmatter.upsert("", "date modified", "X", Frontmatter.newlineOf("# Log\n- one\n")),
        )
    }

    // ── The whole path, as it runs on a save ─────────────────────────────────

    private val config = LinterConfig.parse(
        """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-modified":true,
           "date-modified-key":"date modified","format":"YYYY-MM-DD HH:mm"}}}""",
        "sha", 1L, 2L,
    )
    private val now = LocalDateTime.of(2026, 9, 3, 8, 41, 12)
    private val path = "Daily/2026-09-03.md"

    /** The original bug, end to end: one block in, one block out. */
    @Test
    fun `saving a Windows note does not stack a second fence`() {
        val note = "---\r\ntags: [daily]\r\ndate modified: 2026-09-01 10:00\r\n---\r\n\r\n# Log\r\n- one\r\n"
        val (fm, body) = Frontmatter.split(note)
        val stamped = YamlTimestamp.stamp(fm, config, path, now, isNew = false, body = body)
        val out = Frontmatter.join(stamped, body)

        assertEquals(
            "---\r\ntags: [daily]\r\ndate modified: 2026-09-03 08:41\r\n---\r\n\r\n# Log\r\n- one\r\n",
            out,
        )
        // One block is two fence lines, an opening and a closing. The bug made four.
        assertEquals("one block, not two", 2, Regex("(?m)^---").findAll(out).count())
    }

    /**
     * The belt-and-braces guard. A note left unstamped can be stamped on the next
     * save; a note carrying two fences has already lost its frontmatter.
     */
    @Test
    fun `a body that still opens with a fence is left alone rather than buried`() {
        // Exactly the state the bug produced: no frontmatter handed over, though
        // the note plainly has some.
        val body = "---\r\ntags: [daily]\r\n---\r\n\r\n# Log\r\n"
        assertEquals("", YamlTimestamp.stamp("", config, path, now, isNew = false, body = body))
    }

    /**
     * A note that genuinely has no frontmatter still gets one, as the plugin
     * would. The guard is narrow on purpose: dashes that do not open a fence line
     * are body text, and body text does not block a stamp.
     */
    @Test
    fun `a note with no frontmatter at all is still stamped`() {
        assertEquals(
            "---\ndate modified: 2026-09-03 08:41\n---\n\n",
            YamlTimestamp.stamp("", config, path, now, isNew = false, body = "# Log\n"),
        )
        assertEquals(
            "---\ndate modified: 2026-09-03 08:41\n---\n\n",
            YamlTimestamp.stamp("", config, path, now, isNew = false, body = "--- a: 1 --- prose\n"),
        )
    }
}
