# AGENTS.md

Guidance for coding agents working in this repository. Human-facing docs are in
[README.md](README.md).

## Commands

```bash
./gradlew :app:assembleDebug                 # build
./gradlew :app:installDebug                  # build and install on a connected device
./gradlew :app:installPreview                # same, as dev.vaultdrop.preview, beside the real app
./gradlew :app:testDebugUnitTest             # all unit tests
./gradlew :app:lintDebug                     # Android Lint
./gradlew :app:assembleRelease               # signed release APK, if signing is set up
```

Run a single test class or method:

```bash
./gradlew :app:testDebugUnitTest --tests 'dev.vaultdrop.core.FrontmatterTest'
./gradlew :app:testDebugUnitTest --tests 'dev.vaultdrop.core.MomentFormatTest.*literal*'
```

There are no instrumented (`androidTest`) tests. Everything under `app/src/test`
is a plain JVM test — `unitTests.isReturnDefaultValues = true` in
`app/build.gradle.kts` is what lets `org.json` classes resolve without a device.

CI (`.github/workflows/ci.yml`) runs the unit tests, lint and `assembleDebug`
on every push to `main` and every pull request. It also builds the preview APK
and uploads it, unzipped, as the run's artifact
(`vaultdrop-<versionName>-preview-<sha>.apk`), so a change can be installed on a
phone straight from the PR. CI signs it with a fresh debug key every run, so a
newer one won't install over an older one: uninstall the preview first.

## Secrets and personal data

The repo is public. Nothing secret is tracked, and `.gitignore` keeps keystores,
`local.properties` and `.claude/` out.

Release signing reads `vaultdropStoreFile`, `vaultdropStorePassword` and
`vaultdropKeyAlias` as Gradle properties: from `~/.gradle/gradle.properties` on
the maintainer's machine, or from `ORG_GRADLE_PROJECT_*` variables fed by GitHub
Actions secrets. Never put them in a tracked file. Without them
`assembleRelease` still builds, unsigned. Every release must be signed with the
same key, or it won't install over the one before it.

Test data and examples are made up. Don't copy folder names, file names, dates
or note counts out of anyone's real vault into tests, docs or commit messages.

## Architecture

Single activity, single ViewModel, unidirectional state. `MainActivity` holds an
`EditorViewModel`, renders one of four things off `UiState.screen` plus two
overlay booleans, and passes method references down. Composables never touch
`core/` — they receive data and emit callbacks.

```
MainActivity ──> EditorViewModel ──> VaultRepository ──> VaultFiles ──> SAF
                      │                                                (DocumentsContract)
                      ├──> ConfigStore (SharedPreferences)
                      └──> voice/ ──> Open Transcribe engine (binder), AudioRecord
```

`ui/` is presentation only. `core/` is where the logic lives, and almost all of
it is pure and unit-tested; `VaultFiles` is the single point where Android
storage APIs are touched.

### The two-phase launch is the central constraint

Read `EditorViewModel.phase1` and `startPhase2` before changing anything on the
launch path.

**Phase 1** runs on the main thread before the first frame: one
`SharedPreferences` read, then at most one `openInputStream`. Adding *any*
filesystem work here — a config re-read, an mtime probe, a directory listing —
defeats the entire design of the app.

*At most* one, and only ever a read. When today's note is already there that read
is the whole of it, and nothing asks about the URI permission: a note that just
opened plainly still has its grant, and asking costs a binder round trip.

When it is **not** there — the first launch of a new day, which is the case this
app exists for — creating it is a listing per directory in its path, a
`createDocument` and a write. That does not happen on the main thread. The grant
check moves in front of it (one cheap binder call, so the editor cannot flash up
for a vault whose permission is gone) and then `createDay` puts the day's label
and a live caret on screen immediately and fills the note in behind. Anything
typed into that gap is merged *after* the template — see `adopt(fromCreate)`.

The same split governs navigation: `openDay` reads on the main thread, because
one round trip lands inside the frame that asked for it and there is nothing
honest to show in the meantime, and hands any miss to `createDay`.

**Phase 2** runs on `Dispatchers.IO`, started by a `LaunchedEffect` only once the
editor is composed. It may read anything but must never write.

### Config freshness is deliberately asymmetric

Three settings files are read from the vault, all through the same
mtime-then-hash probe (`VaultRepository.configChangedSince` and its siblings):

| File | Adopted |
|---|---|
| `.obsidian/daily-notes.json` | **Next launch only** |
| `.obsidian/plugins/obsidian-linter/data.json` | Immediately |
| `.obsidian/app.json` | Immediately |

