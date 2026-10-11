package dev.vaultdrop.core

import dev.vaultdrop.core.PhotoNames.Name
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PhotoNamesTest {

    private val now = LocalDateTime.of(2026, 9, 29, 17, 45, 1)

    private fun picked(displayName: String?, mimeExt: String?) = PhotoNames.picked(displayName, mimeExt, now)

    // ── Naming ───────────────────────────────────────────────────────────────

    @Test
    fun `a camera photo is the prefix and a timestamp to the second`() {
        assertEquals("Photo 20260929174501", PhotoNames.stamped(now))
        assertEquals("Photo 20260102030405", PhotoNames.stamped(LocalDateTime.of(2026, 1, 2, 3, 4, 5)))
    }

    @Test
    fun `a picked photo keeps its own name`() {
        assertEquals(Name("IMG_0001", "jpg"), picked("IMG_0001.jpg", "jpg"))
        assertEquals(Name("Screenshot_20260105-093000", "png"), picked("Screenshot_20260105-093000.png", "png"))
    }

    @Test
    fun `only the last dot splits off the extension`() {
        assertEquals(Name("PXL_20260105_093000123.MP", "jpg"), picked("PXL_20260105_093000123.MP.jpg", "jpg"))
    }

    @Test
    fun `the name's own extension wins over the MIME type's, case and all`() {
        assertEquals(Name("IMG_0002", "HEIC"), picked("IMG_0002.HEIC", "heic"))
        assertEquals(Name("IMG_0002", "jpeg"), picked("IMG_0002.jpeg", "jpg"))
    }

    @Test
    fun `a name with no extension takes the MIME type's`() {
        assertEquals(Name("IMG_0003", "webp"), picked("IMG_0003", "webp"))
        assertEquals(Name("IMG_0003", "jpg"), picked("IMG_0003.", "jpg"))
    }

    @Test
    fun `what follows the last dot has to look like an extension`() {
        assertEquals(Name("Scan 2026.01.31", "png"), picked("Scan 2026.01.31", "png"))
        assertEquals(Name("notes.draft copy", "jpg"), picked("notes.draft copy", "jpg"))
        assertEquals(Name("photo.backup", "jpg"), picked("photo.backup", "jpg"))
    }

    @Test
    fun `no name at all gets the timestamp`() {
        assertEquals(Name("Photo 20260929174501", "jpg"), picked(null, "jpg"))
        assertEquals(Name("Photo 20260929174501", "jpg"), picked("   ", "jpg"))
        assertEquals(Name("Photo 20260929174501", "png"), picked(".png", "png"))
    }

    @Test
    fun `a name with nothing usable left gets the timestamp`() {
        assertEquals(Name("Photo 20260929174501", "png"), picked("....png", null))
    }

    @Test
    fun `characters the vault write would swap are swapped here first`() {
        assertEquals(Name("Trip- day 1-2", "jpg"), picked("Trip: day 1#2.jpg", "jpg"))
    }

    @Test
    fun `with no extension anywhere the file goes without one`() {
        assertEquals(Name("IMG_0004", ""), picked("IMG_0004", null))
        assertEquals(Name("IMG_0004", ""), picked("IMG_0004", "x-weird+type"))
    }

    // ── What Obsidian draws ──────────────────────────────────────────────────

    @Test
    fun `Obsidian draws the formats its file-formats page lists`() {
        for (ext in listOf("avif", "bmp", "gif", "jpeg", "jpg", "png", "svg", "webp")) {
            assertTrue(ext, PhotoNames.rendersInObsidian(ext))
        }
        assertEquals(8, PhotoNames.RENDERED.size)
    }

    @Test
    fun `the check ignores case, since the name's extension keeps its own`() {
        assertTrue(PhotoNames.rendersInObsidian("JPG"))
        assertTrue(PhotoNames.rendersInObsidian("Png"))
    }

    @Test
    fun `HEIC and the rest are copied but not drawn`() {
        for (ext in listOf("heic", "HEIF", "tiff", "dng", "")) {
            assertFalse(ext, PhotoNames.rendersInObsidian(ext))
        }
    }

    @Test
    fun `the camera's own extension is one Obsidian draws`() {
        assertTrue(PhotoNames.rendersInObsidian(PhotoNames.CAMERA_EXT))
    }
}
