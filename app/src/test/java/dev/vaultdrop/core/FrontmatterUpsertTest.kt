package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontmatterUpsertTest {

    /** The shape the vault's own daily template produces: a fence with nothing in it. */
    @Test
    fun `writes into an empty fence`() {
        val fm = "---\n---\n\n"
        assertEquals(
            "---\ndate created: 2026-09-03 08:41\n---\n\n",
            Frontmatter.upsert(fm, "date created", "2026-09-03 08:41"),
        )
    }

    @Test
    fun `appends above the closing fence`() {
        val fm = "---\ntags: [daily]\n---\n\n"
        assertEquals(
            "---\ntags: [daily]\ndate modified: 2026-09-03 08:41\n---\n\n",
            Frontmatter.upsert(fm, "date modified", "2026-09-03 08:41"),
        )
    }

    @Test
    fun `replaces a key that is already there, in place`() {
        val fm = "---\ndate created: 2026-09-01 00:02\ndate modified: 2026-09-01 00:40\ntags: [daily]\n---\n\n"
        assertEquals(
            "---\ndate created: 2026-09-01 00:02\ndate modified: 2026-09-03 08:41\ntags: [daily]\n---\n\n",
            Frontmatter.upsert(fm, "date modified", "2026-09-03 08:41"),
        )
    }

    /** No fence at all means writing one, which is what the plugin does too. */
    @Test
    fun `creates a fence when there is none`() {
        assertEquals(
            "---\ndate modified: 2026-09-03 08:41\n---\n\n",
            Frontmatter.upsert("", "date modified", "2026-09-03 08:41"),
        )
    }

    @Test
    fun `the gap below the fence is preserved`() {
        val fm = "---\na: 1\n---\n\n\n"
        assertTrue(Frontmatter.upsert(fm, "b", "2").endsWith("---\n\n\n"))
        assertEquals("---\na: 1\nb: 2\n---\n\n\n", Frontmatter.upsert(fm, "b", "2"))
    }

    /** Top-level only — a nested key of the same name is neither found nor disturbed. */
    @Test
    fun `a nested key is not mistaken for a top-level one`() {
        val fm = "---\nmeta:\n  date modified: old\n---\n\n"
        assertFalse(Frontmatter.has(fm, "date modified"))
        assertEquals(
            "---\nmeta:\n  date modified: old\ndate modified: new\n---\n\n",
            Frontmatter.upsert(fm, "date modified", "new"),
        )
    }

    @Test
    fun `a key that is a prefix of another is not confused with it`() {
        val fm = "---\ndate created: X\n---\n\n"
        assertFalse(Frontmatter.has(fm, "date"))
        assertTrue(Frontmatter.has(fm, "date created"))
        assertEquals("---\ndate created: X\ndate: Y\n---\n\n", Frontmatter.upsert(fm, "date", "Y"))
    }

    @Test
    fun `split then upsert then join leaves the body alone`() {
        val note = "---\n---\n\n###### **Daily Log**\n\n- one\n"
        val (fm, body) = Frontmatter.split(note)
        val out = Frontmatter.join(Frontmatter.upsert(fm, "date modified", "2026-09-03 08:41"), body)
        assertEquals("---\ndate modified: 2026-09-03 08:41\n---\n\n###### **Daily Log**\n\n- one\n", out)
    }
}
