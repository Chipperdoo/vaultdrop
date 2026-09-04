package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsed against the `yaml-timestamp` block as the plugin actually writes it —
 * this fixture follows the layout of a
 * `.obsidian/plugins/obsidian-linter/data.json`.
 */
class LinterConfigTest {

    private val real = """
        {
          "ruleConfigs": {
            "yaml-timestamp": {
              "enabled": true,
              "date-created": true,
              "date-created-key": "date created",
              "date-created-source-of-truth": "frontmatter",
              "date-modified": true,
              "date-modified-key": "date modified",
              "date-modified-source-of-truth": "user or Linter edits",
              "format": "YYYY-MM-DD HH:mm",
              "convert-to-utc": false,
              "update-on-file-contents-updated": "after 10 seconds"
            }
          },
          "foldersToIgnore": [],
          "filesToIgnore": [
            { "match": "^Daily Note Template\\.md$", "flags": "", "label": "" },
            { "match": "^Templates/Daily Note Template\\.md$", "flags": "", "label": "" },
            { "match": "^Daily Notes/2026-01-05-Mon\\.md$", "flags": "", "label": "" }
          ]
        }
    """.trimIndent()

    private fun parse(raw: String = real) = LinterConfig.parse(raw, "sha", 1L, 2L)

    @Test
    fun `reads the rule as the plugin writes it`() {
        val c = parse()
        assertTrue(c.present)
        assertTrue(c.enabled)
        assertTrue(c.stampCreated)
        assertEquals("date created", c.createdKey)
        assertTrue(c.createdFromFrontmatter)
        assertTrue(c.stampModified)
        assertEquals("date modified", c.modifiedKey)
        assertEquals("YYYY-MM-DD HH:mm", c.format)
        assertFalse(c.utc)
    }

    /** The format is a Moment string, translated by the same path as every other. */
    @Test
    fun `the stamp matches what Obsidian would have written`() {
        assertEquals(
            "2026-09-03 08:41",
            parse().stamp(java.time.LocalDateTime.of(2026, 9, 3, 8, 41, 12)),
        )
    }

    @Test
    fun `files the user excluded are left alone`() {
        val c = parse()
        assertTrue(c.appliesTo("journals/2026-09-03-Thu.md"))
        assertFalse(c.appliesTo("Templates/Daily Note Template.md"))
        assertFalse(c.appliesTo("Daily Notes/2026-01-05-Mon.md"))
    }

    @Test
    fun `an ignored folder covers what is under it`() {
        val c = LinterConfig.parse(
            """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-modified":true}},
               "foldersToIgnore":["Archive","x/y"]}""",
            "sha", 1L, 2L,
        )
        assertFalse(c.appliesTo("Archive/old.md"))
        assertFalse(c.appliesTo("x/y/old.md"))
        assertTrue(c.appliesTo("Archived Notes/new.md"))
        assertTrue(c.appliesTo("journals/new.md"))
    }

    /** No plugin, rule switched off, or both timestamps off — all mean "don't touch". */
    @Test
    fun `nothing is stamped unless the rule asks for it`() {
        assertFalse(LinterConfig().appliesTo("journals/a.md"))
        assertFalse(parse("""{"ruleConfigs":{"yaml-timestamp":{"enabled":false}}}""").appliesTo("journals/a.md"))
        assertFalse(parse("""{}""").appliesTo("journals/a.md"))
        assertFalse(
            parse("""{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-created":false,"date-modified":false}}}""")
                .appliesTo("journals/a.md"),
        )
    }

    @Test
    fun `defaults fill in for keys the plugin has not written`() {
        val c = parse("""{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-modified":true}}}""")
        assertEquals(LinterConfig.DEFAULT_CREATED_KEY, c.createdKey)
        assertEquals(LinterConfig.DEFAULT_MODIFIED_KEY, c.modifiedKey)
        assertEquals(LinterConfig.DEFAULT_FORMAT, c.format)
    }

    /** A pattern java.util.regex will not take must not disable the whole feature. */
    @Test
    fun `an unreadable ignore pattern is dropped, not treated as a match`() {
        val c = parse(
            """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-modified":true}},
               "filesToIgnore":[{"match":"(?<broken","flags":""}]}""",
        )
        assertTrue(c.appliesTo("journals/2026-09-03-Thu.md"))
    }

    @Test
    fun `survives the SharedPreferences round trip`() {
        val c = parse()
        val back = LinterConfig.fromCache(c.toJson())!!
        assertEquals(c.enabled, back.enabled)
        assertEquals(c.createdKey, back.createdKey)
        assertEquals(c.modifiedKey, back.modifiedKey)
        assertEquals(c.format, back.format)
        assertEquals(c.createdFromFrontmatter, back.createdFromFrontmatter)
        assertEquals(c.sha256, back.sha256)
        assertEquals(c.lastModified, back.lastModified)
        assertFalse(back.appliesTo("Templates/Daily Note Template.md"))
    }
}
