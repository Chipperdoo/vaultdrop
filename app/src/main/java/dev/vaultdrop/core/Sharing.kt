package dev.vaultdrop.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What another app shares into VaultDrop, turned into text for the note and
 * names for the vault. Pure. The view model applies it, and [VaultFiles] reads
 * the shared files.
 *
 * A share goes at the end of today's note after a blank line, so it reads as
 * its own paragraph whatever the note ended with. Whatever else arrives in the
 * same share follows it at the caret, through [splice].
 */
object Sharing {

    /**
     * The line breaks [text] needs at its end so that whatever comes next starts
     * after a blank line. Nothing for a note with nothing in it.
     *
     * Breaks already there count, and a line of only spaces counts as blank, as
     * it does in Markdown. Nothing is taken away, so a note that already ends in
     * three blank lines keeps them.
     */
    fun separator(text: CharSequence): String {
        var end = text.length
        var breaks = 0
        while (end > 0 && text[end - 1].isWhitespace()) {
            if (text[end - 1] == '\n') breaks++
            end--
        }
        val atLineStart = text.isEmpty() || text[text.length - 1] == '\n'
        val eol = lineEnding(text)
        if (end == 0) return if (atLineStart) "" else eol
        val needed = if (atLineStart) 2 - breaks else maxOf(1, 2 - breaks)
        return eol.repeat(needed.coerceAtLeast(0))
    }

    /**
     * Where a shared [insert] goes in [text]: at the end, after a blank line,
     * with the caret after it.
     *
     * A [block], like an embed, ends with a line break and leaves the caret on
     * the line below, the way [splice] places one. Text leaves the caret at its
     * own end, so a line of context can follow on.
     *
     * Line breaks in [insert] follow the note's own, so a CRLF note stays CRLF.
     * Only ever inserts.
     */
    fun append(text: CharSequence, insert: String, block: Boolean): Edit {
        val end = text.length
        if (insert.isEmpty()) return Edit(end, end, "", end)
        val eol = lineEnding(text)
        val body = insert.replace("\r\n", "\n").let { if (eol == "\n") it else it.replace("\n", eol) }
        val replacement = separator(text) + body + if (block) eol else ""
        return Edit(end, end, replacement, end + replacement.length)
    }

    /**
     * Shared text as it should land: blank lines before it and whitespace after
     * it dropped, so the separator is the only gap. Leading spaces on the first
     * line stay, since they can be indentation.
     *
     * @return null when there is nothing left.
     */
    fun cleanText(raw: CharSequence?): String? {
        if (raw == null) return null
        var start = 0
        var lineStart = 0
        while (start < raw.length && raw[start].isWhitespace()) {
            if (raw[start] == '\n') lineStart = start + 1
            start++
        }
        if (start == raw.length) return null
        return raw.subSequence(lineStart, raw.length).toString().trimEnd()
    }

    /** What a shared file is called in the vault, before [Attachments.cleanName] and [Attachments.availableName]. */
    data class FileName(val stem: String, val ext: String)

    /**
     * The name a shared file keeps, the way Obsidian keeps a file's own name
     * when one is attached.
     *
     * [displayName] is the provider's `DISPLAY_NAME`. A suffix that looks like an
     * extension is used as it is, case and all. A name without one takes the
     * extension [mime] maps to, so `Screenshot 2026.10.11` sent as PNG becomes
     * `Screenshot 2026.10.11.png` rather than a file whose extension is `11`.
     *
     * A file with no name of its own gets the one Obsidian would give it:
     * `Recording` and a timestamp for audio, as its recorder writes, and
     * `Pasted image` and a timestamp for an image, as it names one pasted in.
     * Obsidian has no name for anything else, so that is `Attachment`.
     *
     * @param extensionFor the extension a MIME type maps to, or null. On the
     *   device that is `MimeTypeMap`; see [VaultFiles.extensionFor].
     */
    fun fileName(
        displayName: String?,
        mime: String?,
        now: LocalDateTime,
        extensionFor: (String) -> String?,
    ): FileName {
        val name = displayName.orEmpty().substringAfterLast('/').trim().trimEnd('.')
        val dot = name.lastIndexOf('.')
        val suffix = if (dot >= 0) name.substring(dot + 1) else ""
        val (stem, ext) = if (looksLikeExtension(suffix)) {
            name.substring(0, dot).trim() to suffix
        } else {
            name to mime?.let(extensionFor).orEmpty()
        }
        return FileName(stem.ifEmpty { unnamed(mime, now) }, ext)
    }

    /** Letters and digits, short, and at least one letter: `jpg`, `m4a`, `3gp`, but not the `11` of a date. */
    private fun looksLikeExtension(suffix: String): Boolean =
        suffix.length in 1..MAX_EXTENSION &&
            suffix.all { it.isLetterOrDigit() } &&
            suffix.any { it.isLetter() }

    private fun unnamed(mime: String?, now: LocalDateTime): String {
        val prefix = when {
            mime?.startsWith("audio/") == true -> "Recording"
            mime?.startsWith("image/") == true -> "Pasted image"
            else -> "Attachment"
        }
        return "$prefix ${STAMP.format(now)}"
    }

    /** The note's own line break, judged by the last one in it. `\n` for a note that has none yet. */
    private fun lineEnding(text: CharSequence): String {
        val last = text.lastIndexOf('\n')
        return if (last > 0 && text[last - 1] == '\r') "\r\n" else "\n"
    }

    private const val MAX_EXTENSION = 8

    /** Obsidian's `YYYYMMDDHHmmss`, as in `Recording 20260105093000.m4a`. */
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
}
