package dev.vaultdrop.core

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime

/** A note as the editor holds it: frontmatter parked in the chip, body in the field. */
data class OpenNote(
    val uri: Uri?,
    val path: NotePath,
    val frontmatter: String,
    val body: String,
    /** True when this launch created the file — drives the "new" tag in the top bar. */
    val created: Boolean,
    /** Set when the note could not be opened *or* created. */
    val error: String? = null,
)

/** Everything the vault can be asked for. Phase 1 uses only [openNote]. */
class VaultRepository(private val files: VaultFiles) {

    val treeUri: Uri get() = files.treeUri

    // ── Validation and config ────────────────────────────────────────────────

    /** The brief's vault test: `.obsidian/` is a child of the selected root. */
    fun isVault(): Boolean = files.metadata(VaultFiles.OBSIDIAN_DIR) != null

    /**
     * Reads and parses `.obsidian/daily-notes.json`.
     *
     * A vault with Daily Notes never opened has no config file at all; that is
     * not an error, it is the defaults column of the brief's table.
     */
    fun readConfig(now: Long = System.currentTimeMillis()): VaultConfig {
        val bytes = files.readBytes(VaultFiles.CONFIG_PATH)
            ?: return VaultConfig(present = false, lastReadAt = now)
        val meta = files.metadata(VaultFiles.CONFIG_PATH)
        return VaultConfig.parse(
            raw = bytes.toString(Charsets.UTF_8),
            sha256 = Hashing.sha256(bytes),
            lastModified = meta?.lastModified ?: 0L,
            now = now,
        )
    }

    /** Phase 2 step 1. Cheap mtime probe first; only hash if the stamp moved. */
    fun configChangedSince(cached: VaultConfig): VaultConfig? {
        val meta = files.metadata(VaultFiles.CONFIG_PATH)
        if (meta == null) {
            // The file was deleted since we cached it.
            return if (cached.present) VaultConfig(present = false, lastReadAt = System.currentTimeMillis()) else null
        }
        if (cached.present && meta.lastModified == cached.lastModified) return null
        val fresh = readConfig()
        return if (fresh.sha256 == cached.sha256 && cached.present) null else fresh
    }

    /**
     * Reads and parses the Linter plugin's own settings file.
     *
     * A vault without the plugin has no file at all; that is not an error, it is
     * the rule being off — exactly as `daily-notes.json` missing is the defaults.
     */
    fun readLinter(now: Long = System.currentTimeMillis()): LinterConfig {
        val bytes = files.readBytes(LinterConfig.PATH)
            ?: return LinterConfig(present = false, lastReadAt = now)
        val meta = files.metadata(LinterConfig.PATH)
        return LinterConfig.parse(
            raw = bytes.toString(Charsets.UTF_8),
            sha256 = Hashing.sha256(bytes),
            lastModified = meta?.lastModified ?: 0L,
            now = now,
        )
    }

    /**
     * Reads `.obsidian/app.json`, the vault's own application settings.
     *
     * A vault that has never had a setting changed from its default has no file
     * at all; that is not an error, it is Obsidian's defaults.
     */
    fun readAppConfig(now: Long = System.currentTimeMillis()): AppConfig {
        val bytes = files.readBytes(AppConfig.PATH)
            ?: return AppConfig(present = false, lastReadAt = now)
        val meta = files.metadata(AppConfig.PATH)
        return AppConfig.parse(
            raw = bytes.toString(Charsets.UTF_8),
            sha256 = Hashing.sha256(bytes),
            lastModified = meta?.lastModified ?: 0L,
            now = now,
        )
    }

    /** The same mtime-then-hash probe [configChangedSince] uses. */
    fun appChangedSince(cached: AppConfig): AppConfig? {
        val meta = files.metadata(AppConfig.PATH)
        if (meta == null) {
            return if (cached.present) AppConfig(present = false, lastReadAt = System.currentTimeMillis()) else null
        }
        if (cached.present && meta.lastModified == cached.lastModified) return null
        val fresh = readAppConfig()
        return if (fresh.sha256 == cached.sha256 && cached.present) null else fresh
    }

