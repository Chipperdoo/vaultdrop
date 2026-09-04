package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WikiIndexTest {

    private fun index(vararg names: String) =
        WikiIndex(names.map { IndexEntry(name = it, path = "Daily/$it.md") })

    @Test
    fun `byPath addresses every entry`() {
        val index = index("a", "b")
        assertEquals(2, index.byPath.size)
        assertEquals("b", index.byPath["Daily/b.md"]?.name)
        assertEquals(null, index.byPath["missing.md"])
    }

    /** The `[[` picker wants what you were most likely typing towards first. */
    @Test
    fun `prefix matches come before contains matches`() {
        val hits = index("Reading list", "My reading", "Read me").nameHits("read", limit = 4)
        assertEquals(listOf("Reading list", "Read me", "My reading"), hits.matches.map { it.name })
    }

    /**
     * The count used to be a second full scan of its own. It comes off the same
     * pass now, so it still has to count every match rather than the rows kept.
     */
    @Test
    fun `total counts every match, not just the ones returned`() {
        val hits = index("a1", "a2", "a3", "a4", "a5", "b1").nameHits("a", limit = 2)
        assertEquals(2, hits.matches.size)
        assertEquals(5, hits.total)
    }

    @Test
    fun `matching ignores case in both directions`() {
        assertEquals(1, index("Groceries").nameHits("GROC", limit = 4).total)
        assertEquals(1, index("GROCERIES").nameHits("groc", limit = 4).total)
    }

    @Test
    fun `an empty query is the whole vault, capped at the limit`() {
        val hits = index("a", "b", "c").nameHits("  ", limit = 2)
        assertEquals(2, hits.matches.size)
        assertEquals(3, hits.total)
    }

    /**
     * Offsets have to survive the fold, or a snippet window would point at the
     * wrong text. `String.lowercase()` does not guarantee that; this does.
     */
    @Test
    fun `folding case never changes a string's length`() {
        for (s in listOf("İstanbul", "ǅungla", "ﬁle", "Ω", "ASCII")) {
            assertEquals(s, s.length, s.foldCase().length)
        }
    }
}
