# VaultDrop

An Android capture surface for the daily note in an existing Obsidian vault.
It reads your core Daily Notes settings, opens today's `.md`, and puts the
caret at the end. It does not sync, render markdown, or manage the graph.

Implements the design canvas `VaultDrop.dc.html` — board **1a** (the working
prototype), **1b** (light), **1c** (the edge states), and **2a/2b** (every
colour bound to a Material You role).

## Build

```
./gradlew :app:installDebug     # or open the project in Android Studio
./gradlew :app:testDebugUnitTest
```

Requires JDK 17 and the Android SDK (compileSdk 35, minSdk 26). The Gradle
wrapper is checked in, so `./gradlew` works on a fresh clone. `local.properties`
is not tracked — point `sdk.dir` at your SDK, set `ANDROID_HOME`, or let Android
Studio write it for you on first open.

## How it is put together

The brief spends nearly all its length on the critical path, so the code is
organised around the two phases rather than around screens.

**Phase 1** runs on the main thread, before the first frame, in
`MainActivity.onCreate` → `EditorViewModel.phase1()`:

1. Read the cached config out of `SharedPreferences` (`ConfigStore`).
2. Resolve today's path from the cached `format` and `folder` (`DailyNote`).
3. One `openInputStream`. That is the only filesystem operation on the path.

Nothing else is allowed here: no config re-read, no scan, no mtime check, no
network. `VaultFiles` is built on document-id arithmetic rather than
`DocumentFile`, because `DocumentFile.findFile()` lists an entire directory to
resolve a single name — a tree grant lets us mint any descendant's document id
directly, which is what keeps step 3 to a single call.

**Phase 2** starts only once the editor is on screen, on `Dispatchers.IO`
(`EditorViewModel.startPhase2`):

1. Probe `.obsidian/daily-notes.json`'s mtime; hash only if the stamp moved.
   A changed config is cached and announced, but `activeConfig` — what the open
   session resolves paths with — is deliberately left alone, so the note on
   screen is never reloaded, renamed or closed. The new settings take effect on
   the next launch.
2. Read the template into memory, ready for tomorrow's note.
3. Walk the vault for `.md` files, building the `basename → path` map. One
   cursor per directory; **not a single file is opened.**

The index is cached to `filesDir/wiki-index.tsv` and served immediately on the
next launch, so the `[[` strip only shows "Indexing vault…" on a cold start.

### Search

The index holds names and paths and no note content, which is what keeps the
walk free of file reads. So search has two speeds. Typing filters names off the
index — a single pass over pre-folded strings, debounced and on
`Dispatchers.Default`. The Search key hands the query to `VaultSearch`, which
reads the vault a note at a time (12 concurrent, capped at 64 KB each) and
streams hits back as it finds them, cancelling the moment you type again.

Full text therefore costs a read of the vault per search rather than per launch,
which is the right way round for an app whose job is opening today's note fast —
and it cannot go stale, because it looks at what is on disk when you ask.

Neither path touches the main thread. Both used to: the scans ran inline inside
`onValueChange`, so the cost landed between the keypress and the frame and the
keyboard queued up behind it.

### File safety

`VaultRepository.openNote` reads first and creates only on a miss, so an
existing note is loaded byte-for-byte and the template is written exactly once,
at creation. `Frontmatter.split`/`join` are exact inverses (there is a test for
it), so parking the frontmatter in a chip cannot alter the file. Phase 2 never
writes. Edits autosave on a 600 ms debounce and on `onPause`, with a truncating
`"wt"` write so a shortened body cannot leave a tail behind.

### Notes on two details

- **`text/markdown` on create.** Passing `text/plain` makes
  `ExternalStorageProvider` rewrite the extension to `.txt`, and Obsidian would
  stop seeing the note.
- **The date format is pinned to `Locale.ENGLISH`**, matching Moment's default
  on the desktop, so filenames do not move when the phone's locale does.

## Type

Chrome is Roboto; anything that is a path, a date or file content is monospace.
The design specifies IBM Plex Mono — drop the OFL `.ttf` files into
`app/src/main/res/font/` and point `ui/theme/Type.kt`'s `Mono` at them. Until
then it resolves to the platform monospace face.

## Colour

Nothing takes a literal colour. On Android 12+ the scheme is
`dynamicDark/LightColorScheme` from the wallpaper; `ui/theme/Color.kt` holds the
ochre fallback for older devices, which is the fallback column of board 2b.

## License

Copyright (C) 2026 Chipperdoo.

VaultDrop is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE) for the full text.
