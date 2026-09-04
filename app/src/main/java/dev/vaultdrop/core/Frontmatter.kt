package dev.vaultdrop.core

/**
 * Splits leading YAML frontmatter off a note so the editor can park it in a chip
 * instead of making the user scroll past it.
 *
 * [split] and [join] are exact inverses — the file is never rewritten just for
 * having been opened. That invariant is also why nothing here normalises line
 * endings: a note Obsidian wrote on Windows is CRLF from top to bottom, and
 * rewriting it to LF to move one timestamp would touch every line of a diary
 * entry. The fence is recognised in whatever shape it arrives in, and written
 * back in that same shape.
 */
object Frontmatter {

    private const val FENCE = "---"

    /**
     * The UTF-8 byte-order mark, as it decodes into a `String`.
     *
     * Some Windows editors write one at the very start of a file. It is
     * invisible everywhere it matters — but it sits in front of the opening
     * fence, so a check that does not expect it concludes the note has no
     * frontmatter at all.
     */
    private const val BOM = '\uFEFF'

    /**
     * @return the fence block (including its closing `---` line and the blank
     *   line(s) that follow it) and the body.
     *
     * The gap belongs to the frontmatter rather than being trimmed and re-added,
     * so [join] is a plain concatenation. Trimming it and always writing one
     * newline back rewrote every note whose gap was not exactly one blank line —
     * `---\n\n\n# Log` lost a line, `---\n# Log` gained one — on the first autosave.
     *
     * A note whose fence cannot be found comes back as all body, which is the
     * honest answer. [opensWithFence] is what stops that from turning into a
     * second fence stacked above the first.
     */
    fun split(text: String): Pair<String, String> {
        val openEnd = fenceLineEnd(text, contentStart(text))
        if (openEnd == -1) return "" to text
        val closeStart = fenceLineStart(text, openEnd)
        if (closeStart == -1) return "" to text

        var bodyStart = fenceLineEnd(text, closeStart)
        // Blank lines below the fence belong to the gap, in whichever ending the
        // file uses: `\r\n\r\n` is two of them, not four.
        while (true) {
            val next = terminatorEnd(text, bodyStart)
            if (next == -1) break
            bodyStart = next
        }
        return text.substring(0, bodyStart) to text.substring(bodyStart)
    }

    fun join(frontmatter: String, body: String): String = frontmatter + body

    /**
     * True when [text] already opens with a fence line.
     *
     * The guard on [upsert]'s mint-a-fence branch: [split] should have found any
     * fence the note has and handed it over as frontmatter, so a *body* that
     * still opens with one means a shape not understood here. Leaving that note
     * unstamped is recoverable — the next save tries again. Stacking a second
     * fence above the first is not: everything below it stops being frontmatter
     * and becomes body text.
     */
    fun opensWithFence(text: String): Boolean =
        fenceLineEnd(text, contentStart(text)) != -1

