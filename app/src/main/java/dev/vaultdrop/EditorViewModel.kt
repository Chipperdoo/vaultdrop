package dev.vaultdrop

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vaultdrop.core.AppConfig
import dev.vaultdrop.core.AskReason
import dev.vaultdrop.core.Attachments
import dev.vaultdrop.core.ConfigStore
import dev.vaultdrop.core.DailyNote
import dev.vaultdrop.core.DayIndex
import dev.vaultdrop.core.Hashing
import dev.vaultdrop.core.IndexEntry
import dev.vaultdrop.core.LinterConfig
import dev.vaultdrop.core.LinkScan
import dev.vaultdrop.core.NotePath
import dev.vaultdrop.core.OpenNote
import dev.vaultdrop.core.SearchHit
import dev.vaultdrop.core.TrashOption
import dev.vaultdrop.core.VaultConfig
import dev.vaultdrop.core.VaultFiles
import dev.vaultdrop.core.VaultRepository
import dev.vaultdrop.core.VaultSearch
import dev.vaultdrop.core.VoiceChoice
import dev.vaultdrop.core.VoiceEngine
import dev.vaultdrop.core.VoiceEvent
import dev.vaultdrop.core.VoiceFailure
import dev.vaultdrop.core.VoiceOptions
import dev.vaultdrop.core.WikiIndex
import dev.vaultdrop.core.chooseVoice
import dev.vaultdrop.core.dictationText
import dev.vaultdrop.core.splice
import dev.vaultdrop.voice.Dictation
import dev.vaultdrop.voice.findVoiceOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

enum class Screen { FirstRun, Verifying, Editor }

/** Something voice input needs that only the activity can start. */
enum class VoiceLaunch {
    /** Android's own voice input, `RECOGNIZE_SPEECH`. */
    Recognizer,

    /** `RECORD_AUDIO`, the first time an Open Transcribe app is used. */
    MicPermission,
}

/** The strip above the keyboard while an Open Transcribe app listens. */
data class DictationUi(
    /** The app doing the listening, as the launcher names it. */
    val engine: String,
    /** Everything heard so far. */
    val heard: String = "",
    /** Stop was pressed and the app is working through the end. */
    val finishing: Boolean = false,
)

/** One row of the sheet that asks which app takes speech. */
data class VoiceOption(
    val engine: VoiceEngine,
    val label: String,
    val detail: String,
    /** The app picked last time, still installed and signed as it was then. */
    val current: Boolean,
)

data class VoicePicker(
    val options: List<VoiceOption>,
    /** Why the sheet is up, when it is not just the first time. */
    val note: String?,
    /** Raised by the mic, so picking starts listening; from the menu, picking only saves. */
    val thenListen: Boolean,
)

/** An app the notice offers to open, for as long as [notice] is the one on screen. */
data class NoticeApp(val label: String, val packageName: String, val notice: String)

data class VerifyLine(val label: String, val value: String)

data class DayRow(
    val date: LocalDate,
    val label: String,
    val sub: String,
    val isCurrent: Boolean,
    /**
     * Whether the day has a note. False means tapping this row is what would
     * create it; null means nobody has been able to say yet — the walk behind
     * [DayIndex] has not landed, so there is nothing to answer from until the
     * provider is asked.
     */
    val exists: Boolean?,
)

/**
 * The note a capture started on, held from the moment it starts until what it
 * captured lands. [EditorViewModel.isStillOpen] says whether that is still the
 * note on screen.
 */
data class Pin(val generation: Int, val note: NotePath)