`activeConfig` — the config the open session resolves paths with — is
deliberately *not* updated when Phase 2 finds a newer `daily-notes.json`, because
changing it would rename or reload the note under the user's caret. The other two
only affect the next save's timestamps and the next delete's destination, so they
are adopted live. Do not "fix" this inconsistency; it is the point.

### The index holds no note content

`WikiIndex` is `name → path` and nothing else, which is why the vault walk
(`VaultRepository.buildNameIndex`) opens zero files — it reads two columns per
directory cursor. Full-text search is `VaultSearch`, on demand, reading at most
64 KB per note with 12 concurrent permits.

If you are tempted to cache note previews in the index: that existed, and it cost
a file open per note on every walk. Don't reintroduce it.

The index is cached at `filesDir/wiki-index.tsv`. The loader tolerates extra
columns from older cache versions.

The launch pass does not always walk. A walk is one cursor per directory into the
same provider the editor has just read today's note out of, so a cache younger
than `RESUME_REINDEX_AFTER_MS` is served as-is and the walk skipped — the same
staleness rule the resume pass uses, and the reason Reindex vault exists.

`WikiIndex.paths` is a `Set<String>`, not the `path -> entry` map it used to be:
`DayIndex.build` is the only caller and it only ever asks whether a path is
there.

### Day navigation goes through DayIndex, not date arithmetic

`DayIndex` is the sorted set of days that actually have notes. Chevrons, swipe
and the rubber-band all read `before()`/`after()`, so a lit control can never
offer a step that wouldn't happen. **Stepping never creates a note** — only the
day sheet does.

`DayIndex.build` derives dates *forwards* through `DailyNote.resolve`, the same
call that writes notes, so the two cannot disagree. Parsing filenames back into
dates is not available: `MomentFormat` emits Moment ordinals (`Do`, `Mo`) as
literals, and a format with no year has no inverse at all.

`dayEdits` in the ViewModel is a main-thread overlay of what this session knows
first-hand (a note it just created, a day a step found missing) and is re-applied
on every rebuild, so a walk that predates those facts can't undo them.

The day sheet draws its rows from `DayIndex` and is therefore on screen in the
frame the tap was in. It still probes the provider per day, because the walk
behind the index is always a little behind the vault — but strictly newest-first
and one at a time, so today is confirmed after one round trip rather than after
all seven, and each row settles as its own answer lands. `DayRow.exists` is
nullable: null is a day nobody can answer for yet, which is what a cold start
sees before any walk has landed.

Resolving a span of days goes through `DailyLayout`, not `DailyNote.resolve` in a
loop — the config's own share of the work is hoisted out. `DailyLayout.resolve`
is derived from `DailyLayout.relativePath` so the two cannot disagree, and
`DailyNoteTest` pins them together across every format and folder shape.

### Captures hand off to apps already on the phone

The row above the keyboard (mic, camera, gallery) and Android's share sheet are
the ways in. VaultDrop records, recognises and photographs nothing itself: it
hands off to the phone's own apps and copies what comes back into the vault.
`docs/plans/multimedia.md` has the reasoning.

- **Every capture pins the note.** `pin()` before leaving the app for a camera,
  picker or recogniser, `isStillOpen(pin)` before splicing, and `unpin()` in a
  `finally`, exactly once. Pins count. While one is held, day navigation, the
  day sheet, delete and `rollOver` wait, and shares queue until the last unpin.
- **`attach` re-reads `app.json` every time**, because resuming probes nothing.
  Folder, link style and link format come out of `Attachments`, from Obsidian's
  own keys.
- **An attachment's MIME comes from `VaultFiles.mimeFor(ext)`.** The provider
  renames a file whose MIME and extension disagree, and `isAt` deletes the file
  rather than embed a name that is not on disk.
- **Shares are read at launch from memory only.** `onCreate` takes the text out
  of the intent's extras after `phase1()`, so it is in the first frame. Asking
  the sender for a name, a type or a stream waits for `attachSharedFiles`,
  which runs from the same effect as `startPhase2`. Only `content:` URIs are
  opened: a `file:` URI would read with VaultDrop's own permissions. The
  `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` check is what keeps Recents from
  appending the same share twice.
- **The camera is the phone's own app**, through `ACTION_IMAGE_CAPTURE` into
  `cache/capture/`. The manifest declares no `CAMERA` permission: declaring it
  without holding it makes Android refuse that intent.
- **Build authorities from the application ID** (`${applicationId}.fileprovider`,
  `context.packageName`). The preview build installs beside the real one under
  another ID.
