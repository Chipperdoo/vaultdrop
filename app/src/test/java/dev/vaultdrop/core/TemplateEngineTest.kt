package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import java.time.LocalDateTime
import org.junit.Test

class TemplateEngineTest {

    private val now = LocalDateTime.of(2026, 9, 2, 14, 32, 45)
    private val config = VaultConfig(format = "YYYY-MM-DD ddd")

    private fun render(template: String) = TemplateEngine.render(template, config, now)

    @Test
    fun `date uses the configured format`() {
        assertEquals("2026-09-02 Wed", render("{{date}}"))
    }

    @Test
    fun `time is always HH mm`() {
        assertEquals("14:32", render("{{time}}"))
    }

    @Test
    fun `an inner format overrides the configured one`() {
        assertEquals("2026-09-02", render("{{date:YYYY-MM-DD}}"))
        assertEquals("Wednesday", render("{{date:dddd}}"))
    }

    @Test
    fun `whitespace inside the braces is tolerated`() {
        assertEquals("2026-09-02 Wed", render("{{ date }}"))
    }

    @Test
    fun `unknown variables are left for Obsidian to deal with`() {
        assertEquals("{{title}} and 2026-09-02 Wed", render("{{title}} and {{date}}"))
    }

    @Test
    fun `renders a whole daily template`() {
        assertEquals(
            "---\ndate: 2026-09-02\n---\n\n## 14:32\n- \n",
            render("---\ndate: {{date:YYYY-MM-DD}}\n---\n\n## {{time}}\n- \n"),
        )
    }
}