data class UiState(
    val screen: Screen = Screen.FirstRun,
    // First run
    val vaultErrorTitle: String? = null,
    val vaultErrorDetail: String? = null,
    // Verifying
    val verifyLines: List<VerifyLine> = emptyList(),
    val verifyStep: Int = 0,
    val verifyReady: Boolean = false,
    val shaLine: String = "",
    // Editor
    val dateLabel: String = "",
    /** The open note's path in the vault, which the delete confirmation names. */
    val notePath: String = "",
    val frontmatter: String = "",
    val fmOpen: Boolean = false,
    val created: Boolean = false,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    /** The day a step would land on, or null at that edge. */
    val prevDayLabel: String? = null,
    val nextDayLabel: String? = null,
    /**
     * A capture is working on the note on screen: a photo being copied in, a
     * dictation being transcribed. Nothing that leaves the note works until it
     * lands. See [EditorViewModel.pin].
     */
    val captureInFlight: Boolean = false,
    val configMissing: Boolean = false,
    val config: VaultConfig = VaultConfig(),
    // Phase 2
    /** Pass one is still running: no names yet, so the `[[` picker has nothing. */
    val indexing: Boolean = true,
    val indexSize: Int = 0,
    // Link picker
    val linkOpen: Boolean = false,
    val linkQuery: String = "",
    val linkMatches: List<IndexEntry> = emptyList(),
    val linkCount: String = "",
    // Overlays
    val notice: String? = null,
    /** Scrib with no speech model yet, say. See [NoticeApp]. */
    val noticeApp: NoticeApp? = null,
    val daysOpen: Boolean = false,
    val days: List<DayRow> = emptyList(),
    val deleteOpen: Boolean = false,
    /** What the vault's own `trashOption` asks for; the confirmation says so. */
    val deleteMode: TrashOption = TrashOption.DEFAULT,
    /** Set once, the first time a `system` vault meets what Android will not do. */
    val explainTrashFallback: Boolean = false,
    val reindexOpen: Boolean = false,
    // Search
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val searchStats: String = "",
    /** A full-text scan is reading the vault right now. */
    val searchScanning: Boolean = false,
    val openedNote: IndexEntry? = null,
    // Voice
    val dictation: DictationUi? = null,
    val voicePicker: VoicePicker? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf(UiState())
        private set

    /**
     * The note body, owned by the field rather than by [state].
     *
     * A keystroke used to copy the whole of [UiState] and invalidate everything
     * reading it — the top bar, the frontmatter chip, the notice, every overlay
     * test — for a change only the text field cares about. The field mutates
     * this in place instead, so a character costs its own relayout and nothing
     * else. It is also what lets [insertLink] splice a link in without
     * rebuilding the note around it.
     */
    val body = TextFieldState()

    private val store = ConfigStore(app)

    private var repo: VaultRepository? = null
    private var note: OpenNote? = null

    /**
     * The config the open session resolves paths with. Phase 2 may discover a
     * newer one, but the brief is explicit: a changed config applies to
     * tomorrow's note and beyond, never to the note already on screen.
     */
    private var activeConfig: VaultConfig = VaultConfig()

    /** Written by the walk on IO, read by the search and link jobs off it. */
    @Volatile
    private var index: WikiIndex = WikiIndex.EMPTY
    private var templateCache: String? = null

    /**
     * The Linter plugin's `yaml-timestamp` settings, as they are in the vault
     * right now. Unlike [activeConfig] this is picked up live: it does not decide
     * where a note lives, only what gets stamped into the next save, so applying
     * a change immediately is what the user editing it in Obsidian would expect.
     */
    private var linterConfig: LinterConfig = LinterConfig()

    /**
     * `.obsidian/app.json`. Picked up live like [linterConfig] and for the same
     * reason: it does not decide where a note lives, only what happens when one
     * is deleted and where an attachment goes, so honouring a change straight
     * away is what the user editing it in Obsidian would expect.
     *
     * Written on IO by Phase 2, Reload config and every [attach], and read on
     * the main thread by delete.
     */
    @Volatile
    private var appConfig: AppConfig = AppConfig()

    /** The body as last written, so a save that would change nothing is skipped. */
    private var savedBody: String? = null
    /**
     * The day on screen, and the day that was "today" when it was opened. The
     * pair is what lets a resume notice that midnight went past: an offset from
     * `LocalDate.now()` silently means something different after the clock rolls.
     */
    private var openDate: LocalDate = LocalDate.now()
    private var anchoredTo: LocalDate = LocalDate.now()

    /**
     * Which days already have a note. Null until the first walk lands, which is
     * what keeps both chevrons dim rather than guessing at the edges.
     */
    private var dayIndex: DayIndex? = null

    /**
     * What this session knows about single days that the walk behind [dayIndex]
     * does not: a note the day sheet just created, a day a step found missing,
     * the sheet's own probes. Re-applied on every rebuild, so a walk that predates
     * them cannot undo them. Main thread only.
     */
    private val dayEdits = mutableMapOf<LocalDate, Boolean>()
    private var phase1Done = false
    private var phase2Started = false

    /**
     * The note-open in flight, and which one it is.
     *
     * Only creating a note goes through a job — reading one is a single round
     * trip and lands in the frame that asked for it. A create that comes back to
     * find the counter moved on has been navigated past, and drops its result
     * rather than pulling the screen back to the day it was asked about.
     */
    private var openJob: Job? = null
    private var openGeneration = 0

    /** How many captures hold a [Pin] right now. Main thread only. */
    private var pins = 0

    /** Midnight went past while a capture held the note; the last [unpin] catches up. */
    private var rollOverPending = false

    /** The day sheet's probes. One sheet's worth at a time. */
    private var dayProbeJob: Job? = null

    /** The one vault walk in flight, whoever started it. */
    private var indexJob: Job? = null

    /** The config re-read in flight. Separate from the walk — they share nothing. */
    private var configJob: Job? = null

    /**
     * The three debounced jobs behind the two text fields. Each is cancelled and
     * relaunched by the keystroke after it, so what the main thread does per
     * character is set a string and start a coroutine — never a scan.
     */
    private var searchJob: Job? = null
    private var scanJob: Job? = null
    private var linkJob: Job? = null

    /**
     * The query whose full-text results are on screen, if any. A walk landing
     * behind a scan would otherwise swap the hits the user asked for back to the
     * name matches, without a keystroke to explain it.
     */
    private var scannedQuery: String? = null
    private val walking: Boolean get() = indexJob?.isActive == true

    init {
        // The `[[` strip follows the caret. Cheap enough to run per edit:
        // LinkScan works in indices and never copies the note.
        viewModelScope.launch {
            snapshotFlow { body.text to body.selection }.collect { (text, selection) ->
                onBodyEdited(text, selection.start)
            }
        }
        // Autosave. collectLatest cancels the pending delay whenever the text
        // moves again, which is the cancel-on-next-keystroke the explicit job
        // used to do by hand — without a job to allocate and cancel per
        // character. The first emission arrives before there is a note to save
        // to, and saveNow stops at that.
        viewModelScope.launch {
            snapshotFlow { body.text }.collectLatest {
                delay(SAVE_DEBOUNCE_MS)
                saveNow()
            }
        }
    }

    // ── Phase 1 ──────────────────────────────────────────────────────────────

    /**
     * Runs on the main thread before the first frame, by design: one
     * SharedPreferences read and at most one `openInputStream`. No config
     * re-read, no scan, no mtime check, no network.
     *
     * *At most* one, and only ever a read. Today's note usually exists, and then
     * this is the single round trip the design is built around. When it does not
     * — the first launch of a new day, which is the case this app is for —
     * creating it costs a listing per directory in its path, a `createDocument`
     * and a write, and all of that happens behind the first frame rather than in
     * front of it. See [createDay].
     */
    fun phase1() {
        if (phase1Done) return
        phase1Done = true

        val uri = store.vaultUri
        val cached = store.loadConfig()
        if (uri == null || cached == null) {
            state = state.copy(screen = Screen.FirstRun)
            return
        }
        activeConfig = cached
        // Cached from the last run, so a note created before the first frame is
        // still stamped. Phase 2 refreshes it against the plugin's own file.
        linterConfig = store.loadLinter()
        appConfig = store.loadApp()
        val repository = VaultRepository(VaultFiles(getApplication<Application>(), uri))
        repo = repository

        val today = LocalDate.now()
        val existing = repository.readNote(cached, today)
        if (existing != null) {
            // The one filesystem operation on the critical path, and it worked.
            // Nothing asks about the grant: a note that just opened plainly
            // still has one, and asking costs a binder round trip of its own.
            adopt(++openGeneration, existing, today, showCreatedNotice = false)
        } else {
            // No note for today yet — or no permission left to see it. The grant
            // is worth its round trip only here, and only ahead of the create:
            // it is what keeps the editor from flashing up for a vault we have
            // lost, and it is far cheaper than the create it now stands in front
            // of rather than behind.
            if (!hasGrant(uri)) {
                store.clear()
                repo = null
                note = null
                state = UiState(screen = Screen.FirstRun)
                return
            }
            createDay(today, showCreatedNotice = false)
        }
        state = state.copy(screen = Screen.Editor, config = cached, configMissing = !cached.present)
    }

    private fun hasGrant(uri: Uri): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    // ── First run ────────────────────────────────────────────────────────────

    /** Result of `ACTION_OPEN_DOCUMENT_TREE`. Validates, parses, then verifies on screen. */
    fun onVaultPicked(uri: Uri?) {
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        val files = VaultFiles(getApplication<Application>(), uri)
        val candidate = VaultRepository(files)
        val name = folderName(uri)

        if (!candidate.isVault()) {
            state = state.copy(
                screen = Screen.FirstRun,
                vaultErrorTitle = "Not a vault",
                vaultErrorDetail = ".obsidian/ not found in $name",
            )
            return
        }

        repo = candidate
        state = state.copy(vaultErrorTitle = null, vaultErrorDetail = null)
        runVerify(uri, candidate)
    }

    private fun folderName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { "that folder" }
            ?: "that folder"

    /**
     * The READING CONFIG screen. The work itself takes a few milliseconds; the
     * cadence exists so the parse is legible before the editor takes over.
     */
    private fun runVerify(uri: Uri, repository: VaultRepository) {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { repository.readConfig() }
            activeConfig = config
            store.saveVault(uri, config)
            linterConfig = withContext(Dispatchers.IO) { repository.readLinter() }
            store.saveLinter(linterConfig)
            appConfig = withContext(Dispatchers.IO) { repository.readAppConfig() }
            store.saveApp(appConfig)
            WikiIndex.clear(getApplication<Application>())

            val lines = listOf(
                VerifyLine(".obsidian/", "found"),
                VerifyLine("daily-notes.json", if (config.present) "read" else "not found"),
                VerifyLine("format", config.format),
                VerifyLine("folder", config.folder.ifEmpty { "vault root" }),
                VerifyLine("template", config.template.ifEmpty { "none" }),
                VerifyLine("linter", linterLine()),
                VerifyLine("deleted files", trashLine()),
                VerifyLine("attachments", attachmentLine()),
            )
            state = state.copy(
                screen = Screen.Verifying,
                verifyLines = lines,
                verifyStep = 0,
                shaLine = if (config.present) "sha256 ${Hashing.elide(config.sha256)}" else "no config file",
                config = config,
                configMissing = !config.present,
            )
            for (i in 1..lines.size) {
                delay(STEP_MS)
                state = state.copy(verifyStep = i)
            }
            delay(STEP_MS + 140)

            openDay(LocalDate.now(), create = true, showCreatedNotice = false)
            state = state.copy(verifyReady = true)
        }
    }

    /** The user has reviewed the READING CONFIG checklist and wants to continue. */
    fun onVerifyContinue() {
        if (!state.verifyReady) return
        state = state.copy(screen = Screen.Editor)
    }

    // ── Phase 2 ──────────────────────────────────────────────────────────────

    /** Started only once the editor is on screen and the caret is live. */
    fun startPhase2() {
        val repository = repo ?: return
        if (phase2Started) return
        phase2Started = true
        indexJob = viewModelScope.launch(Dispatchers.IO) {
            // 1 & 2. Config, then the template it names.
            val fresh = readConfigAndTemplate(repository)
            if (fresh != null) {
                withContext(Dispatchers.Main) {
                    state = state.copy(
                        config = fresh,
                        configMissing = !fresh.present,
                        notice = if (fresh.locationDiffers(activeConfig)) {
                            "daily-notes.json changed. New format applies from tomorrow — this note is untouched."
                        } else {
                            state.notice
                        },
                    )
                }
            }

            // 3. Index: serve the cache first, then refresh it.
            // An empty cache is no cache: it serves nothing and it is not
            // evidence that the vault was walked recently either.
            val cache = WikiIndex.load(getApplication<Application>())?.takeIf { it.size > 0 }
            if (cache != null) {
                index = cache
                val days = buildDayIndex(cache.paths)
                withContext(Dispatchers.Main) {
                    state = state.copy(indexing = false, indexSize = cache.size)
                    adoptDayIndex(days)
                    scheduleLinkMatches()
                    scheduleSearch()
                }
            }

            // A walk is one cursor per directory in the vault, into the same
            // provider the editor has just read today's note out of. A cache
            // written minutes ago is worth more than the notes a walk would add
            // to it, so the launch pass takes the same staleness rule the resume
            // pass does. Reindex vault is there for when you want one regardless.
            val age = System.currentTimeMillis() - WikiIndex.savedAt(getApplication<Application>())
            if (cache != null && age < RESUME_REINDEX_AFTER_MS) return@launch

            // Then the listings themselves, which open no files at all.
            val startedAt = System.currentTimeMillis()
            val names = repository.buildNameIndex()
            ensureActive() // the vault may have been changed out from under the walk
            val elapsed = System.currentTimeMillis() - startedAt
            val coldBuild = cache == null
            publishNames(
                names,
                notice = if (coldBuild) {
                    "Phase 2 done — ${names.size} notes indexed in ${elapsed}ms. Wiki-links ready."
                } else {
                    null
                },
            )
        }
    }

    /**
     * The walk, published: names, paths and the day edges.
     *
     * That is the whole index now. The `[[` picker matches on [IndexEntry.name]
     * and [DayIndex] is built from paths, so nothing here waits on a file open —
     * there are none. Full text is [VaultSearch]'s, on demand.
     */
    private suspend fun publishNames(names: WikiIndex, notice: String?) {
        index = names
        WikiIndex.save(getApplication<Application>(), names)
        val days = buildDayIndex(names.paths)
        withContext(Dispatchers.Main) {
            adoptDayIndex(days)
            state = state.copy(
                indexing = false,
                indexSize = names.size,
                notice = notice ?: state.notice,
            )
            scheduleLinkMatches()
            scheduleSearch()
        }
    }

    /**
     * Re-reads `.obsidian/daily-notes.json` and the template it names.
     *
     * [activeConfig] is deliberately left alone: the brief is explicit that a
     * changed config applies to tomorrow's note, never to the one on screen.
     *
     * @return the new config, or null if the file has not moved since we cached it.
     */
    private fun readConfigAndTemplate(repository: VaultRepository): VaultConfig? {
        val fresh = repository.configChangedSince(activeConfig)
        if (fresh != null) store.saveConfig(fresh)
        templateCache = repository.readTemplate(fresh ?: activeConfig)

        // The Linter's settings are a settings file like any other, so they are
        // probed the same way and on the same trigger. Unlike the daily-note
        // config this one is adopted immediately — it changes what the next save
        // stamps, not where the open note lives.
        repository.linterChangedSince(linterConfig)?.let {
            linterConfig = it
            store.saveLinter(it)
        }
        // Same treatment, same reason: `trashOption` and the attachment settings
        // are read live so a change made in Obsidian applies to the next delete
        // or attachment, not the next launch.
        refreshAppConfig(repository)
        return fresh
    }

    /**
     * Probes `.obsidian/app.json` and adopts it if it moved. IO.
     *
     * Phase 2 and Reload config come through here, and so does every [attach]:
     * resuming probes nothing, so a folder changed in Obsidian since then would
     * otherwise send the next photo to the old one.
     */
    private fun refreshAppConfig(repository: VaultRepository): AppConfig {
        repository.appChangedSince(appConfig)?.let {
            appConfig = it
            store.saveApp(it)
        }
        return appConfig
    }

    // ── Editing ──────────────────────────────────────────────────────────────

    /**
     * What an edit to [body] costs, off the field's own observer.
     *
     * Typing outside a `[[` is every other keystroke, and it must cost nothing:
     * the strip is already closed and already has no query, so there is nothing
     * to write and no reason to copy [UiState]. Only a change to the strip's own
     * state is worth a frame.
     */
    private fun onBodyEdited(text: CharSequence, caret: Int) {
        val query = LinkScan.queryAt(text, caret)
        val open = query != null
        if (open == state.linkOpen && query.orEmpty() == state.linkQuery) return

        state = state.copy(linkOpen = open, linkQuery = query.orEmpty())
        if (open) scheduleLinkMatches() else linkJob?.cancel()
    }

    /**
     * Matches for the `[[` strip, debounced and off the main thread.
     *
     * This used to run inline in [onBodyChange] — a scan of every name in the
     * vault, twice, between the keypress and the frame, while someone was typing
     * into their daily note. The strip fills a beat behind the caret instead,
     * which is what an autocomplete is supposed to do anyway.
     */
    private fun scheduleLinkMatches() {
        if (!state.linkOpen) return
        linkJob?.cancel()
        val query = state.linkQuery
        val snapshot = index
        linkJob = viewModelScope.launch {
            delay(LINK_DEBOUNCE_MS)
            val hits = withContext(Dispatchers.Default) { snapshot.nameHits(query, LINK_LIMIT) }
            // The caret may have left the `[[` while we were away.
            if (!state.linkOpen || state.linkQuery != query) return@launch
            state = state.copy(
                linkMatches = hits.matches,
                linkCount = if (state.indexing) "" else "${hits.total} of ${snapshot.size}",
            )
        }
    }

    /**
     * Completes the `[[` the user was already typing.
     *
     * Splices the link into the field's own buffer. Rebuilding the note around
     * it — two substrings of the whole thing and a concatenation — copied a
     * diary entry three times over to insert nine characters into it.
     */
    fun insertLink(name: String) {
        val caret = body.selection.start.coerceIn(0, body.text.length)
        val cut = LinkScan.openBracketBefore(body.text, caret)
        if (cut == -1) return
        val replacement = "[[$name]] "
        body.edit {
            replace(cut, caret, replacement)
            selection = TextRange(cut + replacement.length)
        }
        state = state.copy(linkOpen = false, linkQuery = "")
    }

    fun toggleFrontmatter() {
        state = state.copy(fmOpen = !state.fmOpen)
    }

    /**
     * Also called from the activity's onPause, so nothing is lost on the way out.
     *
     * A save that would write the same bytes back is skipped. That used to be
     * merely wasteful; now that a write moves `date modified`, leaving on and
     * coming back to a note would have restamped it without a word being typed.
     */
    fun saveNow() {
        val repository = repo ?: return
        val current = note ?: return
        val text = body.text.toString()
        if (text == savedBody) return
        savedBody = text

        viewModelScope.launch(Dispatchers.IO) {
            val written = repository.save(current, text, linterConfig)
            if (written == null) {
                // Don't go on believing this body reached the file — let the next
                // save try again instead of stranding it behind the skip check.
                withContext(Dispatchers.Main) { if (savedBody == text) savedBody = null }
                return@launch
            }
            if (written == current.frontmatter) return@launch
            // The stamp moved, so the note in hand and the chip both have to
            // follow, or the next save would edit the fence we just replaced.
            val stamped = current.copy(frontmatter = written)
            withContext(Dispatchers.Main) {
                if (note === current) {
                    note = stamped
                    state = state.copy(frontmatter = written)
                }
            }
        }
    }

    // ── Capturing ────────────────────────────────────────────────────────────

    /**
     * Holds the note on screen for a capture, until [unpin].
     *
     * A photo or a dictation that finishes after you have moved to another day
     * would land in the wrong note. So while any capture holds a pin, the
     * chevrons, the swipe, the day sheet and delete are off, and a rollover past
     * midnight waits for the last one to let go. Every pin needs exactly one
     * [unpin], however the capture ends.
     *
     * The note may still be being created when the capture starts. The pin
     * holds the day rather than the [OpenNote], through [openGeneration], the
     * same test [createDay] makes, so text that lands in that gap goes in after
     * the template like anything else typed there.
     *
     * @return null when there is no note on screen to pin.
     */
    fun pin(): Pin? {
        if (repo == null || state.screen != Screen.Editor) return null
        pins++
        state = state.copy(captureInFlight = true, daysOpen = false, deleteOpen = false)
        return Pin(openGeneration, note?.path ?: DailyNote.resolve(activeConfig, openDate))
    }

    /** Lets go of one [pin]. The last one out turns leaving back on. */
    fun unpin() {
        if (pins == 0) return
        if (--pins > 0) return
        state = state.copy(captureInFlight = false)
        if (rollOverPending) {
            rollOverPending = false
            rollOver()
        }
    }

    /** Whether [pin]'s note is still the one on screen. Check before splicing anything in. */
    fun isStillOpen(pin: Pin): Boolean = pin.generation == openGeneration

    /**
     * Puts [insert] into the note at the caret, as a line of its own or as
     * prose; see [splice]. Through the field's own buffer, like [insertLink], so
     * the note is not copied around it, and autosave picks it up from there.
     *
     * The caret is the far end of any selection, so selected text stays.
     */
    fun insertAtCaret(insert: String, block: Boolean) {
        val edit = splice(body.text, body.selection.max, insert, block)
        if (edit.text.isEmpty()) return
        body.edit {
            replace(edit.start, edit.end, edit.text)
            selection = TextRange(edit.caret)
        }
    }

    /**
     * Copies [source] into the vault as an attachment of [pin]'s note, and
     * returns the embed for it. Null when nothing was written, including when
     * the note is no longer on screen.
     *
     * `app.json` is probed first, every time; see [refreshAppConfig]. When the
     * file has not moved that is one metadata query, and it means Obsidian's
     * settings decide where the file goes at the moment it is written.
     *
     * Call from the main thread. The copy runs on IO. [source] is not closed
     * here, and nothing is spliced: check [isStillOpen] again when this
     * returns, then hand the embed to [insertAtCaret].
     */
    suspend fun attach(pin: Pin, stem: String, ext: String, source: InputStream): String? {
        val repository = repo ?: return null
        if (!isStillOpen(pin)) return null
        return withContext(Dispatchers.IO) {
            val app = refreshAppConfig(repository)
            repository.attach(pin.note, stem, ext, source, app)?.let { Attachments.embed(it, pin.note, app) }
        }
    }

    // ── Voice ────────────────────────────────────────────────────────────────

    /**
     * What voice input needs the activity to launch. A buffer rather than a
     * field in [state], so a request made while the activity is being
     * recreated waits for the new one instead of being lost or launched twice.
     */
    val voiceLaunches = Channel<VoiceLaunch>(Channel.BUFFERED)

    /** The package queries behind a mic tap or the menu's Voice input…. */
    private var voiceQuery: Job? = null

    /** Android's recogniser is up, and this is waiting for what it heard. */
    private var recognized: CompletableDeferred<String?>? = null

    /** The Open Transcribe dictation in progress, if any. */
    private var dictation: Dictation? = null

    /** An app picked for dictation, waiting on the answer to `RECORD_AUDIO`. */
    private var awaitingMic: Transcribing? = null

    /** An Open Transcribe app and the name the screen uses for it. */
    private data class Transcribing(val engine: VoiceEngine.OpenTranscribe, val label: String)

    /**
     * The mic. Works out what can take speech, on IO and only now: package
     * queries are binder calls, and Phase 1 makes none. Then it uses the one
     * answer, asks, or says there is nothing to use; see [chooseVoice].
     */
    fun onMic() {
        if (state.captureInFlight || voiceQuery?.isActive == true) return
        voiceQuery = viewModelScope.launch {
            val options = withContext(Dispatchers.IO) { findVoiceOptions(getApplication()) }
            when (val choice = chooseVoice(options, store.voiceEngine)) {
                is VoiceChoice.Use -> use(choice.engine, labelFor(choice.engine, options))
                is VoiceChoice.Ask -> askVoice(options, choice.reason, thenListen = true)
                VoiceChoice.Unavailable -> state = state.copy(notice = NO_VOICE)
            }
        }
    }

    /** Overflow: Voice input…. The same sheet, to change the pick without dictating. */
    fun openVoicePicker() {
        if (voiceQuery?.isActive == true) return
        voiceQuery = viewModelScope.launch {
            val options = withContext(Dispatchers.IO) { findVoiceOptions(getApplication()) }
            if (options.transcribers.isEmpty() && !options.system) {
                state = state.copy(notice = NO_VOICE)
            } else {
                askVoice(options, reason = null, thenListen = false)
            }
        }
    }

    private fun askVoice(options: VoiceOptions, reason: AskReason?, thenListen: Boolean) {
        val stored = store.voiceEngine
        val rows = options.transcribers.map { app ->
            VoiceOption(
                engine = app.engine,
                label = app.label,
                detail = "Open Transcribe · VaultDrop records, ${app.label} transcribes",
                current = app.engine == stored,
            )
        } + listOfNotNull(
            VoiceOption(
                engine = VoiceEngine.System,
                label = systemLabel(options),
                detail = "Android's voice input · opens its own window",
                current = stored == VoiceEngine.System,
            ).takeIf { options.system },
        )
        val note = when (reason) {
            AskReason.Changed -> {
                val name = options.transcribers
                    .firstOrNull { stored is VoiceEngine.OpenTranscribe && it.engine.sameService(stored) }
                    ?.label ?: "The app picked before"
                "$name is signed with a different key than the copy you picked. VaultDrop " +
                    "hands this app your voice, so pick it again only if you trust where it came from."
            }
            AskReason.Gone -> "The app picked before is not installed any more."
            AskReason.FirstTime, null -> null
        }
        state = state.copy(
            voicePicker = VoicePicker(rows, note, thenListen),
            daysOpen = false,
            deleteOpen = false,
        )
    }

    /** A row of the sheet. Remembered from now on, and used at once if the mic raised the sheet. */
    fun pickVoice(option: VoiceOption) {
        val picker = state.voicePicker ?: return
        state = state.copy(voicePicker = null)
        store.voiceEngine = option.engine
        if (picker.thenListen && !state.captureInFlight) use(option.engine, option.label)
    }

    fun closeVoicePicker() {
        state = state.copy(voicePicker = null)
    }

    private fun use(engine: VoiceEngine, label: String) = when (engine) {
        VoiceEngine.System -> recognize()
        is VoiceEngine.OpenTranscribe -> dictate(Transcribing(engine, label))
    }

    private fun labelFor(engine: VoiceEngine, options: VoiceOptions): String = when (engine) {
        VoiceEngine.System -> systemLabel(options)
        is VoiceEngine.OpenTranscribe ->
            options.transcribers.firstOrNull { it.engine == engine }?.label ?: engine.packageName
    }

    private fun systemLabel(options: VoiceOptions): String =
        options.systemLabel?.let { "Voice input ($it)" } ?: "System voice input"

    /**
     * Hands over to Android's voice input and waits for what it heard. The
     * note is pinned for as long as the recogniser's window is up, and
     * `onPause` has saved it before that window appears.
     */
    private fun recognize() {
        val pin = pin() ?: return
        val heard = CompletableDeferred<String?>()
        recognized = heard
        viewModelScope.launch {
            try {
                voiceLaunches.send(VoiceLaunch.Recognizer)
                heard.await()?.let { land(pin, it) }
            } finally {
                if (recognized === heard) recognized = null
                unpin()
            }
        }
    }

    /** The recogniser's answer, or null when it was dismissed or heard nothing. */
    fun onRecognized(text: String?) {
        val waiting = recognized
        if (waiting != null) {
            waiting.complete(text)
            return
        }
        // Nothing is waiting when Android killed the process behind the
        // recogniser's window. The text still arrives, into whatever note the
        // relaunch opened, rather than nowhere.
        if (text == null) return
        val pin = pin() ?: return
        try {
            land(pin, text)
        } finally {
            unpin()
        }
    }

    /** No app took the intent after all; it was uninstalled since the tap looked. */
    fun onRecognizerMissing() {
        recognized?.complete(null)
        state = state.copy(notice = NO_VOICE)
    }

    /**
     * Listens through an Open Transcribe app until it settles. The microphone
     * permission is asked for here, the first time it is needed, and never
     * before.
     *
     * The note is pinned from the first word until the text lands, and the
     * strip above the keyboard shows what has been heard so far.
     */
    private fun dictate(chosen: Transcribing) {
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingMic = chosen
            voiceLaunches.trySend(VoiceLaunch.MicPermission)
            return
        }
        val pin = pin() ?: return
        val events = Channel<VoiceEvent>(Channel.UNLIMITED)
        val session = Dictation.start(app, chosen.engine) { events.trySend(it) }
        dictation = session
        state = state.copy(dictation = DictationUi(chosen.label))
        viewModelScope.launch {
            try {
                for (event in events) {
                    when (event) {
                        is VoiceEvent.Progress ->
                            state.dictation?.let { state = state.copy(dictation = it.copy(heard = event.text)) }
                        is VoiceEvent.Final -> {
                            if (!land(pin, event.text) && isStillOpen(pin)) {
                                state = state.copy(notice = "${chosen.label} heard nothing.")
                            }
                            break
                        }
                        is VoiceEvent.Failure -> {
                            dictationFailed(pin, chosen, event.reason)
                            break
                        }
                    }
                }
            } finally {
                session.cancel() // nothing, once it has ended
                if (dictation === session) dictation = null
                state = state.copy(dictation = null)
                unpin()
            }
        }
    }

    /** The answer to `RECORD_AUDIO`. */
    fun onMicPermission(granted: Boolean) {
        val waiting = awaitingMic ?: return
        awaitingMic = null
        if (granted) {
            dictate(waiting)
        } else {
            state = state.copy(
                notice = "VaultDrop needs the microphone to send your voice to ${waiting.label}. " +
                    "Allow it in Android's settings for VaultDrop, or switch to system voice " +
                    "input under Voice input… in the menu.",
            )
        }
    }

    /** The strip's Stop, and leaving the app. What was heard is still transcribed and still lands. */
    fun stopDictation() {
        val session = dictation ?: return
        session.stop()
        state.dictation?.let { state = state.copy(dictation = it.copy(finishing = true)) }
    }

    /** The strip's Cancel. Nothing lands. */
    fun cancelDictation() {
        dictation?.cancel()
    }

    /**
     * Says why a dictation ended without its transcript. Whatever was heard
     * before a failure is kept: it was said, and it was transcribed.
     */
    private fun dictationFailed(pin: Pin, chosen: Transcribing, reason: VoiceFailure) {
        if (reason == VoiceFailure.Cancelled) return
        val kept = state.dictation?.heard?.let { land(pin, it) } == true
        val name = chosen.label
        val why = when (reason) {
            VoiceFailure.ModelNotReady -> "$name has no speech model yet. Open it to download one, then try the mic again."
            VoiceFailure.Busy -> "$name is busy with something else. Try again in a moment."
            VoiceFailure.Unreachable -> "Could not reach $name. Voice input… in the menu picks another app."
            VoiceFailure.Died -> "$name stopped before it finished."
            VoiceFailure.TimedOut -> "$name went quiet for three minutes, so VaultDrop stopped waiting."
            VoiceFailure.Microphone -> "The microphone would not start. Another app may be using it."
            is VoiceFailure.Engine -> "$name could not transcribe that" + (reason.message?.let { ": $it" } ?: ".")
            VoiceFailure.Cancelled -> return
        }
        val notice = if (kept) "$why What it heard before that is in the note." else why
        state = state.copy(
            notice = notice,
            noticeApp = if (reason == VoiceFailure.ModelNotReady) {
                NoticeApp(name, chosen.engine.packageName, notice)
            } else {
                null
            },
        )
    }

    /**
     * Splices what was heard into [pin]'s note as prose, if that is still the
     * note on screen. False when nothing went in.
     */
    private fun land(pin: Pin, heard: String): Boolean {
        if (!isStillOpen(pin)) return false
        val text = dictationText(heard, body.text.getOrNull(body.selection.max))
        if (text.isEmpty()) return false
        insertAtCaret(text, block = false)
        return true
    }

    // ── Day navigation ───────────────────────────────────────────────────────

    /**
     * A step lands on the nearest day that already *has* a note, skipping any gap
     * whole rather than filling it in a file at a time. Stepping is not how a
     * note gets made — tapping a day in the sheet is.
     */
    fun stepDay(direction: Int) {
        if (state.captureInFlight) return
        val target = if (direction < 0) dayIndex?.before(openDate) else dayIndex?.after(openDate)
        if (target == null || target.isAfter(LocalDate.now())) return
        saveNow()
        openDay(target, create = false, showCreatedNotice = false)
    }

    fun goToDate(date: LocalDate) {
        if (state.captureInFlight) return
        saveNow()
        openDay(date, create = true, showCreatedNotice = true)
        state = state.copy(daysOpen = false)
    }

    /**
     * Read-first for every day, not just today: an existing note is loaded
     * untouched, a missing one is created from the template exactly once — and
     * only when [create] says this is a path that is allowed to create at all.
     *
     * A step passes false. Its target always came out of [dayIndex], so a miss
     * means the vault has moved on underneath the walk; correcting the index and
     * staying put is the honest answer, not writing the note the user was only
     * navigating past.
     *
     * The read stays on the main thread. It is one round trip, it lands inside
     * the frame the tap was in, and there is no honest thing to show in the
     * meantime — the day on screen would be neither the old one nor the new one.
     * Creating is the half that moves off it; see [createDay].
     */
    private fun openDay(date: LocalDate, create: Boolean, showCreatedNotice: Boolean) {
        val repository = repo ?: return
        val generation = ++openGeneration
        val existing = repository.readNote(activeConfig, date)
        when {
            existing != null -> adopt(generation, existing, date, showCreatedNotice)
            create -> createDay(date, showCreatedNotice)
            else -> {
                recordDays(mapOf(date to false))
                state = state.copy(
                    notice = "${DailyNote.label(activeConfig, date)} is not in the vault any more.",
                )
            }
        }
    }

    /**
     * The miss half of [openDay], behind the frame rather than in front of it.
     *
     * Creating a note is a listing per directory in its path, a `createDocument`
     * and a write — three to six round trips out to the provider. That is far
     * too much to hold a frame for, and it is exactly what the first launch of
     * every new day used to pay before anything reached the screen. The editor
     * takes the new day straight away, empty and with a live caret, and the note
     * fills in behind it.
     */
    private fun createDay(date: LocalDate, showCreatedNotice: Boolean) {
        val repository = repo ?: return
        val generation = ++openGeneration
        openJob?.cancel()

        // Everything the screen needs that the file cannot tell us: the label is
        // the date through the config's own format, and the edges come off the
        // day index. Neither waits on the provider.
        note = null
        savedBody = ""
        openDate = date
        anchoredTo = LocalDate.now()
        body.clearText()
        state = state.copy(
            dateLabel = DailyNote.label(activeConfig, date),
            notePath = "",
            frontmatter = "",
            created = false,
            fmOpen = false,
            linkOpen = false,
            linkQuery = "",
            notice = null,
            daysOpen = false,
        )
        refreshEdges()

        openJob = viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) {
                repository.openNote(
                    config = activeConfig,
                    date = date,
                    now = LocalDateTime.now(),
                    templateCache = templateCache,
                    linter = linterConfig,
                )
            }
            // Navigated on while we were away. Say nothing and change nothing:
            // the day this was asked about is not the day on screen any more.
            if (generation != openGeneration) return@launch
            adopt(generation, opened, date, showCreatedNotice, fromCreate = true)
        }
    }

    /**
     * Puts an opened note on screen.
     *
     * [fromCreate] marks the second half of [createDay], and changes two things.
     * Anything typed while the file was being made belongs *after* the template,
     * which is where the caret was — the end of an empty note; leaving
     * [savedBody] on the seed is what sends the next autosave after it, because
     * the file has only the seed in it. And the notice is left alone rather than
     * cleared: the navigation itself already cleared it, so anything on screen
     * now was raised *since*, and is newer than the note landing. Clearing it
     * here ate the "moved to .trash/" line when the deleted note was the last
     * one in the vault.
     */
    private fun adopt(
        generation: Int,
        opened: OpenNote,
        date: LocalDate,
        showCreatedNotice: Boolean,
        fromCreate: Boolean = false,
    ) {
        if (generation != openGeneration) return
        note = opened
        savedBody = opened.body
        openDate = date
        anchoredTo = LocalDate.now()

        val typed = if (fromCreate) body.text.toString() else ""
        body.setTextAndPlaceCursorAtEnd(if (typed.isEmpty()) opened.body else opened.body + typed)

        state = state.copy(
            dateLabel = DailyNote.label(activeConfig, date),
            notePath = opened.path.relativePath,
            frontmatter = opened.frontmatter,
            created = opened.created,
            fmOpen = false,
            linkOpen = false,
            linkQuery = "",
            notice = when {
                opened.error != null -> opened.error
                opened.created && showCreatedNotice -> createdNotice(opened)
                fromCreate -> state.notice
                else -> null
            },
            daysOpen = false,
        )
        // A note this session just wrote is not in the walk the index came from,
        // so fold it in before the edges are read back off it.
        recordDays(if (opened.created) mapOf(date to true) else emptyMap())
    }

    /**
     * The chevrons, the swipe hints and the rubber-band all come off these two
     * lookups, so a lit button can never offer a step that would not happen.
     *
     * Both edges stay dead until [dayIndex] exists. Phase 2 serves the cached
     * index within milliseconds of the editor appearing, and a cold start has
     * them as soon as the name pass lands — seconds, not the whole walk. That
     * cold path is not only a first-ever launch: picking a vault clears the
     * cache, so it is any vault change, against a vault that may already hold
     * years of notes to step through.
     */
    private fun refreshEdges() {
        val prev = dayIndex?.before(openDate)
        val next = dayIndex?.after(openDate)?.takeIf { !it.isAfter(LocalDate.now()) }
        state = state.copy(
            canGoPrev = prev != null,
            canGoNext = next != null,
            prevDayLabel = prev?.let { DailyNote.label(activeConfig, it) },
            nextDayLabel = next?.let { DailyNote.label(activeConfig, it) },
        )
    }

    /** Applies what we have just learned first-hand about single days, then re-derives the edges. */
    private fun recordDays(changes: Map<LocalDate, Boolean>) {
        dayEdits.putAll(changes)
        dayIndex = dayIndex?.with(changes)
        refreshEdges()
    }

    /** [built] comes off the walk; [dayEdits] is what the walk could not know yet. */
    private fun adoptDayIndex(built: DayIndex) {
        dayIndex = built.with(dayEdits)
        refreshEdges()
    }

    private fun buildDayIndex(paths: Set<String>): DayIndex =
        DayIndex.build(activeConfig, paths, LocalDate.now(), DAY_SCAN_SPAN)

    private fun createdNotice(opened: OpenNote): String {
        val template = activeConfig.template
        return if (template.isBlank()) {
            "Created ${opened.path.relativePath}."
        } else {
            "Created ${opened.path.relativePath} from $template — {{date}} and {{time}} resolved."
        }
    }

    // ── Deleting ─────────────────────────────────────────────────────────────

    /**
     * The vault asked for the system trash and Android will not give a
     * folder-grant app access to it, so the note goes to `.trash/` instead. Said
     * once, the first time it matters, rather than every time or not at all.
     */
    fun askDelete() {
        if (state.captureInFlight) return
        val fallback = appConfig.trash == TrashOption.System && !store.trashFallbackExplained
        if (fallback) store.trashFallbackExplained = true
        state = state.copy(
            deleteOpen = true,
            deleteMode = appConfig.trash,
            explainTrashFallback = fallback,
            daysOpen = false,
            linkOpen = false,
        )
    }

    fun cancelDelete() {
        state = state.copy(deleteOpen = false)
    }

    /**
     * Moves the open note to `.trash/`, then falls back to the nearest note
     * either side of the day it occupied.
     *
     * The note in hand is dropped up front and the save is then finished by
     * hand. Both halves matter: [saveNow] and the debounce behind it both stop
     * at a null note, so nothing can write after the move and put the file
     * straight back — and a trashed copy missing the last thing typed is a worse
     * copy to recover than a complete one.
     */
    fun confirmDelete() {
        val repository = repo ?: return
        val current = note ?: return
        if (state.captureInFlight) return
        val date = openDate
        note = null
        state = state.copy(deleteOpen = false)

        viewModelScope.launch {
            val text = body.text.toString()
            val trashed = withContext(Dispatchers.IO) {
                if (text != savedBody) repository.save(current, text, linterConfig)
                repository.trash(current, appConfig.trash)
            }
            if (trashed == null) {
                // Still where it was, so saving can resume against it.
                note = current
                state = state.copy(notice = "Could not delete ${current.path.relativePath}.")
                return@launch
            }
            savedBody = null
            dropFromIndex(current.path.relativePath)
            recordDays(mapOf(date to false))

            // Nearest note either side, with today as the floor — the app's
            // contract is that there is always a note on screen, including in a
            // vault whose last one has just gone.
            val target = dayIndex?.before(date) ?: dayIndex?.after(date)
            if (target != null) {
                openDay(target, create = false, showCreatedNotice = false)
            } else {
                openDay(LocalDate.now(), create = true, showCreatedNotice = false)
            }
            state = state.copy(
                notice = when (trashed) {
                    is VaultRepository.Trashed.Moved ->
                        "Moved ${current.path.relativePath} to ${trashed.path}."
                    VaultRepository.Trashed.Deleted ->
                        "Deleted ${current.path.relativePath} — trashOption is none, so no copy was kept."
                },
            )
        }
    }

    /** A trashed note has to stop showing up in search and in the `[[` picker. */
    private fun dropFromIndex(path: String) {
        val remaining = index.all.filterNot { it.path == path }
        if (remaining.size == index.size) return
        index = WikiIndex(remaining)
        state = state.copy(indexSize = index.size)
        scheduleLinkMatches()
        scheduleSearch()
        val snapshot = index
        viewModelScope.launch(Dispatchers.IO) {
            WikiIndex.save(getApplication<Application>(), snapshot)
        }
    }

    /**
     * Tapping the date opens the sheet.
     *
     * The rows come off [dayIndex] and are on screen in the frame the tap was
     * in. The sheet used to build them from a provider probe per day before it
     * could show anything at all, which meant seven round trips of the previous
     * open's rows sitting there — for days the index already knew about.
     *
     * The probes still happen, because the walk behind the index is always a
     * little behind the vault: they run strictly newest-first, so today is
     * confirmed after one round trip rather than after all seven, and each row
     * settles the moment its own answer lands.
     */
    fun openDays() {
        val repository = repo ?: return
        if (state.captureInFlight) return
        val today = LocalDate.now()
        val known = dayIndex
        state = state.copy(
            daysOpen = true,
            linkOpen = false,
            days = (0 until DAY_SHEET_SPAN).map { back ->
                dayRow(today.minusDays(back.toLong()), known?.contains(today.minusDays(back.toLong())))
            },
        )

        dayProbeJob?.cancel()
        dayProbeJob = viewModelScope.launch {
            for (back in 0 until DAY_SHEET_SPAN) {
                val date = today.minusDays(back.toLong())
                val exists = withContext(Dispatchers.IO) { repository.exists(activeConfig, date) }
                if (state.daysOpen) {
                    state = state.copy(
                        days = state.days.map { if (it.date == date) dayRow(date, exists) else it },
                    )
                }
                // The sheet asked the provider itself, which beats the walk behind
                // the day index for exactly the days most likely to have moved.
                // Worth finishing even if the sheet has gone: it is what the
                // chevrons on whatever day was picked are read off.
                recordDays(mapOf(date to exists))
            }
        }
    }

    /** One sheet row. [exists] is null while nobody can say yet. */
    private fun dayRow(date: LocalDate, exists: Boolean?): DayRow = DayRow(
        date = date,
        label = DailyNote.label(activeConfig, date),
        sub = buildString {
            if (date == LocalDate.now()) append("today · ")
            append(activeConfig.folder.ifEmpty { "vault root" })
            when (exists) {
                true -> append(" · exists")
                false -> {
                    append(" · not created yet")
                    if (activeConfig.template.isNotBlank()) append(" · will render template")
                }
                null -> append(" · checking…")
            }
        },
        isCurrent = date == openDate,
        exists = exists,
    )

    fun closeDays() {
        state = state.copy(daysOpen = false)
    }

    // ── Search ───────────────────────────────────────────────────────────────

    fun openSearch() {
        state = state.copy(
            searchOpen = true,
            linkOpen = false,
            daysOpen = false,
            deleteOpen = false,
            reindexOpen = false,
            openedNote = null,
        )
        scheduleSearch()
    }

    fun closeSearch() {
        searchJob?.cancel()
        scanJob?.cancel()
        scannedQuery = null
        state = state.copy(searchOpen = false, openedNote = null, searchScanning = false)
    }

    /**
     * A keystroke sets the query and starts a job. That is all it does.
     *
     * It used to scan every note's cached preview inline, inside the text field's
     * `onValueChange` — so the cost landed between the keypress and the frame, the
     * IME's callback blocked, and characters queued up behind it. The rarer the
     * query the worse it got, because the result limit only short-circuits on a
     * match.
     */
    fun onSearchQuery(query: String) {
        // A scan is reading the vault for the previous query; it is answering a
        // question nobody is asking any more.
        scanJob?.cancel()
        scannedQuery = null
        state = state.copy(searchQuery = query, openedNote = null, searchScanning = false)
        scheduleSearch()
    }

    fun clearSearch() = onSearchQuery("")

    /** The live half: names only, which is all the index holds. */
    private fun scheduleSearch() {
        if (!state.searchOpen) return
        val query = state.searchQuery.trim()
        // Full text for this exact query is on screen or on its way. Only a
        // keystroke goes back to names, and that clears both of these first.
        if (state.searchScanning || scannedQuery == query) return
        searchJob?.cancel()
        if (query.isEmpty()) {
            state = state.copy(searchResults = emptyList(), searchStats = idleStats())
            return
        }
        val snapshot = index
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            // Timed inside the hop, not around it: what the line reports is the
            // cost of the match, and getting onto a worker is not that.
            val (hits, ms) = withContext(Dispatchers.Default) {
                val startedAt = System.nanoTime()
                val found = snapshot.nameHits(query, SEARCH_LIMIT)
                found to ((System.nanoTime() - startedAt) / 1_000_000.0).coerceAtLeast(1.0).toInt()
            }
            // Cancellation is not instant, so a result that arrives for a query
            // the field has already moved past is dropped rather than shown.
            if (state.searchQuery.trim() != query) return@launch
            state = state.copy(
                searchResults = hits.matches.map { SearchHit(it, null) },
                searchStats = "${hits.total} of ${snapshot.size} notes · ${ms}ms · Enter searches every note",
            )
        }
    }

    /**
     * The Search key: read the vault and look inside every note.
     *
     * Nothing caches note content, so this is what full text costs — and it is
     * charged to the search rather than to every launch, which is the right way
     * round for a daily-note app. It also cannot be stale.
     */
    fun onSearchSubmit() {
        val repository = repo ?: return
        val query = state.searchQuery.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        scanJob?.cancel()
        val snapshot = index
        if (snapshot.size == 0) return
        state = state.copy(searchScanning = true, searchStats = "reading 0 of ${snapshot.size} notes…")

        scanJob = viewModelScope.launch {
            val search = VaultSearch(repository.files)
            val startedAt = System.currentTimeMillis()
            val result = withContext(Dispatchers.Default) {
                search.run(snapshot, query) { scanned, hits ->
                    withContext(Dispatchers.Main) {
                        state = state.copy(
                            searchResults = hits,
                            searchStats = "reading $scanned of ${snapshot.size} notes · ${hits.size} found",
                        )
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - startedAt
            scannedQuery = query
            state = state.copy(
                searchScanning = false,
                searchResults = result.hits,
                searchStats = scanStats(result, snapshot.size, elapsed),
            )
        }
    }

    private fun scanStats(result: VaultSearch.Result, total: Int, elapsed: Long): String {
        val seconds = String.format(Locale.ENGLISH, "%.1f", elapsed / 1000.0)
        // A capped scan stopped early, so it never learned the real total — say
        // "40+" rather than let a number it did not count read as the answer.
        val found = if (result.capped) "${result.hits.size}+" else "${result.hits.size}"
        return "$found in ${result.scanned} of $total notes · ${seconds}s"
    }

    private fun idleStats(): String =
        if (state.indexing) "Indexing vault…" else "Type to filter names · Enter searches inside every note"

    fun openResult(entry: IndexEntry) {
        state = state.copy(openedNote = entry)
    }

    fun backToResults() {
        state = state.copy(openedNote = null)
    }

    /** The index holds no note text, so opening a result reads that one file. */
    fun readFull(entry: IndexEntry, onLoaded: (String) -> Unit) {
        val repository = repo ?: return
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { repository.files.readText(entry.path) }
            onLoaded(text ?: "Could not read ${entry.path}.")
        }
    }

    // ── Chrome ───────────────────────────────────────────────────────────────

    fun dismissNotice() {
        state = state.copy(notice = null, noticeApp = null)
    }

    /** Overflow: hand the vault back to the picker. */
    fun changeVault() {
        saveNow()
        indexJob?.cancel()
        searchJob?.cancel()
        scanJob?.cancel()
        linkJob?.cancel()
        openJob?.cancel()
        dayProbeJob?.cancel()
        // A capture still working lands against a vault that has gone, and
        // drops rather than writing into whichever vault is picked next.
        openGeneration++
        rollOverPending = false
        scannedQuery = null
        store.clear()
        WikiIndex.clear(getApplication<Application>())
        repo = null
        note = null
        phase2Started = false
        index = WikiIndex.EMPTY
        templateCache = null
        linterConfig = LinterConfig()
        body.clearText()
        savedBody = null
        openDate = LocalDate.now()
        anchoredTo = openDate
        dayIndex = null
        dayEdits.clear()
        state = UiState(screen = Screen.FirstRun)
    }

    /**
     * Overflow: re-read the config and the template, and nothing else.
     *
     * Deliberately does not walk the vault — that is Reindex vault's whole job,
     * and having both menu items pay for the walk meant the slow half of Phase 2
     * ran whichever one you picked.
     */
    fun reloadConfig() {
        val repository = repo ?: return
        if (configJob?.isActive == true) return
        configJob = viewModelScope.launch(Dispatchers.IO) {
            val fresh = readConfigAndTemplate(repository)
            val config = fresh ?: activeConfig
            val notice = when {
                fresh == null -> "daily-notes.json unchanged — ${describe(config)}."
                fresh.locationDiffers(activeConfig) ->
                    "daily-notes.json changed. New format applies from tomorrow — this note is untouched."
                else -> "daily-notes.json reloaded — ${describe(config)}."
            }
            withContext(Dispatchers.Main) {
                state = state.copy(config = config, configMissing = !config.present, notice = notice)
            }
        }
    }

    /** What the verify screen says about deleting, in Obsidian's own terms. */
    private fun trashLine(): String = when (appConfig.trash) {
        TrashOption.System -> "system trash → .trash/"
        TrashOption.Local -> ".trash/"
        TrashOption.None -> "delete permanently"
    }

    /** Where an attachment goes, in the shapes Obsidian's own setting offers. */
    private fun attachmentLine(): String {
        val setting = appConfig.attachmentFolder
        // Resolved against a note at the root, which leaves just the setting's own folders.
        val folder = Attachments.folderFor(setting, NotePath(emptyList(), "")).joinToString("/")
        return when {
            setting == "." || setting == "./" -> "the note's folder"
            setting.startsWith("./") -> "$folder/ in the note's folder"
            folder.isEmpty() -> "vault root"
            else -> "$folder/"
        }
    }

    /** What the verify screen says about the plugin, in the plugin's own terms. */
    private fun linterLine(): String = when {
        !linterConfig.present -> "not installed"
        !linterConfig.enabled -> "yaml-timestamp off"
        else -> buildList {
            if (linterConfig.stampCreated) add(linterConfig.createdKey)
            if (linterConfig.stampModified) add(linterConfig.modifiedKey)
        }.ifEmpty { listOf("no timestamps") }.joinToString(" · ")
    }

    private fun describe(config: VaultConfig): String =
        "format ${config.format}, ${config.template.ifBlank { "no template" }}"

    /** Overflow: ask before paying for a full walk — see [confirmReindex]. */
    fun askReindex() {
        state = state.copy(reindexOpen = true, daysOpen = false, linkOpen = false)
    }

    fun cancelReindex() {
        state = state.copy(reindexOpen = false)
    }

    /**
     * Overflow: walk the vault again, on demand.
     *
     * The index only refreshes at launch, so a note written in Obsidian since
     * then is invisible to search and to `[[` until this runs. Search and the
     * link strip keep serving the index they have until the new one lands, so a
     * reindex never empties them out mid-walk.
     */
    fun confirmReindex() {
        state = state.copy(reindexOpen = false)
        if (walking) {
            state = state.copy(notice = WALKING)
            return
        }
        refreshIndex(announce = true)
    }

    /**
     * Walks the vault against the index already in memory, so only the notes
     * whose stamps moved are opened.
     *
     * [announce] is what separates the overflow item from the automatic pass on
     * resume: a refresh the user asked for should say what it found, one they
     * did not ask for should not interrupt them to say nothing changed.
     */
    private fun refreshIndex(announce: Boolean) {
        val repository = repo ?: return
        state = state.copy(indexing = true, notice = if (announce) null else state.notice)
        indexJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val names = repository.buildNameIndex()
            ensureActive() // the vault may have been changed out from under the walk
            val elapsed = System.currentTimeMillis() - startedAt
            publishNames(
                names,
                notice = if (announce) "Reindexed ${names.size} notes in ${elapsed}ms." else null,
            )
        }
    }

    // ── Coming back ──────────────────────────────────────────────────────────

    private var backgroundedAt = 0L

    fun onBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * A vault that has been out of sight for a while has usually moved on —
     * Obsidian on the desktop, a sync client, another editor on the phone. The
     * walk is incremental, so an untouched vault costs one cursor per directory
     * and not a single file read.
     */
    fun onResumed() {
        val since = backgroundedAt
        backgroundedAt = 0L
        rollOver()
        if (since == 0L || !phase2Started || walking) return
        if (System.currentTimeMillis() - since < RESUME_REINDEX_AFTER_MS) return
        refreshIndex(announce = false)
    }

    /**
     * The clock does not stop while the app is in the background. Someone who
     * left the app on today's note and comes back after midnight should land on
     * the new today, not go on typing into yesterday behind a stale label — and
     * with the old offset arithmetic the forward chevron stayed disabled, so
     * today was unreachable without a restart.
     *
     * Anyone parked on an older note is left where they put themselves. Their
     * forward edge was already open and every label is derived from the note's
     * own date, so nothing on screen goes stale.
     */
    private fun rollOver() {
        if (repo == null || state.screen != Screen.Editor) return
        // A capture still belongs to the note it started in; see [pin].
        if (state.captureInFlight) {
            rollOverPending = true
            return
        }
        val today = LocalDate.now()
        if (today == anchoredTo) return
        val wasOnLatest = openDate == anchoredTo
        anchoredTo = today
        if (!wasOnLatest) return
        saveNow()
        openDay(today, create = true, showCreatedNotice = true)
    }

    private companion object {
        const val WALKING = "Already walking the vault — this one will finish on its own."
        const val NO_VOICE = "Nothing on this phone takes voice input. Scrib and FUTO Voice Input " +
            "are both free on F-Droid. A keyboard's own mic types into the note as well."

        /** Long enough that pausing to read a link or answer a message is not a rescan. */
        const val RESUME_REINDEX_AFTER_MS = 3 * 60 * 1000L
        const val STEP_MS = 230L
        const val SAVE_DEBOUNCE_MS = 600L

        /**
         * Long enough that a burst of typing starts one scan rather than one per
         * character, short enough that the results feel attached to the caret.
         */
        const val SEARCH_DEBOUNCE_MS = 120L
        const val LINK_DEBOUNCE_MS = 80L

        const val LINK_LIMIT = 4
        const val SEARCH_LIMIT = 40
        const val DAY_SHEET_SPAN = 7

        /**
         * How far back the day index looks for notes. Ten years, because the scan
         * is one cached-formatter call and one hash lookup per day — a few
         * milliseconds for the whole span, on the pass that builds the index anyway.
         */
        const val DAY_SCAN_SPAN = 3650
    }
}