- **`PhotoFiles` is the one storage class outside `VaultFiles`**, for the
  camera's cache file and the picker's URIs. It never touches the vault. The view
  model builds it before Phase 1, so its constructor reads nothing.
- **`voice/` is the only place that touches binder and audio APIs.** It binds
  only the Open Transcribe app the user picked, pinned to its signing-cert
  SHA-256. `findVoiceOptions` runs on a tap, on IO. Dictation audio is never
  kept. The `org/opentranscribe/api` AIDL files are Scrib's, copied unchanged,
  because their package is the binder descriptor.

## Invariants that protect user data

These are load-bearing. Tests exist for most of them.

- **`Frontmatter.split`/`join` are exact inverses.** Line endings are never
  normalised, and the blank-line gap after the fence belongs to the frontmatter.
  A note must never be rewritten merely for having been opened.
- **Read before create.** `VaultRepository.openNote` only writes on a miss.
  `readNote` is the read-only half used by navigation.
- **Writes use `"wt"`**, not `"w"` — plain `"w"` leaves the tail of a longer
  previous body.
- **Notes are created with MIME `text/markdown`.** `text/plain` makes
  `ExternalStorageProvider` rewrite the extension to `.txt`.
- **Identical-bytes saves are skipped**, because a write moves `date modified`.
- **Trashing is copy-then-delete**, never `DocumentsContract.moveDocument`
  (providers behind synced folders don't reliably offer `FLAG_SUPPORTS_MOVE`).
  A partial failure leaves the note where it was.

## Threading rules

The main thread sets state and starts coroutines. It never scans.

Every text field is backed by a debounced, cancel-on-next-keystroke job
(`searchJob`, `scanJob`, `linkJob`). All of these once ran inline in
`onValueChange`, which put a full vault scan between the keypress and the frame.
Results are dropped if the query moved on while the job was away — check the
guards before `state = state.copy(...)` in `scheduleSearch` and
`scheduleLinkMatches`.

The note body is **not** in `UiState`. It is a `TextFieldState` the view model
owns and the field mutates in place, because a keystroke that copies `UiState`
invalidates every composable reading it — the top bar, the chip, the notice —
for a change only the field cares about. Two `snapshotFlow` collectors in `init`
hang off it: one drives the `[[` strip and writes state *only* when the strip's
own state changes, the other is the autosave debounce (`collectLatest`, which is
where `saveJob` went). Anything that must stop a pending save sets `note = null`
rather than cancelling a job; `saveNow` stops there.

The debounce is two seconds, not a few hundred milliseconds, because every write
is a new version for the user's sync client to carry, and a short one wrote at
every pause mid-sentence. Writes to the open note go through `noteWriter`, one
at a time and in order, so two can't land oldest-last. The top bar's saved dot
reads `saved`, which is its own `mutableStateOf` for the same reason the body
is: it flips on the first keystroke after every save. `refreshSaved` derives it
from `note`, `savedBody` and the writes still out; call it after changing any of
them outside the autosave collector.

`index` is `@Volatile`: written by the walk on IO, read by search and link jobs
off it. Jobs snapshot it into a local before suspending.

Open Transcribe callbacks arrive on binder threads, and `EngineLink` posts them
to main. Calls into the engine block, so they run on the link's worker thread.
The mic has a capture thread and a sender thread with a queue between them,
because `write` blocks while the engine catches up.

## Gotchas

- **`TemplateEngine.VARIABLE` escapes both closing braces.** Android's regex is
  ICU-backed and rejects a bare `}` that the JVM tolerates — so an unescaped one
  passes unit tests and then throws `ExceptionInInitializerError` on device.
- **`MomentFormat` pins every formatter to `Locale.ENGLISH`** so filenames don't
  move with the phone's locale. Formatters are cached in a `ConcurrentHashMap`;
  building one is expensive relative to formatting a label.
- **`DailyNote.resolve` formats at `atStartOfDay()`**, not from a `LocalDate`. A
  format carrying a time token throws `UnsupportedTemporalTypeException` against
  a bare date, from the launch path, before anything is on screen.
- **`String.foldCase()` (in `WikiIndex.kt`) is char-for-char**, unlike
  `lowercase()`, which changes length for some codepoints and would slide snippet
  offsets onto the wrong text.
- **`ConfigStore.clear()` keeps `voice_engine`.** Which voice app was picked is a
  fact about the phone, so it survives a change of vault.
- **`onStop` stops a dictation, except on rotation.** The text still lands when
  the engine finishes.
- **`TrashOption.System` cannot be honoured.** Android exposes no trash call to a
  folder-grant app, so it falls back to `.trash/` and the UI says so once.
