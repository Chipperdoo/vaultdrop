# VaultDrop

**An Android capture surface for your Obsidian daily note.**

Tap the icon and the caret is already blinking at the end of today's note. That
is the whole product. VaultDrop reads your vault's own Daily Notes settings, so
the file lands exactly where Obsidian would have put it, named exactly what
Obsidian would have named it.

It is not an Obsidian client. It does not sync, render markdown, or manage your
graph — Obsidian already does those well. This is for the thirty seconds when
you just need to write something down.

## What it does

- **Opens today's note before the first frame.** One read from cache, one file
  open, no scan.
- **Creates the note from your template** if today's doesn't exist yet, with
  `{{date}}` and `{{time}}` resolved. Existing notes are never rewritten.
- **Autosaves** on a 600 ms pause and when you leave the app.
- **`[[` autocomplete** against every note name in the vault.
- **Search** — filter names as you type, or press Search to read inside every
  note in the vault.
- **Move between days** by swiping the note, tapping the chevrons, or picking
  from the last week in the day sheet.
- **Deletes the way your vault asks it to**, honouring Obsidian's own
  `trashOption` setting.
- **Stamps `date created` / `date modified`** if you use the Linter plugin,
  reading that plugin's own settings.
- **Takes dictation** through the voice apps already on your phone. See
  [Voice input](#voice-input).

Nothing above is a VaultDrop preference. Every setting is one you already made
in Obsidian, so a vault carried to another device behaves the same way there.

## Voice input

VaultDrop does no speech recognition of its own. It works with the voice apps
you already have, in three ways:

- **A voice keyboard.** The note is a plain text field, so a keyboard's mic
  types straight into it: Gboard's, FUTO Keyboard's, Scrib's. Android's Voice
  Access works too. There is nothing to set up.
- **The mic above the keyboard** fires Android's own voice input
  (`RECOGNIZE_SPEECH`). The app your phone uses for it shows its window and
  listens, and the text goes in at the caret. Google, FUTO Voice Input,
  Transcribro and Kõnele all answer it. If several do, Android asks which one
  and remembers. VaultDrop needs no microphone permission for this.
- **Scrib** doesn't answer that intent. It speaks its own
  [Open Transcribe](https://github.com/23rd/Scrib/blob/main/CONTRACT.md)
  contract instead, and the same mic speaks it too: VaultDrop records, Scrib
  transcribes on the phone, and the text shows in a strip above the keyboard as
  you talk. Press Stop and it goes in at the caret. This is the one path that
  needs the microphone permission, and VaultDrop asks for it the first time you
  use it. Leaving the app stops the recording, and the text still lands when
  Scrib is done. The audio is not kept.

If an Open Transcribe app is installed, the first tap asks which app to use,
and remembers. Voice input… in the menu changes it. VaultDrop also remembers the
certificate the app was signed with, and asks again if that ever changes,
because the app it picked is the one hearing your voice. With no voice app
installed, the mic says so and names two free ones on F-Droid.

## Requirements

JDK 17 and the Android SDK. The app builds against `compileSdk 37`, targets
API 37 (Android 17), and runs on Android 8.0 (`minSdk 26`) and up.

## Build

```bash
./gradlew :app:installDebug        # build and install on a connected device
./gradlew :app:testDebugUnitTest   # run the unit tests
```

Or open the project in Android Studio, which will handle the SDK setup for you.

The Gradle wrapper is checked in, so `./gradlew` works on a fresh clone.
`local.properties` is not tracked — point `sdk.dir` at your SDK, set
`ANDROID_HOME`, or let Android Studio write it on first open.

## How it works

The app is organised around two phases rather than around screens, because
almost all of the design effort went into the critical path.

### Phase 1 — get the note on screen

Runs on the main thread, before the first frame
(`MainActivity.onCreate` → `EditorViewModel.phase1`):

1. Read the cached config out of `SharedPreferences`.
2. Resolve today's path from the cached `format` and `folder`.
3. One `openInputStream`.

That third step is the only filesystem operation on the critical path. Nothing
else is permitted here — no config re-read, no vault scan, no mtime check, no
network.

If today's note isn't there yet, creating it costs several more round trips out
to the provider, and those happen *behind* the first frame: the date and a live
caret are on screen immediately, and the note fills in a moment later. Anything
typed in the meantime is kept, below the template.

`VaultFiles` is built on document-id arithmetic rather than `DocumentFile`. This
matters: `DocumentFile.findFile()` lists an entire directory to resolve a single
name, which is what makes the Storage Access Framework feel slow. A tree grant
lets us mint any descendant's document id directly, and that is what keeps step 3
to a single call.

### Phase 2 — everything else

Starts only once the editor is on screen and the caret is live, on
`Dispatchers.IO` (`EditorViewModel.startPhase2`):

1. **Check the config.** Probe `.obsidian/daily-notes.json`'s mtime, and hash it
   only if the stamp moved.
2. **Read the template** into memory, ready for tomorrow's note.
3. **Walk the vault** for `.md` files, building a `name → path` map. One cursor
   per directory, and not a single file is opened.

The index is cached to `filesDir/wiki-index.tsv` and served immediately on the
next launch, so "Indexing vault…" only appears on a cold start.

A changed config is cached and announced, but it deliberately does not affect the
note already on screen — that note is never reloaded, renamed or closed
underneath you. New settings take effect on the next launch. Two other settings
files behave the opposite way and are picked up live: the Linter's rules and the
vault's `trashOption`. Neither decides *where* a note lives, only what gets
stamped into the next save or what happens on the next delete, so applying them
immediately is what you'd expect.

### Search has two speeds

The index holds names and paths and no note content, which is what keeps the walk
free of file reads. So:

- **Typing** filters names off the index — one pass over pre-folded strings,
  debounced, off the main thread.
- **Pressing Search** hands the query to `VaultSearch`, which reads the vault a
  note at a time (12 concurrent, capped at 64 KB each) and streams hits back as
  it finds them, cancelling the moment you type again.

Full text therefore costs one read of the vault per search rather than per
launch, which is the right way round for an app whose job is opening today's note
fast. It also cannot go stale: it searches what is on disk at the moment you ask.

### File safety

Your notes are the thing worth protecting, so:

- `VaultRepository.openNote` reads first and creates only on a miss. An existing
  note is loaded byte-for-byte and the template is written exactly once, at
  creation.
- `Frontmatter.split` and `join` are exact inverses (there is a test for it), so
  parking the frontmatter in a chip cannot alter the file. Line endings are never
  normalised — a CRLF note stays CRLF.
- Phase 2 never writes.
- Saves use a truncating `"wt"` write, so shortening the body cannot leave a tail
  of the old one behind.
- A save that would write identical bytes is skipped, so opening and closing a
  note doesn't move its `date modified`.

### Two details worth knowing

- **Notes are created as `text/markdown`.** Passing `text/plain` makes
  `ExternalStorageProvider` rewrite the extension to `.txt`, and Obsidian would
  stop seeing the note.
- **Date formats are pinned to `Locale.ENGLISH`**, matching Moment's default on
  the desktop, so filenames don't move when the phone's locale does.

## Design

The UI is transcribed from a design canvas (`VaultDrop.dc.html`, not part of this
repo) covering the working prototype, its light variant, the edge states, and
every colour bound to a Material You role.

**Type.** Chrome is Roboto; anything that is a path, a date or file content is
monospace. The design calls for IBM Plex Mono — drop the OFL `.ttf` files into
`app/src/main/res/font/` and point `Mono` in `ui/theme/Type.kt` at them. Until
then it resolves to the platform monospace face.

**Colour.** Nothing takes a literal colour. On Android 12+ the scheme comes from
the wallpaper via `dynamicDark/LightColorScheme`; `ui/theme/Color.kt` holds an
ochre fallback for older devices.

## License

Copyright (C) 2026 Chipperdoo.

VaultDrop is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE) for the full text.
