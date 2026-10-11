package dev.vaultdrop.core

/**
 * Where an attached file goes, what it is called, and the link that embeds it.
 *
 * The folder and the link come from the vault's own `app.json` through
 * [AppConfig], following Obsidian core as the obsidian-dev-utils library
 * reimplements it in `attachment-path.ts` and `link.ts`. A photo attached here
 * should look like one attached in Obsidian.
 */
object Attachments {

    /**
     * The folder `attachmentFolderPath` names for an attachment of [note].
     *
     * Obsidian stores four shapes. `/` or empty is the vault root. `.` or `./` is
     * the note's own folder. `./img` is `img` under the note's folder. Anything
     * else is a folder from the vault root, used as given. Which string each
     * option in Obsidian's settings stores is still to be read back on desktop;
     * see "Checks against desktop Obsidian" in docs/plans/multimedia.md.
     *
     * "The note's folder" is where the note really is, so a `YYYY/MM/DD` format
     * puts `./` attachments in `2026/09/`.
     *
     * Backslashes count as separators, as they do in Obsidian's `normalizePath`.
     * A `..` steps up a folder and stops at the vault root: SAF document ids are
     * plain strings, and one that climbs out of the tree is not ours to write.
     */
    fun folderFor(setting: String, note: NotePath): List<String> = when {
        setting == "." || setting == "./" -> note.dirs
        setting.startsWith("./") -> segments(setting.substring(2), note.dirs)
        else -> segments(setting, emptyList())
    }

    private fun segments(path: String, base: List<String>): List<String> {
        val out = base.toMutableList()
        for (segment in path.split('/', '\\')) {
            when (segment) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.add(segment)
            }
        }
        return out
    }

    /**
     * The first free name for `stem.ext`, the way Obsidian's `getAvailablePath`
     * picks one: the name as it is, then `stem 1.ext`, `stem 2.ext` and so on.
     *
     * `.trash/` dedups differently, starting at ` 2`; that is
     * [DailyNote.trashName], and the two are not interchangeable.
     *
     * Unconfirmed: the ` 1` start comes from secondary sources. See "Checks
     * against desktop Obsidian" in docs/plans/multimedia.md.
     *
     * @param exists asked about each candidate file name in turn.
     * @return null when every attempt up to the cap is taken.
     */
    fun availableName(stem: String, ext: String, exists: (String) -> Boolean): String? {
        val dotExt = if (ext.isEmpty()) "" else ".$ext"
        for (attempt in 0 until NAME_ATTEMPTS) {
            val candidate = if (attempt == 0) "$stem$dotExt" else "$stem $attempt$dotExt"
            if (!exists(candidate)) return candidate
        }
        return null
    }

    /**
     * [raw] made safe to use as one segment of an attachment's name.
     *
     * A slash would make the name a path. The characters Android's provider
     * refuses would be swapped for `_` behind our back, leaving the embed pointing
     * at a name that is not on disk. `# ^ [ ] |` are legal on disk, but Obsidian
     * cannot link to a file whose name carries them. A leading dot hides the file
     * from Obsidian and from the walk. All of these become `-`, the substitute
     * [DailyNote.sanitize] already uses.
     *
     * @return null when nothing is left to name the file by.
     */
    fun cleanName(raw: String): String? {
        val out = StringBuilder(raw.length)
        for (c in raw.trim()) out.append(if (c in UNSAFE || c.code < 0x20 || c.code == 0x7F) '-' else c)
        return out.trimStart('.').trim().toString().ifEmpty { null }
    }

    /**
     * The embed Obsidian would write for the attachment at [target], a
     * vault-relative path, from inside [note].
     *
     * `shortest` writes the bare file name. Obsidian only does that when no other
     * file in the vault shares the name, and the index here holds notes, not
     * attachments, so a picked photo whose name exists elsewhere gets a link
     * Obsidian would have written longer. Timestamped names never collide.
     */
    fun embed(target: String, note: NotePath, app: AppConfig): String {
        val linkText = when (app.linkFormat) {
            LinkFormat.Shortest -> target.substringAfterLast('/')
            LinkFormat.Absolute -> target
            LinkFormat.Relative -> relative(note.dirs, target)
        }
        return if (app.markdownLinks) "![](${encode(linkText)})" else "![[$linkText]]"
    }

    /** [target] as seen from the folder [from]: `../` for each step up, then down. */
    private fun relative(from: List<String>, target: String): String {
        val parts = target.split('/')
        val dirs = parts.dropLast(1)
        var shared = 0
        while (shared < from.size && shared < dirs.size && from[shared] == dirs[shared]) shared++
        return buildString {
            repeat(from.size - shared) { append("../") }
            for (dir in dirs.drop(shared)) append(dir).append('/')
            append(parts.last())
        }
    }

    /**
     * Percent-encodes what Obsidian encodes in a Markdown link and nothing else:
     * the space, the backslash and the control characters. `#`, `(`, `%` and
     * non-ASCII letters go through as they are.
     *
     * Unconfirmed: the set is obsidian-dev-utils' `encodeUrl`, not something
     * read off desktop Obsidian. See "Checks against desktop Obsidian" in
     * docs/plans/multimedia.md.
     */
    private fun encode(path: String): String = buildString(path.length + 8) {
        for (c in path) {
            if (encodes(c)) append('%').append(HEX[c.code shr 4]).append(HEX[c.code and 0xF]) else append(c)
        }
    }

    /** `encodeUrl`'s set: space, backslash, and the controls 00, 08, 0B, 0C and 0E to 1F. Not tab, LF or CR. */
    private fun encodes(c: Char): Boolean = when (c.code) {
        0x20, 0x5C, 0x00, 0x08, 0x0B, 0x0C, in 0x0E..0x1F -> true
        else -> false
    }

    /** A hundred files picked under the same name before giving up, as `.trash/` allows. */
    private const val NAME_ATTEMPTS = 100

    private val UNSAFE = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '#', '^', '[', ']')

    private const val HEX = "0123456789ABCDEF"
}