    /**
     * @return the line ending [text] uses, or `\n` for text with no line break
     *   to learn from.
     */
    fun newlineOf(text: String): String {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("\r\n", i)) return "\r\n"
            if (text[i] == '\r') return "\r"
            if (text[i] == '\n') return "\n"
            i++
        }
        return "\n"
    }

    /** True when [key] is already a top-level key in [frontmatter]. */
    fun has(frontmatter: String, key: String): Boolean =
        lineFor(frontmatter, key) != null

    /**
     * Sets `key: value` in [frontmatter], returning the new fence.
     *
     * Replaces the key's line if it is already there, appends it just above the
     * closing `---` if it is not, and writes a whole fence when [frontmatter] is
     * empty. Every other byte — key order, comments, spacing, line endings, the
     * gap below the fence — is carried through untouched, because this runs on a
     * note the user is in the middle of typing into.
     *
     * Top-level scalars only, the same limit the plugin's own rule carries: a key
     * nested under another is not found, and so is not disturbed.
     *
     * @param newline the ending to mint a *new* fence with. Ignored when there is
     *   already a fence, which knows its own.
     */
    fun upsert(frontmatter: String, key: String, value: String, newline: String = "\n"): String {
        val entry = "$key: $value"
        if (frontmatter.isEmpty()) {
            return "$FENCE$newline$entry$newline$FENCE$newline$newline"
        }

        val openEnd = fenceLineEnd(frontmatter, contentStart(frontmatter))
        val closeStart = if (openEnd == -1) -1 else fenceLineStart(frontmatter, openEnd)
        // Not a fence we recognise; refuse rather than guess at where to write.
        if (closeStart == -1) return frontmatter

        val head = frontmatter.substring(0, openEnd)
        val inner = frontmatter.substring(openEnd, closeStart)
        val tail = frontmatter.substring(closeStart)

        // A line added to an existing block wears that block's ending, so a stamp
        // never leaves a lone LF in the middle of a CRLF file.
        val nl = newlineOf(head)
        val existing = lineFor(inner, key)
        val body = if (existing != null) {
            inner.replaceRange(existing.first, existing.last, entry)
        } else {
            inner + entry + nl
        }
        return head + body + tail
    }

    // ── Recognising a fence ──────────────────────────────────────────────────

    /** Index of the first real character, stepping over a byte-order mark. */
    private fun contentStart(text: String): Int =
        if (text.isNotEmpty() && text[0] == BOM) 1 else 0

    /**
     * @return the index just past the line ending at [at], or -1 when there is
     *   not one there.
     *
     * CRLF is matched ahead of the lone CR that classic Mac files use, or a
     * Windows note would read as two endings and every blank line below the
     * fence would count double.
     */
    private fun terminatorEnd(text: String, at: Int): Int = when {
        at < 0 || at >= text.length -> -1
        text.startsWith("\r\n", at) -> at + 2
        text[at] == '\n' || text[at] == '\r' -> at + 1
        else -> -1
    }

    /**
     * @return the index just past the fence line beginning at [at], or -1 when
     *   the line there is not a `---` fence.
     *
     * Trailing spaces and tabs are tolerated — invisible in any editor that could
     * have left one, and still a fence to Obsidian. Nothing else is: `----` is a
     * horizontal rule and `--- x` is not a fence at all.
     */
    private fun fenceLineEnd(text: String, at: Int): Int {
        if (at < 0 || !text.startsWith(FENCE, at)) return -1
        var i = at + FENCE.length
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        // A closing fence is allowed to end the file with no line ending at all.
        if (i == text.length) return i
        return terminatorEnd(text, i)
    }

    /**
     * @return where the next fence line at or after [from] begins, or -1 when
     *   there is none before the end of [text].
     */
    private fun fenceLineStart(text: String, from: Int): Int {
        var i = from
        while (i != -1 && i <= text.length) {
            if (fenceLineEnd(text, i) != -1) return i
            i = nextLineStart(text, i)
        }
        return -1
    }

    /** @return the index just past the next line ending after [from], or -1. */
    private fun nextLineStart(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val end = terminatorEnd(text, i)
            if (end != -1) return end
            i++
        }
        return -1
    }

    /**
     * @return the range of the line declaring [key], end-exclusive of its line
     *   ending, or null when the key is absent.
     */
    private fun lineFor(text: String, key: String): IntRange? {
        val needle = "$key:"
        var from = 0
        while (from <= text.length) {
            val at = text.indexOf(needle, from)
            if (at == -1) return null
            // Top level only: the key has to open its own line, unindented. The
            // first line counts whether or not a byte-order mark precedes it, and
            // either half of any line ending opens the next one.
            val opensLine = at == contentStart(text) ||
                (at > 0 && (text[at - 1] == '\n' || text[at - 1] == '\r'))
            // Stopping short of the `\r` as well as the `\n` is what keeps a
            // replacement inside a CRLF block from eating the carriage return.
            if (opensLine) return at..lineContentEnd(text, at)
            from = at + needle.length
        }
        return null
    }

    /** @return the index of the line ending at or after [from], else the length. */
    private fun lineContentEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        return i
    }
}
