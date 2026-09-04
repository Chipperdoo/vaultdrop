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
        for (c in segment) out.append(if (c in UNSAFE) '-' else c)
        return out.toString().trim()
    }

    /**
     * Resolves the note path for [date] under [config].
     *
     * A `/` anywhere in the format is a directory separator, so the formatted
     * date can contribute folders of its own (`YYYY/MM/DD` -> `2026/09/02.md`).
     */
    fun resolve(config: VaultConfig, date: LocalDate): NotePath {
        val formatted = render(config, date)
        val parts = formatted.split('/').filter { it.isNotBlank() }.map(::sanitize)
        val name = parts.lastOrNull().orEmpty().ifBlank { date.toString() }

        val folderDirs = config.folder
            .split('/')
            .filter { it.isNotBlank() }
            .map(::sanitize)

        return NotePath(
            dirs = folderDirs + parts.dropLast(1),
            fileName = "$name.md",
        )
    }

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