    /** The same mtime-then-hash probe [configChangedSince] uses. */
    fun linterChangedSince(cached: LinterConfig): LinterConfig? {
        val meta = files.metadata(LinterConfig.PATH)
        if (meta == null) {
            return if (cached.present) LinterConfig(present = false, lastReadAt = System.currentTimeMillis()) else null
        }
        if (cached.present && meta.lastModified == cached.lastModified) return null
        val fresh = readLinter()
        return if (fresh.sha256 == cached.sha256 && cached.present) null else fresh
    }

    // ── Phase 1: open today's note ───────────────────────────────────────────

    /**
     * Read-first, create-only-on-miss. The existing file is never rewritten,
     * so anything already typed in Obsidian survives untouched.
     */
    fun openNote(
        config: VaultConfig,
        date: LocalDate,
        /** The wall clock. `{{time}}` comes from here; `{{date}}` comes from [date]. */
        now: LocalDateTime = LocalDateTime.now(),
        templateCache: String? = null,
        linter: LinterConfig = LinterConfig(),
    ): OpenNote {
        readNote(config, date)?.let { return it }
        val path = DailyNote.resolve(config, date)

        // Miss. Render the template (or the minimal frontmatter fallback) once,
        // at creation time, and never again.
        //
        // The stamp is the *note's* date carrying the current wall clock: a note
        // backfilled for last Tuesday must say last Tuesday in its frontmatter,
        // or `date:` disagrees with the filename it was just written under.
        val seed = renderSeed(config, date.atTime(now.toLocalTime()), templateCache)
        val (seedFm, seedBody) = Frontmatter.split(seed)

        // The plugin would have stamped this the moment Obsidian created it, so
        // the note lands with both timestamps rather than waiting for a keystroke.
        val fm = YamlTimestamp.stamp(seedFm, linter, path.relativePath, now, isNew = true, body = seedBody)

        val created = files.create(path)
            ?: return OpenNote(null, path, "", "", created = false, error = "Could not create ${path.relativePath}")
        if (!files.write(created, Frontmatter.join(fm, seedBody))) {
            return OpenNote(created, path, "", "", created = false, error = "Could not write ${path.relativePath}")
        }
        return OpenNote(created, path, fm, seedBody, created = true)
    }

    /**
     * The read half of [openNote] on its own: null when the day has no file.
     *
     * Stepping between days must never create, so the chevrons and the swipe come
     * through here instead. A null is not an error — it is the day sheet's cue
     * that tapping the row would be the thing that creates the note.
     */
    fun readNote(config: VaultConfig, date: LocalDate): OpenNote? {
        val path = DailyNote.resolve(config, date)
        val uri = files.uriFor(path.relativePath)
        val existing = files.readBytes(uri) ?: return null
        val (fm, body) = Frontmatter.split(existing.toString(Charsets.UTF_8))
        return OpenNote(uri, path, fm, body, created = false)
    }

    private fun renderSeed(config: VaultConfig, stamp: LocalDateTime, templateCache: String?): String {
        val raw = templateCache ?: readTemplate(config)
        if (raw.isNullOrEmpty()) return DailyNote.minimalFrontmatter(stamp)
        return TemplateEngine.render(raw, config, stamp)
    }

    /** Obsidian stores the template path with or without the extension. */
    fun readTemplate(config: VaultConfig): String? {
        val path = config.template.trim().trim('/')
        if (path.isEmpty()) return null
        return files.readText(path)
            ?: files.readText(if (path.endsWith(".md")) path else "$path.md")
    }

    /**
     * Autosave target. Only ever called for the note the user has open.
     *
     * @return the frontmatter as written — `date modified` moved on, if the
     *   plugin's rule asks for it — or null when the write failed. The caller
     *   holds it so the next save edits the same fence rather than a stale one.
     */
    fun save(
        note: OpenNote,
        body: String,
        linter: LinterConfig = LinterConfig(),
        now: LocalDateTime = LocalDateTime.now(),
    ): String? {
        val uri = note.uri ?: return null
        val fm = YamlTimestamp.stamp(note.frontmatter, linter, note.path.relativePath, now, isNew = false, body = body)
        return if (files.write(uri, Frontmatter.join(fm, body))) fm else null
    }

