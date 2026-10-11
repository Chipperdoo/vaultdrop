package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentsTest {

    /** `journals/2026-09-29-Tue.md`, a dated note in one flat folder. */
    private val flat = NotePath(listOf("journals"), "2026-09-29-Tue.md")

    /** `YYYY/MM/DD` under a folder: the note's own folder is two levels of date. */
    private val nested = NotePath(listOf("Journal", "2026", "09"), "29.md")

    /** A note at the vault root, where "the note's folder" is the root too. */
    private val root = NotePath(emptyList(), "2026-09-29.md")

    private fun folder(setting: String, note: NotePath): String =
        Attachments.folderFor(setting, note).joinToString("/")

    // ── Where a file goes ────────────────────────────────────────────────────

    @Test
    fun `slash or empty is the vault root`() {
        for (note in listOf(flat, nested, root)) {
            assertEquals("", folder("/", note))
            assertEquals("", folder("", note))
        }
    }

    @Test
    fun `dot and dot-slash are the note's own folder`() {
        assertEquals("journals", folder("./", flat))
        assertEquals("journals", folder(".", flat))
        assertEquals("Journal/2026/09", folder("./", nested))
        assertEquals("Journal/2026/09", folder(".", nested))
        assertEquals("", folder("./", root))
    }

    @Test
    fun `dot-slash-sub is a subfolder of the note's folder`() {
        assertEquals("journals/img", folder("./img", flat))
        assertEquals("Journal/2026/09/img", folder("./img", nested))
        assertEquals("img", folder("./img", root))
        assertEquals("journals/img/raw", folder("./img/raw/", flat))
    }

    @Test
    fun `a plain folder is used as given, wherever the note is`() {
        assertEquals("Assets", folder("Assets", flat))
        assertEquals("Assets", folder("Assets", nested))
        assertEquals("Assets", folder("Assets/", root))
        assertEquals("Assets", folder("/Assets", flat))
        assertEquals("Media/Photos", folder("Media/Photos", nested))
    }

    @Test
    fun `backslashes are separators, as in Obsidian's normalizePath`() {
        assertEquals("Media/Photos", folder("Media\\Photos", flat))
    }

    @Test
    fun `dot-dot never climbs out of the vault`() {
        assertEquals("Journal/2026/Assets", folder("./../Assets", nested))
        assertEquals("Assets", folder("../../Assets", flat))
    }

    // ── What it is called ────────────────────────────────────────────────────

    @Test
    fun `a free name is used as it is`() {
        assertEquals("Photo 20260929174501.jpg", Attachments.availableName("Photo 20260929174501", "jpg") { false })
    }

    /**
     * Unconfirmed against desktop Obsidian: the ` 1` start comes from secondary
     * sources. See "Checks against desktop Obsidian" in docs/plans/multimedia.md.
     */
    @Test
    fun `a taken name gets Obsidian's suffix, starting at 1`() {
        val taken = setOf("PXL_1.jpg", "PXL_1 1.jpg")
        assertEquals("PXL_1 2.jpg", Attachments.availableName("PXL_1", "jpg") { it in taken })
    }

    @Test
    fun `attachments do not dedup the way the trash does`() {
        val taken = setOf("a.jpg")
        assertEquals("a 1.jpg", Attachments.availableName("a", "jpg") { it in taken })
        assertEquals("a 2.jpg", DailyNote.trashName("a.jpg", 1))
    }

    @Test
    fun `the attempts are capped`() {
        assertNull(Attachments.availableName("a", "jpg") { true })
    }

    @Test
    fun `no extension means no trailing dot`() {
        assertEquals("README", Attachments.availableName("README", "") { false })
    }

    @Test
    fun `a name cannot become a path or hide`() {
        assertEquals("a-b", Attachments.cleanName("a/b"))
        assertEquals("a-b", Attachments.cleanName("a\\b"))
        assertEquals("hidden", Attachments.cleanName(".hidden"))
        assertEquals("a-b-c", Attachments.cleanName("a:b?c"))
        assertEquals("note -1 -x-", Attachments.cleanName("note #1 [x]"))
        assertEquals("Screenshot_20260105-093000", Attachments.cleanName("Screenshot_20260105-093000"))
        assertNull(Attachments.cleanName(" .. "))
    }

    // ── The link ─────────────────────────────────────────────────────────────

    private val photo = "Assets/Photo 20260929174501.jpg"

    private fun embed(markdown: Boolean, format: LinkFormat, target: String = photo, note: NotePath = flat): String =
        Attachments.embed(target, note, AppConfig(markdownLinks = markdown, linkFormat = format))

    @Test
    fun `wikilinks follow newLinkFormat`() {
        assertEquals("![[Photo 20260929174501.jpg]]", embed(false, LinkFormat.Shortest))
        assertEquals("![[Assets/Photo 20260929174501.jpg]]", embed(false, LinkFormat.Absolute))
        assertEquals("![[../Assets/Photo 20260929174501.jpg]]", embed(false, LinkFormat.Relative))
    }

    @Test
    fun `markdown links encode the space and take the path per format`() {
        assertEquals("![](Photo%2020260929174501.jpg)", embed(true, LinkFormat.Shortest))
        assertEquals("![](Assets/Photo%2020260929174501.jpg)", embed(true, LinkFormat.Absolute))
        assertEquals("![](../Assets/Photo%2020260929174501.jpg)", embed(true, LinkFormat.Relative))
    }

    @Test
    fun `relative paths walk up from the note's real folder`() {
        assertEquals("![[../../../Assets/Photo 20260929174501.jpg]]", embed(false, LinkFormat.Relative, note = nested))
        assertEquals("![[Assets/Photo 20260929174501.jpg]]", embed(false, LinkFormat.Relative, note = root))
        assertEquals("![[x.jpg]]", embed(false, LinkFormat.Relative, target = "journals/x.jpg"))
        assertEquals("![[img/x.jpg]]", embed(false, LinkFormat.Relative, target = "journals/img/x.jpg"))
        assertEquals("![[../x.jpg]]", embed(false, LinkFormat.Relative, target = "x.jpg"))
        assertEquals("![[x.jpg]]", embed(false, LinkFormat.Relative, target = "Journal/2026/09/x.jpg", note = nested))
    }

    /**
     * Unconfirmed against desktop Obsidian: this is obsidian-dev-utils'
     * `encodeUrl`, which leaves `#`, `(`, `)`, `%` and non-ASCII alone. See
     * "Checks against desktop Obsidian" in docs/plans/multimedia.md.
     */
    @Test
    fun `markdown links encode only what Obsidian encodes`() {
        assertEquals("![](a%20#1%20(b)%20100%.jpg)", embed(true, LinkFormat.Shortest, target = "a #1 (b) 100%.jpg"))
        assertEquals("![](café.jpg)", embed(true, LinkFormat.Shortest, target = "café.jpg"))
        assertEquals("![](a%5Cb.jpg)", embed(true, LinkFormat.Absolute, target = "a\\b.jpg"))
        assertEquals("![](a%1Fb.jpg)", embed(true, LinkFormat.Absolute, target = "a\u001Fb.jpg"))
    }
}
