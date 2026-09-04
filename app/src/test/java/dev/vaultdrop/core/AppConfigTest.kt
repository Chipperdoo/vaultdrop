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
}
