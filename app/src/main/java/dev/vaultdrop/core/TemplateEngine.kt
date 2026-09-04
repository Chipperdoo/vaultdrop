package dev.vaultdrop.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Expands the template variables the brief lists. Everything else in the
 * template file is copied through untouched — VaultDrop is not a templating
 * engine, it just fills in the date and time Obsidian would have.
 */
object TemplateEngine {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /**
     * `{{date}}`, `{{time}}`, and `{{date:FORMAT}}` with a Moment format inside.
     *
     * Both closing braces are escaped. `java.util.regex` on the JVM tolerates a
     * bare `}`, but Android's regex is ICU-backed and rejects it, so an unescaped
     * one compiles green in unit tests and then throws
     * `ExceptionInInitializerError` out of `<clinit>` on the device.
     */
    private val VARIABLE = Regex("""\{\{\s*(date|time)\s*(?::([^}]*))?\s*\}\}""", RegexOption.IGNORE_CASE)

    fun render(template: String, config: VaultConfig, now: LocalDateTime): String =
        VARIABLE.replace(template) { match ->
            val name = match.groupValues[1].lowercase(Locale.ROOT)
            val inner = match.groupValues[2].trim()
            when {
                name == "date" && inner.isNotEmpty() -> MomentFormat.formatter(inner).format(now)
                name == "date" -> MomentFormat.formatter(config.format).format(now)
                inner.isNotEmpty() -> MomentFormat.formatter(inner).format(now)
                else -> TIME.format(now)
            }
        }
}
