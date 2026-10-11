package dev.vaultdrop.core

/** One replacement in the note: [text] goes over `start until end`, and the caret lands at [caret]. */
data class Edit(val start: Int, val end: Int, val text: String, val caret: Int)

/**
 * Where [insert] goes in [text] at [caret], and where the caret lands after it.
 *
 * A [block], like a photo's embed, gets a line of its own: a newline before it
 * unless the caret is at the start of a line, and one after it unless a line
 * break is already next. The caret lands at the start of the line below, so
 * typing carries on under the photo rather than on the end of its link.
 *
 * Prose, like a dictation, joins the text it lands in: a space before it unless
 * there is whitespace there already. The caret lands at its end.
 *
 * Only ever inserts. Nothing the user typed is replaced, selected or not.
 */
fun splice(text: CharSequence, caret: Int, insert: String, block: Boolean): Edit {
    val at = caret.coerceIn(0, text.length)
    if (insert.isEmpty()) return Edit(at, at, "", at)

    if (!block) {
        val joined = at == 0 || text[at - 1].isWhitespace() || insert[0].isWhitespace()
        val replacement = if (joined) insert else " $insert"
        return Edit(at, at, replacement, at + replacement.length)
    }

    val before = if (at == 0 || text[at - 1] == '\n') "" else "\n"
    // A CRLF note's line break is two characters, and the caret steps over both.
    val existingBreak = when {
        at < text.length && text[at] == '\n' -> 1
        at + 1 < text.length && text[at] == '\r' && text[at + 1] == '\n' -> 2
        else -> 0
    }
    val replacement = before + insert + if (existingBreak == 0) "\n" else ""
    return Edit(at, at, replacement, at + replacement.length + existingBreak)
}
