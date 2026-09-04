package dev.vaultdrop.core

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class YamlTimestampTest {

    private val now = LocalDateTime.of(2026, 9, 3, 8, 41, 12)
    private val path = "journals/2026-09-03-Thu.md"

    private val config = LinterConfig.parse(
        """
        {"ruleConfigs":{"yaml-timestamp":{
          "enabled": true,
          "date-created": true, "date-created-key": "date created",
          "date-created-source-of-truth": "frontmatter",
          "date-modified": true, "date-modified-key": "date modified",
          "format": "YYYY-MM-DD HH:mm", "convert-to-utc": false
        }}}
        """.trimIndent(),
        "sha", 1L, 2L,
    )

    private fun stamp(fm: String, isNew: Boolean, c: LinterConfig = config) =
        YamlTimestamp.stamp(fm, c, path, now, isNew)

    /** A new note gets both, in one pass, before it is written. */
    @Test
    fun `a created note is stamped with both`() {
        assertEquals(
            "---\ndate created: 2026-09-03 08:41\ndate modified: 2026-09-03 08:41\n---\n\n",
            stamp("---\n---\n\n", isNew = true),
        )
    }

    /** A save moves the modified stamp and nothing else. */
    @Test
    fun `a save moves only the modified stamp`() {
        val fm = "---\ndate created: 2026-09-01 00:02\ndate modified: 2026-09-01 00:40\n---\n\n"
        assertEquals(
            "---\ndate created: 2026-09-01 00:02\ndate modified: 2026-09-03 08:41\n---\n\n",
            stamp(fm, isNew = false),
        )
    }

    /** VaultDrop never invents a creation date for a note it did not create. */
    @Test
    fun `a save never adds a creation date`() {
        assertEquals(
            "---\ntags: [daily]\ndate modified: 2026-09-03 08:41\n---\n\n",
            stamp("---\ntags: [daily]\n---\n\n", isNew = false),
        )
    }

    /** Source of truth "frontmatter": a value the template already wrote wins. */
    @Test
    fun `a creation date the template set is kept`() {
        val fm = "---\ndate created: 2020-01-01 09:00\n---\n\n"
        assertEquals(
            "---\ndate created: 2020-01-01 09:00\ndate modified: 2026-09-03 08:41\n---\n\n",
            stamp(fm, isNew = true),
        )
    }

    @Test
    fun `source of truth file system overwrites it instead`() {
        val fromFile = LinterConfig.parse(
            """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-created":true,
               "date-created-source-of-truth":"file system","format":"YYYY-MM-DD HH:mm"}}}""",
            "sha", 1L, 2L,
        )
        assertEquals(
            "---\ndate created: 2026-09-03 08:41\n---\n\n",
            stamp("---\ndate created: 2020-01-01 09:00\n---\n\n", isNew = true, c = fromFile),
        )
    }

    /** Off, absent, or excluded: the note comes back byte-identical. */
    @Test
    fun `an untouched note is returned unchanged`() {
        val fm = "---\ntags: [daily]\n---\n\n"
        // No plugin at all.
        assertEquals(fm, YamlTimestamp.stamp(fm, LinterConfig(), path, now, isNew = true))
        // Installed and on, but this file is on the user's ignore list.
        val excluding = LinterConfig.parse(
            """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-created":true,"date-modified":true}},
               "filesToIgnore":[{"match":"^Templates/","flags":""}]}""",
            "sha", 1L, 2L,
        )
        assertEquals(fm, YamlTimestamp.stamp(fm, excluding, "Templates/x.md", now, isNew = true))
        assertEquals(
            "---\ntags: [daily]\ndate created: 2026-09-03 08:41\ndate modified: 2026-09-03 08:41\n---\n\n",
            YamlTimestamp.stamp(fm, excluding, path, now, isNew = true),
        )
    }

    @Test
    fun `the users own key names are used`() {
        val custom = LinterConfig.parse(
            """{"ruleConfigs":{"yaml-timestamp":{"enabled":true,"date-modified":true,
               "date-modified-key":"updated","format":"YYYY-MM-DD"}}}""",
            "sha", 1L, 2L,
        )
        assertEquals(
            "---\nupdated: 2026-09-03\n---\n\n",
            stamp("---\n---\n\n", isNew = false, c = custom),
        )
    }
}
