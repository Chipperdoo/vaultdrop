package dev.vaultdrop.core

import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates the Moment.js format strings Obsidian stores in
 * `.obsidian/daily-notes.json` into `java.time` patterns.
 *
 * Only the tokens the brief lists are translated. Anything else is emitted as a
 * quoted literal, so an unknown token degrades into visible text rather than
 * throwing at format time and losing the user their note.
 */
object MomentFormat {

    /** Longest-first: `dddd` must win over `ddd`, `YYYY` over `YY`, `mm` over `m`. */
    private val TOKENS: List<Pair<String, String>> = listOf(
        "dddd" to "EEEE",
        "YYYY" to "yyyy",
        "ddd" to "E",
        "YY" to "yy",
        "MM" to "MM",
        "DD" to "dd",
        "HH" to "HH",
        "mm" to "mm",
        "ss" to "ss",
        "M" to "M",
        "D" to "d",
        "H" to "H",
        "m" to "m",
        "A" to "a",
    )

    /**
     * Obsidian filenames should not move when the phone's locale does, so every
     * formatter is pinned to English — the default Moment locale on the desktop.
     */
    private val LOCALE: Locale = Locale.ENGLISH

    fun toJavaPattern(moment: String): String {
        val out = StringBuilder()
        val literal = StringBuilder()

        fun flushLiteral() {
            if (literal.isEmpty()) return
            // A literal run is quoted wholesale; an embedded quote doubles.
            out.append('\'').append(literal.toString().replace("'", "''")).append('\'')
            literal.setLength(0)
        }

        var i = 0
        while (i < moment.length) {
            val c = moment[i]

            // Moment's own escape: [Daily] emits "Daily" verbatim.
            if (c == '[') {
                val close = moment.indexOf(']', i + 1)
                if (close != -1) {
                    literal.append(moment, i + 1, close)
                    i = close + 1
                    continue
                }
            }

            // A trailing `o` is Moment's ordinal suffix — `Do`, `Mo`, `wo`. None of
            // them is translated, so the whole token has to fall through as text;
            // matching the `D` alone would render `Do` as `2o`.
            val token = TOKENS.firstOrNull { (m, _) -> moment.startsWith(m, i) }
                ?.takeIf { (m, _) -> moment.getOrNull(i + m.length) != 'o' }
            if (token != null) {
                flushLiteral()
                out.append(token.second)
                i += token.first.length
            } else {
                literal.append(c)
                i++
            }
        }
        flushLiteral()
        return out.toString()
    }

    /**
     * Formatters are immutable and thread-safe, and a session uses a handful of
     * distinct format strings at most — the vault's own, plus whatever a template
     * asks for inline. Building one means translating the pattern and assembling
     * a composite printer, which is far too much work to repeat for every label
     * on the top bar and every row of the day sheet.
     */
    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    /**
     * @return a formatter for [moment], or a formatter for [VaultConfig.DEFAULT_FORMAT]
     *   if the translated pattern is not one `java.time` will accept.
     */
    fun formatter(moment: String): DateTimeFormatter =
        formatters.computeIfAbsent(moment) { pattern ->
            runCatching {
                DateTimeFormatter.ofPattern(toJavaPattern(pattern), LOCALE)
            }.getOrElse {
                DateTimeFormatter.ofPattern(toJavaPattern(VaultConfig.DEFAULT_FORMAT), LOCALE)
            }
        }
}
