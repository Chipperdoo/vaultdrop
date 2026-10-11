# Plan: voice and photos

Status: built, all four tracks, and not yet run end to end on a device. What
is still open is under [Checks against desktop Obsidian](#checks-against-desktop-obsidian)
and [Open questions](#open-questions). The build follows the recommendation
for each open question until it is answered.

VaultDrop takes text and nothing else. You can't photograph the thing you
wanted to remember, and you can't talk when typing is awkward, so the app gets
skipped on exactly the days it should be used. This plan adds both.

VaultDrop will not become a recorder, a camera, or a speech engine. It hands off
to whatever the phone already has, and it writes files where the vault's own
Obsidian settings say they go. That's the same rule the rest of the app follows:
every decision about the vault comes from `.obsidian/`, never from a VaultDrop
preference.

## What a vault says

Take a vault whose `.obsidian/app.json` has:

```json
"attachmentFolderPath": "Assets",
"useMarkdownLinks": false,
"newLinkFormat": "shortest",
```

There, a photo belongs in `Assets/` and the note gets `![[name.jpg]]`. The plan
must not hard-code any of that. The setting can change over the years, so a
long-lived vault often has older attachments in other folders. VaultDrop reads
the setting at the moment it writes, and ignores where old files happen to be.

Obsidian has two naming habits worth copying:

- `![[Recording 20260105093000.m4a]]` is Obsidian mobile's own recorder:
  `Recording YYYYMMDDHHmmss.m4a`.
- `![[Screenshot_20260105-093000.png]]` is a file attached under its original
  name, which is what Obsidian does when you attach an existing file.

And one habit to avoid. Some journal apps keep a photo in their own private
storage and write a link to it, like
`![Image](file:///data/user/0/com.example.journal/files/photo.jpg)`. That link
is dead on every other device and will be dead on this one when the app is
uninstalled. **Every file VaultDrop embeds lives inside the vault.**

## Rules for every track

1. **Use what's installed.** VaultDrop does no speech recognition and draws no
   camera UI. It never declares the `CAMERA` permission.
2. **Obsidian decides.** Attachment folder, link style and path form come from
   `app.json`, re-checked right before each write.
3. **Phase 1 is untouched.** No binding, no package queries, no permission
   checks and no extra reads before the first frame.
4. **A capture belongs to the note it started in.** A dictation or photo copy
   that finishes after you've moved to another day would otherwise land in the
   wrong note. See [Pinning the note](#pinning-the-note).
5. **Files are copied byte for byte.** No re-encoding, resizing or EXIF
   stripping.

## How the work splits

```
Track 0: foundation  ──┬──>  Track A: photos  ──>  Track C: share into VaultDrop
(small, lands first)   └──>  Track B: voice
```

Track 0 is small and touches `core/` and the view model. Merge it to `main`
first. After that, A and B share nothing and can each be built in their own
worktree off `main` at the same time. C reuses A's attachment path and B's
Open Transcribe client, so it goes last.

Each track below is written to be handed to one agent on its own.

---

## Track 0: foundation

### Reading the attachment settings

`AppConfig` already parses `.obsidian/app.json` for `trashOption`, caches it in
`SharedPreferences`, and adopts changes live rather than next launch. Attachment
settings belong in the same class for the same reason: they decide where the
*next* file goes and never move anything already on screen. Add three fields:

| `app.json` key | Field | Obsidian's default when absent |
|---|---|---|
| `attachmentFolderPath` | `attachmentFolder: String` | `"/"` (vault root) |
| `useMarkdownLinks` | `markdownLinks: Boolean` | `false` (wikilinks) |
| `newLinkFormat` | `linkFormat: LinkFormat` | `shortest` |

`LinkFormat` is an enum over `shortest`, `relative`, `absolute`, keyed by
Obsidian's strings the way `TrashOption` is. An unknown value falls back to
`shortest`. Extend `toJson`/`fromCache` and `AppConfigTest` for the round trip.

The verify screen gets one more line, e.g. `attachments  Assets/`.

**Freshness.** Phase 2 and Reload config probe `app.json`, but resuming doesn't.
Change the folder in Obsidian, switch back to VaultDrop, add a photo, and the
cached value would be stale. So every attach calls
`VaultRepository.appChangedSince(appConfig)` on IO before resolving the folder.
When the file hasn't moved that costs one metadata query, and it means Obsidian
really is the source of truth at the moment of writing.

### Where a file goes

New pure file `core/Attachments.kt`, unit-tested like `DailyNote`.

`folderFor(setting, note: NotePath): List<String>` follows Obsidian core, as
reimplemented in
[obsidian-dev-utils](https://github.com/mnaoumov/obsidian-dev-utils/blob/main/src/obsidian/attachment-path.ts):

| `attachmentFolderPath` | Settings label | Folder for `journals/2026-09-29-Tue.md` |
|---|---|---|
| `/` or empty | Vault folder | vault root |
| `./` or `.` | Same folder as current file | `journals/` |
| `./img` | In subfolder under current folder | `journals/img/` |
| `Assets` | In the folder specified below | `Assets/` |

The "current file" is the open note's real path, so a `YYYY/MM/DD` format puts
`./` attachments in `2026/09/`. Test all four shapes against a flat note and a
nested one.

`availableName(stem, ext, exists)` follows Obsidian's `getAvailablePath`:
`stem.ext`, then `stem 1.ext`, `stem 2.ext`. This differs from `.trash/`, which
starts at ` 2` and already has its own function. Cap the attempts the way
`freeTrashPath` does.

`embed(target, note, settings): String` writes the link Obsidian would write:

| Settings | Result for `Assets/Photo 20260929174501.jpg` |
|---|---|
| wikilinks, shortest | `![[Photo 20260929174501.jpg]]` |
| wikilinks, absolute | `![[Assets/Photo 20260929174501.jpg]]` |
| wikilinks, relative | `![[../Assets/Photo 20260929174501.jpg]]` |
| markdown, any | `![](Assets/Photo%2020260929174501.jpg)` (path per format) |

Obsidian's [link docs](https://obsidian.md/help/links) say Markdown links must
be URL-encoded, with spaces as `%20`. Match its encoding exactly and confirm
on desktop with a filename containing `#`, `(` and `%`.

**Known gap in `shortest`.** Obsidian writes only the filename when no other
file in the vault shares it, and a longer path when one does. The index only
records notes, so VaultDrop can't see a same-named image elsewhere. Timestamped
names never collide. A picked photo keeps its own name, and could. v1 accepts
that. If it bites, the walk already lists every file and could record
non-note names for free.

### Writing a file

`VaultFiles.create` hard-codes `text/markdown`. Give it a MIME parameter that
defaults to Markdown, so every existing caller is unchanged.

The `.txt` gotcha in `AGENTS.md` applies to every type:
`ExternalStorageProvider` appends an extension when the MIME and the name
disagree. Take the MIME from `MimeTypeMap.getMimeTypeFromExtension`, the same
table the provider consults, rather than a hand-written map. Check each
supported extension on the device and confirm the name on disk is unchanged.

Add `copyInto(uri, source: InputStream): Boolean`, a chunked copy through a
`"wt"` stream.

`VaultRepository.attach(note, stem, ext, source): String?` does the whole job
on IO:

1. probe `app.json`
2. resolve the folder
3. pick a free name
4. create the file (`ensureDirectory` makes a missing folder, as Obsidian does)
5. copy

If the copy fails, delete the partial file, the same cleanup `trash` does.
Return the vault-relative path, or null.

### Inserting into the note

Photos go in as their own line. Dictation goes in as prose. One pure function
covers both, `core/Splice.kt`:

```kotlin
/** Where [insert] goes in [text] at [caret], and where the caret lands after it. */
fun splice(text: CharSequence, caret: Int, insert: String, block: Boolean): Edit
```

A block gets a newline before it when the caret isn't at the start of a line,
and one after it when the next character isn't one. Prose gets a space before
it when the previous character isn't whitespace. The view model applies the
`Edit` through `body.edit { }`, as `insertLink` does, so the note isn't copied
around it. Autosave picks the change up on its own.

### Pinning the note

A `captureInFlight` flag in `UiState`, set by any track while it works. While
it's set, the chevrons, the swipe, the day sheet and delete are disabled, and
`rollOver` waits. Whatever lands checks it is still inserting into the same
`OpenNote` (the `openGeneration` pattern `createDay` uses). The pin lasts under
a second for a photo, a few seconds for a dictation.

---

## Track A: photos

Two ways in: **take a photo** and **choose photos**.

**Take a photo.** Launch `ActivityResultContracts.TakePicture` at a
`FileProvider` URI in `cacheDir/capture/`, so whichever camera app is the
default does the capturing. On success, `attach` copies it into the vault and
the cache file is deleted. It doesn't write straight into the vault because
the target would have to exist first. Back out of the camera and an empty file
is left behind for whatever syncs the vault. Camera apps are also uneven about writing
to another app's document URI. Copying a 3 to 5 MB JPEG takes a fraction of a
second.

Name: `Photo YYYYMMDDHHmmss.jpg`. This is the one name VaultDrop makes up, and
it follows the shape of Obsidian's own `Recording YYYYMMDDHHmmss` and
`Pasted image YYYYMMDDHHmmss`. See open question 3.

Never declare `CAMERA`. Android throws `SecurityException` for
`ACTION_IMAGE_CAPTURE` from an app that declares the permission without holding
it. Leave a comment in the manifest so nobody adds it later.

**Choose photos.** `ActivityResultContracts.PickMultipleVisualMedia(ImageOnly)`
opens the system photo picker. That needs no storage permission, and it shows
whatever gallery the user already uses. Each photo keeps its display name, as
Obsidian does when attaching a file. The picker's grants are temporary, so copy
straight away. Several photos become one embed per line, in the order picked.

**Formats.** Obsidian [renders](https://obsidian.md/help/file-formats) avif,
bmp, gif, jpeg, jpg, png, svg and webp. HEIC isn't on the list. Most Android cameras save
JPEG, so v1 copies whatever arrives, and a HEIC gets a notice saying Obsidian
won't show it.

**Manifest.** A `FileProvider` (`androidx.core`, already a dependency),
`res/xml/file_paths.xml` for `cache/capture/`, and a `<queries>` entry for
`android.media.action.IMAGE_CAPTURE`.

**Tests.** `AttachmentsTest` and `SpliceTest` cover the logic. On the device,
use a scratch vault with its own `.obsidian/app.json`, not a real one. Cycle
through each `attachmentFolderPath` shape and both link styles. Then open the
scratch vault in desktop Obsidian and confirm every embed renders.

---

## Track B: voice

"Any voice to text" isn't one Android feature. There are three separate ways in,
and each app picks the ones it supports:

| Way in | Answered by | Needs `RECORD_AUDIO` in VaultDrop |
|---|---|---|
| A voice keyboard | Scrib, FUTO, Transcribro, Gboard's mic | no |
| `android.speech.action.RECOGNIZE_SPEECH` | Google, FUTO Voice Input, Transcribro, Kõnele | no |
| [Open Transcribe](https://github.com/23rd/Scrib/blob/main/CONTRACT.md) | Scrib | yes |

Scrib deliberately skips the system path. Its contract explains why: the
framework recognizer needs raw PCM and a microphone permission even for a
file, and FOSS recognizers record the room instead of reading the file. So
supporting Scrib means speaking its contract, and supporting everything else
means firing the standard intent. VaultDrop does both, behind one mic button.

### Layer 1: voice keyboards already work

The editor is a plain `BasicTextField`, so Scrib's keyboard, FUTO's, and
Gboard's mic already type into it. So does Android's Voice Access. Nothing to
build. Say so in the README.

### Layer 2: system voice input

`ActivityResultContracts.StartActivityForResult` with
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` and `LANGUAGE_MODEL_FREE_FORM`. The
chosen app shows its own small window, and the text comes back in
`EXTRA_RESULTS[0]` and is spliced in at the caret.

It needs no permission. Android picks the app. If several can answer, Android
shows its own chooser and remembers "Always", so the question lives in Android,
where the user already answers it for every other app. `onPause` already saves
before the recognizer's window appears.

Label the option with the resolved default when there is one, e.g.
"Voice input (FUTO Voice Input)", and "System voice input" otherwise.

Manifest: a `<queries>` entry for the action.

### Layer 3: Open Transcribe (Scrib)

**Contract.** Copy Scrib's nine `.aidl` files unchanged into
`app/src/main/aidl/org/opentranscribe/api/`. The package has to match or the
binder descriptors won't. Turn on `buildFeatures { aidl = true }` (AGP 8 and
later ship with it off). Scrib and VaultDrop are both GPL-3.0-or-later.

**Manifest.** `RECORD_AUDIO`, and a `<queries>` entry for
`org.opentranscribe.api.ITranscriptionService`.

**Finding and trusting an engine.** `queryIntentServices` for the action lists
every installed implementation. The contract says never auto-bind to whatever
claims the action, because the client is handing over the user's audio, so the
first mic tap asks. Store the chosen component and its signing certificate's
SHA-256 in `ConfigStore`. That's device-local, like the vault URI, because
which apps are installed is a fact about this phone, not about the vault. If the
app disappears or its certificate changes, ask again.

**On tap:**

1. Ask for `RECORD_AUDIO` the first time only.
2. Start `AudioRecord` at once (`VOICE_RECOGNITION` source, 16 kHz mono PCM16),
   so the first word isn't lost while the service binds.
3. Bind with `BIND_AUTO_CREATE` and call `getCapabilities()`.
4. If `modelReady` is false, stop and show "Scrib has no speech model yet" with
   a button that opens Scrib (`getLaunchIntentForPackage`).
5. If `streaming` is true, call `openStream(StreamRequest(languageHint = null,
   sampleRate = 16000, channels = 1))`. Scrib 0.18 reports contract version 2
   with streaming on. A version 1 engine gets the file path instead: record to
   a WAV in cache, then `transcribe(pfd)`.

`openStream` and `transcribe` can return null (Scrib does while its benchmark
runs), which becomes a notice.

**Two threads, not one.** A capture thread reads `AudioRecord` into a queue. A
sender thread drains the queue into `stream.write`. `write` blocks on purpose
while Scrib catches up, for example while it loads a model on first use. On the
capture thread that block would overrun `AudioRecord`'s buffer and drop speech.
Send 20 to 100 ms per write, well under the binder limit.

**While recording**, a strip above the keyboard, where the `[[` strip sits,
shows a pulsing dot and the running text from `onTranscriptionProgress`. That
text is cumulative and only grows, so it's displayed as it arrives. **Stop**
calls `endOfStream()` and shows "Finishing…" until `onTranscriptionResult`,
which is spliced in at the caret. **Cancel** calls `cancel()`.

**Housekeeping:**

- Callbacks arrive on binder threads. Hop to main before touching `state` or
  `body`, per the threading rules.
- Time out on idle, measured from the last callback, not on a total deadline,
  as the contract asks. A long dictation legitimately takes a while.
- Unbind after the terminal callback.
- Leaving the app stops the recording and sends `endOfStream()`. The view model
  outlives the activity, so the text still lands when it arrives. A background
  app can't hold the mic without a microphone foreground service, and this
  plan doesn't add one.

**Keep the audio?** Not in v1 (open question 1). If yes, encode the PCM to AAC
with `MediaCodec` and `MediaMuxer`, name it `Recording YYYYMMDDHHmmss.m4a` to
match Obsidian's recorder, attach it through Track 0, and put the embed above
the transcript.

### One button, few questions

- Only system voice input available: the mic fires it. No VaultDrop question.
- An Open Transcribe app is installed: the first tap shows a sheet with each
  one by name, plus the system voice input entry. The choice is remembered and
  can be changed from the overflow menu ("Voice input…").
- Nothing installed: a notice naming Scrib and FUTO Voice Input on F-Droid.

The package queries run when the mic is tapped, never at launch.

### Where the code lives

The view model sees a small sealed type, `VoiceEngine.System` or
`VoiceEngine.OpenTranscribe(component, certSha256)`, and three events:
progress text, final text, failure. The binder and audio code lives in a new
`voice/` package, the way `VaultFiles` is the one place that touches storage.
Anything pure goes in `core/` with tests: choosing an engine from what's
installed, the idle-timeout rule, the prose splice.

---

## Track C: share into VaultDrop

The other half of "work with what's already there". Anything on the phone that
can share, like Scrib's own app, a voice recorder, the gallery or a browser,
can drop into today's note.

**Intent filters** on `MainActivity`: `SEND` and `SEND_MULTIPLE` for
`text/plain`, `image/*` and `audio/*`.

**Where it lands.** Today's note, at the end, after a blank line. That's where
the caret starts anyway.

**The launch path.** Phase 1 stays as it is. Reading the intent's extras is
in-memory, so it costs nothing before the first frame, and the payload is
parked on the view model:

- Text is applied in `adopt`. If today's note is still being created, it goes
  into the field at once, and the existing `fromCreate` merge puts it after the
  template, exactly like text typed into that gap.
- Files go through Track 0's `attach` on IO after the first frame. The sender's
  grant only lasts as long as the activity, so copy immediately.

With `singleTask`, a share into a running VaultDrop arrives in `onNewIntent`.
Save, open today if another day is on screen, then apply.

**Audio shared in** is attached. If an Open Transcribe engine has been chosen,
the file also goes through `transcribe(pfd)` and the transcript goes under the
embed. That turns a voice memo from any recorder into text in the note.

**Check the launch cost** with `adb shell am start -W` on a share intent, before
and after. The number should not move.

---

## Manifest changes, all tracks

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />  <!-- B, Open Transcribe only -->
<!-- No CAMERA. Declaring it breaks ACTION_IMAGE_CAPTURE for an app that doesn't hold it. -->

<queries>
    <intent><action android:name="android.media.action.IMAGE_CAPTURE" /></intent>          <!-- A -->
    <intent><action android:name="android.speech.action.RECOGNIZE_SPEECH" /></intent>      <!-- B -->
    <intent><action android:name="org.opentranscribe.api.ITranscriptionService" /></intent> <!-- B -->
</queries>

<provider android:name="androidx.core.content.FileProvider" ... />                        <!-- A -->
<!-- SEND / SEND_MULTIPLE filters on MainActivity -->                                       <!-- C -->
```

## Checks against desktop Obsidian

Three behaviours in this plan come from secondary sources or memory. Confirm
each once on the desktop before building on it:

1. Paste the same image twice. The second copy should be `Pasted image … 1.png`,
   which confirms the ` 1` dedup.
2. With Markdown links on, attach a file whose name contains a space, `#`, `(`
   and `%`, and copy the exact link Obsidian writes.
3. Set each of the four attachment locations and read back `app.json`, to pin
   the stored strings in the table above.

## Not in this plan

- **`SpeechRecognizer` and `RecognitionService`.** Google, Transcribro and
  Kõnele offer the service, and all three also answer `RECOGNIZE_SPEECH`. FUTO
  Voice Input answers only the intent, and Scrib answers neither. The service
  path would need `RECORD_AUDIO` and reach no app the intent misses.
- **Video.**
- **Showing images in the editor.** VaultDrop doesn't render Markdown and this
  doesn't change that.
- **Resizing, re-encoding, or stripping EXIF.**
- **An index of attachment names** for exact `shortest` links. See the known gap.

## Open questions

1. **Keep the audio from a dictation?** Recommendation: no for v1. Dictation is
   for getting text down, and a minute of audio is half a megabyte synced to
   every device. Easy to add later with the naming above.
2. **Where do the buttons go?** The editor has no toolbar on purpose, and the
   `[[` strip is described as the affordance that replaces one. Recommendation:
   a slim row above the keyboard with mic, camera and gallery, which gives way
   to the `[[` strip while a link is being typed. The top bar is already full
   on a phone-width screen.
3. **Camera photo name.** `Photo YYYYMMDDHHmmss.jpg`, or Obsidian's own
   `Pasted image YYYYMMDDHHmmss.jpg` so every image looks the same in the vault?
4. **What a share does.** Recommendation: open the editor on today's note with
   the shared thing already in, so you can add a line of context. The
   alternative is appending silently and closing.

## Sources

- Scrib: [repository](https://github.com/23rd/Scrib),
  [CONTRACT.md](https://github.com/23rd/Scrib/blob/main/CONTRACT.md),
  [F-Droid](https://f-droid.org/en/packages/org.scrib.transcriber/). Read at
  commit `e7ba09e` (0.18).
- Transcribro's [manifest](https://github.com/soupslurpr/Transcribro/blob/main/app/src/main/AndroidManifest.xml)
  for which system paths it answers.
- FUTO Voice Input: [source mirror](https://github.com/futo-org/voice-input).
- Obsidian: [attachments](https://obsidian.md/help/attachments),
  [links](https://obsidian.md/help/links),
  [file formats](https://obsidian.md/help/file-formats).
- Obsidian core's attachment folder rules, as reimplemented in
  [obsidian-dev-utils](https://github.com/mnaoumov/obsidian-dev-utils/blob/main/src/obsidian/attachment-path.ts).
