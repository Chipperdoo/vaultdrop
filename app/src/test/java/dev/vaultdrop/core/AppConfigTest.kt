package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    private fun parse(raw: String): AppConfig = AppConfig.parse(raw, sha256 = "", lastModified = 0L, now = 0L)

    @Test
    fun `reads the vault's own trashOption`() {
        assertEquals(TrashOption.System, parse("""{"trashOption":"system"}""").trash)
        assertEquals(TrashOption.Local, parse("""{"trashOption":"local"}""").trash)
        assertEquals(TrashOption.None, parse("""{"trashOption":"none"}""").trash)
    }

    @Test
    fun `a missing key is Obsidian's default, not an error`() {
        val config = parse("""{"readableLineLength":false}""")
        assertEquals(TrashOption.System, config.trash)
        assertTrue(config.present)
    }

    @Test
    fun `an unrecognised value falls back rather than inventing behaviour`() {
        assertEquals(TrashOption.System, parse("""{"trashOption":"shred"}""").trash)
    }

    @Test
    fun `malformed json does not throw`() {
        assertEquals(TrashOption.System, parse("not json at all").trash)
    }

    @Test
    fun `a vault with no app_json is absent, not present with defaults`() {
        assertFalse(AppConfig().present)
    }

    @Test
    fun `survives the SharedPreferences round trip`() {
        val original = parse("""{"trashOption":"none"}""").copy(sha256 = "abc", lastModified = 42L)
        val restored = AppConfig.fromCache(original.toJson())
        assertEquals(TrashOption.None, restored.trash)
        assertEquals("abc", restored.sha256)
        assertEquals(42L, restored.lastModified)
        assertTrue(restored.present)
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    @Test
    fun `reads a typical vault's attachment settings`() {
        val config = parse(
            """{"attachmentFolderPath":"Assets","useMarkdownLinks":false,"newLinkFormat":"shortest"}""",
        )
        assertEquals("Assets", config.attachmentFolder)
        assertFalse(config.markdownLinks)
        assertEquals(LinkFormat.Shortest, config.linkFormat)
    }

    @Test
    fun `reads every link format and markdown links`() {
        assertEquals(LinkFormat.Relative, parse("""{"newLinkFormat":"relative"}""").linkFormat)
        assertEquals(LinkFormat.Absolute, parse("""{"newLinkFormat":"absolute"}""").linkFormat)
        assertTrue(parse("""{"useMarkdownLinks":true}""").markdownLinks)
    }

    @Test
    fun `missing attachment keys are Obsidian's defaults`() {
        val config = parse("""{"trashOption":"local"}""")
        assertEquals("/", config.attachmentFolder)
        assertFalse(config.markdownLinks)
        assertEquals(LinkFormat.Shortest, config.linkFormat)
    }

    @Test
    fun `an unrecognised link format falls back to shortest`() {
        assertEquals(LinkFormat.Shortest, parse("""{"newLinkFormat":"longest"}""").linkFormat)
    }

    @Test
    fun `the attachment settings survive the round trip`() {
        val original = parse(
            """{"attachmentFolderPath":"./img","useMarkdownLinks":true,"newLinkFormat":"relative"}""",
        ).copy(sha256 = "abc", lastModified = 42L)
        val restored = AppConfig.fromCache(original.toJson())
        assertEquals("./img", restored.attachmentFolder)
        assertTrue(restored.markdownLinks)
        assertEquals(LinkFormat.Relative, restored.linkFormat)
        assertTrue(restored.present)
        assertEquals(original, restored)
    }

    /**
     * Its stamp still matches the file, so if it came back present the probe
     * would call the file unchanged and photos would go to the vault root.
     */
    @Test
    fun `a cache from before the attachment settings is read again`() {
        val old = """{"trash":"local","present":true,"sha256":"abc","lastModified":42,"lastReadAt":7}"""
        val restored = AppConfig.fromCache(old)
        assertFalse(restored.present)
        assertEquals(TrashOption.Local, restored.trash)
        assertEquals("/", restored.attachmentFolder)
    }
}
