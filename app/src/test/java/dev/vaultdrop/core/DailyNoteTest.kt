package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import java.time.LocalDate
import org.junit.Test

class DailyNoteTest {

    private val date = LocalDate.of(2026, 9, 2)

    private fun path(format: String, folder: String): String =
        DailyNote.resolve(VaultConfig(format = format, folder = folder), date).relativePath

    @Test
    fun `resolves under the configured folder`() {
        assertEquals("Daily Notes/2026-09-02 Wed.md", path("YYYY-MM-DD ddd", "Daily Notes"))
    }

    @Test
    fun `empty folder means the vault root`() {
        assertEquals("2026-09-02.md", path("YYYY-MM-DD", ""))
    }

    @Test
    fun `a slash in the format becomes a directory separator`() {
        assertEquals("Journal/2026/09/02.md", path("YYYY/MM/DD", "Journal"))
    }

    @Test
    fun `nested folders are kept`() {
        assertEquals("a/b/c/2026-09-02.md", path("YYYY-MM-DD", "a/b/c"))
    }

    @Test
    fun `path-unsafe characters become dashes`() {
        assertEquals("Daily/2026-09-02 00-00.md", path("YYYY-MM-DD HH:mm", "Daily"))
        assertEquals("-a-b-.md", DailyNote.sanitize("""?a"b<""") + ".md")
    }

    @Test
    fun `the label matches the filename stem`() {
        val config = VaultConfig(format = "YYYY-MM-DD ddd", folder = "Daily Notes")
        assertEquals("2026-09-02 Wed", DailyNote.label(config, date))
        assertEquals("2026-09-02 Wed.md", DailyNote.resolve(config, date).fileName)
    }

    /**
     * [DayIndex] tests for [DailyLayout.relativePath] and notes are written to
     * [DailyNote.resolve]. If those two ever disagreed the day index would offer
     * steps onto days whose notes are somewhere else, so pin them together over
     * every shape of format and folder the app has to handle.
     */
    @Test
    fun `the cheap path and the resolved path are the same path`() {
        val formats = listOf(
            "YYYY-MM-DD", "YYYY/MM/DD", "YYYY-MM-DD ddd", "YYYY-MM-DD HH:mm",
            "[Daily] YYYY-MM-DD", "Do MMM", "", "/DD/", "YYYY//MM", " YYYY-MM-DD ",
        )
        val folders = listOf("", "Daily", "a/b/c", "/Journal/", "Da:ily")
        for (format in formats) {
            for (folder in folders) {
                val config = VaultConfig(format = format, folder = folder)
                val layout = DailyLayout(config)
                for (back in 0 until 40) {
                    val day = date.minusDays(back.toLong())
                    assertEquals(
                        "format=$format folder=$folder day=$day",
                        DailyNote.resolve(config, day).relativePath,
                        layout.relativePath(day),
                    )
                }
            }
        }
    }

    /** The builder is reused across calls, so a stale tail would show up here. */
    @Test
    fun `a reused layout does not leak one day into the next`() {
        val layout = DailyLayout(VaultConfig(format = "YYYY-M-D", folder = "Daily"))
        assertEquals("Daily/2026-9-2.md", layout.relativePath(date))
        assertEquals("Daily/2026-12-30.md", layout.relativePath(LocalDate.of(2026, 12, 30)))
        assertEquals("Daily/2026-9-2.md", layout.relativePath(date))
    }

    // ── Trash naming, as Obsidian names it ───────────────────────────────────

    @Test
    fun `the first trashed copy keeps its own name`() {
        assertEquals("2026-09-02-Wed.md", DailyNote.trashName("2026-09-02-Wed.md", 0))
    }

    @Test
    fun `trash is flat - the folder the note lived in is not part of the name`() {
        val path = DailyNote.resolve(VaultConfig(format = "YYYY-MM-DD-ddd", folder = "journals"), date)
        assertEquals("2026-09-02-Wed.md", DailyNote.trashName(path.fileName, 0))
    }

    @Test
    fun `a collision takes Obsidian's numeric suffix, starting at 2`() {
        assertEquals("2026-09-02-Wed 2.md", DailyNote.trashName("2026-09-02-Wed.md", 1))
        assertEquals("2026-09-02-Wed 3.md", DailyNote.trashName("2026-09-02-Wed.md", 2))
    }

    @Test
    fun `a nested format contributes only its leaf name`() {
        val path = DailyNote.resolve(VaultConfig(format = "YYYY/MM/DD", folder = "Journal"), date)
        assertEquals("02.md", DailyNote.trashName(path.fileName, 0))
        assertEquals("02 2.md", DailyNote.trashName(path.fileName, 1))
    }

    @Test
    fun `a name with dots keeps its extension on the suffix`() {
        assertEquals("1. Projects 2.md", DailyNote.trashName("1. Projects.md", 1))
    }
}