    /** Where a deleted note ended up. */
    sealed interface Trashed {
        data class Moved(val path: String) : Trashed
        data object Deleted : Trashed
    }

    /**
     * Removes [note] the way the vault's own `trashOption` asks for.
     *
     * [TrashOption.None] deletes outright. [TrashOption.Local] moves the note
     * into `.trash/`, flat and under its own name, which is where and how
     * Obsidian puts it — see [DailyNote.trashName]. [TrashOption.System] asks for
     * the OS trash, which Android does not expose to a folder-grant app at all:
     * `DocumentsContract` has no trash call, and `MediaStore`'s covers indexed
     * media rather than tree documents. It is treated as [TrashOption.Local],
     * which is the only recoverable thing left, and the caller says so on screen
     * rather than letting the difference pass in silence.
     *
     * The move is copy-then-delete rather than `DocumentsContract.moveDocument`:
     * moving between directories needs `FLAG_SUPPORTS_MOVE`, which the provider
     * behind a synced folder does not reliably offer, and a daily note is small
     * enough that the copy costs nothing. The source only goes once the copy is
     * on disk, so a failure part way leaves the note where it was — and if the
     * delete is what fails, the copy is taken back out rather than leaving the
     * same note in two places for the sync client to argue about.
     *
     * Reads the file rather than taking a body from the caller, so what lands in
     * `.trash` is what was actually on disk.
     *
     * @return where it went, or null if it is still where it was.
     */
    fun trash(note: OpenNote, option: TrashOption): Trashed? {
        val uri = note.uri ?: return null
        if (option == TrashOption.None) {
            return if (files.delete(uri)) Trashed.Deleted else null
        }
        val text = files.readBytes(uri)?.toString(Charsets.UTF_8) ?: return null
        val target = freeTrashPath(note.path.fileName) ?: return null
        val copy = files.create(target) ?: return null
        if (!files.write(copy, text)) {
            files.delete(copy)
            return null
        }
        if (!files.delete(uri)) {
            files.delete(copy)
            return null
        }
        return Trashed.Moved(target.relativePath)
    }

    /** The first free name in `.trash/`, following Obsidian's numeric dedup. */
    private fun freeTrashPath(fileName: String): NotePath? {
        for (attempt in 0 until TRASH_ATTEMPTS) {
            val candidate = NotePath(listOf(DailyNote.TRASH_DIR), DailyNote.trashName(fileName, attempt))
            if (!files.exists(candidate.relativePath)) return candidate
        }
        return null
    }

    fun exists(config: VaultConfig, date: LocalDate): Boolean =
        files.exists(DailyNote.resolve(config, date).relativePath)

    // ── Phase 2: the walk ────────────────────────────────────────────────────

    /**
     * The whole walk: the directory listings, and not a single file opened.
     *
     * Dot-directories are skipped — `.obsidian`, `.trash` and friends are not
     * notes. Every note's name and path falls out of the listings for free, which
     * is everything the `[[` picker, [DayIndex] and the search screen's name
     * filter need. Nothing here reads a note, so there is nothing to be
     * incremental about: a walk is one cursor per directory, whatever changed
     * since the last one. Full text is [VaultSearch]'s job, on demand.
     */
    fun buildNameIndex(): WikiIndex {
        val entries = ArrayList<IndexEntry>(256)
        val queue = ArrayDeque<String>()
        queue.add("")

        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            for (child in files.listChildren(dir)) {
                if (child.name.startsWith(".")) continue
                val relative = if (dir.isEmpty()) child.name else "$dir/${child.name}"
                if (child.isDirectory) {
                    queue.add(relative)
                } else if (child.name.endsWith(".md", ignoreCase = true)) {
                    entries.add(
                        IndexEntry(
                            name = child.name.removeSuffix(".md").removeSuffix(".MD"),
                            path = relative,
                        ),
                    )
                }
            }
        }
        // CASE_INSENSITIVE_ORDER compares in place; the lowercase() selector was
        // allocating two strings for every comparison in the sort.
        entries.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return WikiIndex(entries)
    }

    private companion object {
        /** Deep enough that a day trashed over and over still finds a free name. */
        const val TRASH_ATTEMPTS = 100
    }
}
