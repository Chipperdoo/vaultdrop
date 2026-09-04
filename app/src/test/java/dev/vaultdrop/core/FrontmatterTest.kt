package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FrontmatterTest {

    /** The read-first promise: opening a note must not be able to change it. */
    @Test
    fun `split then join is lossless`() {
        val documents = listOf(
            "---\ndate: 2026-09-02\n---\n\n## 09:14\nReviewed the [[Q3 Retro]] doc.\n",
            "---\ndate: 2026-09-02\ntags: [daily]\n---\n\n## Log\n- \n",
            "## 09:14\njust a body, no frontmatter\n",
            "---\nan unclosed fence is not frontmatter\n",
            // The gaps the old trim-and-re-add pass could not reproduce.
            "---\ndate: 2026-09-02\n---\n\n\n\n## Log\n",
            "---\ndate: 2026-09-02\n---\n## Log\n",
            "---\ndate: 2026-09-02\n---\n",
            "",
        )
        for (doc in documents) {
            val (fm, body) = Frontmatter.split(doc)
            assertEquals(doc, Frontmatter.join(fm, body))
        }
    }

    /** The gap after the fence is the frontmatter's, so join can just concatenate. */
    @Test
    fun `the fence and the gap below it stay with the frontmatter`() {
        val (fm, body) = Frontmatter.split("---\ndate: 2026-09-02\n---\n\n## 09:14\n")
        assertEquals("---\ndate: 2026-09-02\n---\n\n", fm)
        assertEquals("## 09:14\n", body)
    }

    @Test
    fun `a body with no frontmatter is untouched`() {
        val (fm, body) = Frontmatter.split("just text")
        assertEquals("", fm)
        assertEquals("just text", body)
    }
}
