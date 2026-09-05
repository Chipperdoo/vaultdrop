package dev.vaultdrop.core

import java.time.LocalDate
import java.time.LocalDateTime

/** Where today's note lives, split the way SAF needs it. */
data class NotePath(
    /** Directory segments below the vault root; empty means the root itself. */
    val dirs: List<String>,
    /** Leaf filename, extension included. */
    val fileName: String,
) {
    val relativePath: String get() = (dirs + fileName).joinToString("/")
    val parentPath: String get() = dirs.joinToString("/")
}

object DailyNote {

    /** The brief's list, verbatim. `/` is excluded — it is a separator, not a name. */
    private val UNSAFE = charArrayOf(':', '*', '?', '"', '<', '>', '|')

    fun sanitize(segment: String): String {
        val out = StringBuilder(segment.length)
        appendSanitized(out, segment, 0, segment.length)
        return out.toString()
    }

    /**
     * [sanitize] over `source[from until to]`, straight into [out].
     *
     * @return false when the segment was blank, and so contributes no directory
     *   and no filename at all.
     *
     * Trimming happens before the substitution rather than after it, which
     * [sanitize] cannot tell apart: every character in [UNSAFE] is replaced by a
     * `-`, and swapping one non-whitespace character for another can neither
     * create nor destroy the whitespace at the ends.
     */
    internal fun appendSanitized(out: StringBuilder, source: String, from: Int, to: Int): Boolean {
        var start = from
        var end = to
        while (start < end && source[start].isWhitespace()) start++
        while (end > start && source[end - 1].isWhitespace()) end--
        if (start == end) return false
        for (i in start until end) {
            val c = source[i]
            out.append(if (c in UNSAFE) '-' else c)
        }
        return true
    }

    /**
     * Resolves the note path for [date] under [config].
     *
     * A `/` anywhere in the format is a directory separator, so the formatted
     * date can contribute folders of its own (`YYYY/MM/DD` -> `2026/09/02.md`).
     *
     * One date at a time. Anything resolving a *span* of them wants
     * [DailyLayout], which does the config's share of this once instead of once
     * per day.
     */
    fun resolve(config: VaultConfig, date: LocalDate): NotePath =
        DailyLayout(config).resolve(date)

    /** The date label the top bar shows — the same string the filename is built from. */
    fun label(config: VaultConfig, date: LocalDate): String = render(config, date)

    /**
     * Formatting at the start of the day, not at [LocalDate], because a format
     * carrying a time token — `YYYY-MM-DD HH:mm` — throws
     * `UnsupportedTemporalTypeException` against a bare date, and that is thrown
     * from the launch path, before anything is on screen. Midnight also keeps the
     * filename a pure function of the day: resolving against the wall clock would
     * hand the same day a different file every minute.
     */
    private fun render(config: VaultConfig, date: LocalDate): String =
        MomentFormat.formatter(config.format).format(date.atStartOfDay())

    /** Fallback filename stem when the format resolves to nothing at all. */
    internal fun fallbackStem(date: LocalDate): String = date.toString()

    /**
     * The name a trashed note takes, named the way Obsidian names it.
     *
     * Obsidian's local trash is flat: a note from `journals/` lands at the root
     * of `.trash/` under its own filename, not under a mirrored path. A collision
     * is settled with a numeric suffix and nothing else — the first copy keeps
     * the name, the next is ` 2`, then ` 3`. Both halves are visible in any vault
     * that has used it for a while, and matching them is what makes a note
     * trashed here indistinguishable from one trashed in Obsidian on the desktop.
     *
     * A deletion stamp of our own would have been more informative and would have
     * been ours alone; the vault is the portable thing, not this app.
     *
     * @param attempt 0 for the name as-is, 1 for ` 2`, and so on.
     */
    fun trashName(fileName: String, attempt: Int): String {
        if (attempt <= 0) return fileName
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.take(dot) else fileName
        val extension = if (dot > 0) fileName.substring(dot) else ""
        return "$stem ${attempt + 1}$extension"
    }

    /** Obsidian's own local-trash folder, so a recovery is where the user would look. */
    const val TRASH_DIR = ".trash"

    /** Fallback body when there is no template, per the brief. */
    fun minimalFrontmatter(now: LocalDateTime): String =
        "---\ndate: ${now.toLocalDate()}\n---\n\n"
}

/**
 * [DailyNote.resolve] with the config's own share of the work hoisted out.
 *
 * Resolving one date is a formatter call and a walk over the result. Splitting
 * and sanitising `config.folder` is neither, and neither is allocating the four
 * lists the list-based version went through — [DayIndex.build] was paying for
 * all of it 3650 times in a row, on the pass that builds the index it reads.
 *
 * [resolve] is derived *from* [relativePath] rather than alongside it, so there
 * is one place a path is built and the day index cannot test for a path a note
 * would not be written to.
 *
 * Not thread-safe: [relativePath] writes through a shared builder. Every caller
 * is a single loop on a single thread, which is the case worth optimising.
 */
class DailyLayout(config: VaultConfig) {

    private val formatter = MomentFormat.formatter(config.format)

    /** The configured folder, split and sanitised once rather than once per day. */
    private val folderDirs: List<String> =
        config.folder.split('/').filter { it.isNotBlank() }.map(DailyNote::sanitize)

    private val scratch = StringBuilder(64)

    /** Where [date]'s note lives, as one vault-relative path. */
    fun relativePath(date: LocalDate): String {
        val out = scratch
        out.setLength(0)
        for (dir in folderDirs) out.append(dir).append('/')

        val formatted = formatter.format(date.atStartOfDay())
        // A `/` in the format is a directory separator, so the formatted date can
        // contribute folders of its own. A segment that is blank contributes
        // nothing — not an empty directory, and not an empty name.
        var start = 0
        var any = false
        while (true) {
            var end = formatted.indexOf('/', start)
            if (end == -1) end = formatted.length
            if (DailyNote.appendSanitized(out, formatted, start, end)) {
                out.append('/')
                any = true
            }
            if (end == formatted.length) break
            start = end + 1
        }

        if (any) {
            out.setLength(out.length - 1) // the separator after the final segment
        } else {
            out.append(DailyNote.fallbackStem(date))
        }
        return out.append(".md").toString()
    }

    /** [relativePath] split the way SAF needs it. */
    fun resolve(date: LocalDate): NotePath {
        val path = relativePath(date)
        val cut = path.lastIndexOf('/')
        return if (cut == -1) {
            NotePath(emptyList(), path)
        } else {
            NotePath(path.substring(0, cut).split('/'), path.substring(cut + 1))
        }
    }
}
