package dev.vaultdrop.core

/**
 * Finds the `[[` the caret is sitting inside.
 *
 * Indices only, never substrings of the note: this runs on the main thread on
 * every keystroke, so the cost of typing must not grow with the length of the
 * note. Only the query itself — a few characters — is ever copied.
 */
object LinkScan {

    /** @return the index of the unclosed `[[` before [caret], or -1 if there is none. */
    fun openBracketBefore(text: String, caret: Int): Int {
        val at = caret.coerceIn(0, text.length)
        // Both brackets have to be behind the caret to count.
        if (at < 2) return -1
        val cut = text.lastIndexOf("[[", startIndex = at - 2)
        if (cut == -1) return -1
        val close = text.indexOf("]]", cut)
        return if (close != -1 && close <= at - 2) -1 else cut
    }

    /** @return what has been typed since that `[[`, or null when the caret is not in one. */
    fun queryAt(text: String, caret: Int): String? {
        val cut = openBracketBefore(text, caret)
        if (cut == -1) return null
        return text.substring(cut + 2, caret.coerceIn(0, text.length))
    }
}
