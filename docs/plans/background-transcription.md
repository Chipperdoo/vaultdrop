# Plan: keep transcribing with the screen off

Status: proposed, not built. The [open questions](#open-questions) settle the
last details.

You dictate into Scrib, press Stop, and wait while Scrib works through what it
heard. On a large model that can take minutes. Lock the phone during that wait
and the transcription stops until you unlock it. This plan keeps it going.

## Why it stops

Nothing in VaultDrop asks Android to keep it running once it leaves the screen.
Three things follow from that.

1. **VaultDrop goes into the cache.** With no visible activity and no foreground
   service, VaultDrop is a cached process. On Android 14 and later the cached
   apps freezer stops a cached process 10 seconds after it becomes cached. It
   gets no CPU until something thaws it, such as you coming back.
2. **Scrib goes with it.** A process bound with `BIND_AUTO_CREATE` is always at
   least as important as its client, and VaultDrop is the client. Scrib does
   have a foreground service, `TranscriptionForegroundService`, but only its
   in-app runs (`TranscriptionRun`) start it. Its Open Transcribe
   `TranscriptionService` never does (read at commit `43b2e8b`, 2026-10-11).
   Unless something else keeps Scrib up, it is cached when VaultDrop is.
3. **The CPU sleeps.** With the screen off the phone suspends the CPU unless
   some app holds a wake lock. Neither app holds one, so even a process that
   isn't frozen stops computing.

`IdleTimeout` already knows this happens. Its comment says a late check means
"the app was frozen in the background, or the phone was asleep, and an engine
that only this app keeps bound was frozen with it". That rule stops the frozen
gap counting as engine silence, so the transcript survives, but nothing moves
during the gap. The text lands after you unlock, which is what you see.

## What fixes it

Two pieces, and it needs both.

- A **foreground service** in VaultDrop, for as long as an Open Transcribe
  request is open. It keeps VaultDrop out of the cache, and Scrib with it
  through the binding. Neither gets frozen.
- A **partial wake lock**, held by that service, so the CPU keeps running with
  the screen off.

The wake lock alone does nothing for a cached app. AOSP's `PowerManagerService`
disables wake locks held by cached processes ("Cached inactive processes are
never allowed to hold wake locks"). The service alone doesn't stop the CPU
sleeping. Together they hold up through Doze too. In idle mode the same code
ignores an app's partial wake lock only when its process ranks below
`PROCESS_STATE_BOUND_FOREGROUND_SERVICE`, and a foreground service ranks above
it.

Scrib could start its own foreground service for Open Transcribe requests, and
that may be worth suggesting upstream. It wouldn't be enough on its own.
VaultDrop has to be running to receive the transcript and write it to the
vault, so VaultDrop has to stay out of the cache either way. Once it does,
Scrib comes along.

## Design

### The service

`voice/TranscribeService.kt`. A plain started `Service` in VaultDrop's process,
with no binder of its own. `voice/` is already where VaultDrop talks to the
engine.

| Android | Service type | Why |
|---|---|---|
| 15 and up (API 35+) | `mediaProcessing` | For "time-consuming operations on media assets". Google's `dataSync` migration guide points media work here. |
| 10 to 14 (API 29 to 34) | `dataSync` | `mediaProcessing` doesn't exist yet. `dataSync` covers "local file processing", and Scrib's own service uses it. |
| 8 and 9 (API 26 to 28) | none | Types arrived in API 29. |

On apps targeting Android 15 or later, both types get 6 hours in every 24 while
the app is in the background. A dictation comes nowhere near that, but the
service still implements `onTimeout(startId, fgsType)` and calls `stopSelf()`.
A service that ignores the timeout crashes the app.

The other types don't fit. `shortService` ends at about 3 minutes, and a long
dictation on a large model can take longer than that after Stop. `microphone`
is for recording in the background, and the mic is closed before the app
leaves the screen (see [open question 1](#open-questions)). `specialUse` is for
work no other type covers.

Manifest additions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".voice.TranscribeService"
    android:exported="false"
    android:foregroundServiceType="dataSync|mediaProcessing" />
```

All four are normal permissions. Android grants them at install, with no
prompt.

How the service runs:

- `onStartCommand` calls `ServiceCompat.startForeground` first, every time,
  with the type for the running Android version. Android expects that after
  every `startForegroundService`. A service stopped before it gets there
  crashes the app with "Context.startForegroundService() did not then call
  Service.startForeground()".
- Then it checks whether anything still holds it. A request that ended before
  the service got this far leaves nothing, and the service stops itself.
- It stops with `stopSelfResult(latestStartId)`, so a hold taken while it was
  stopping starts it again instead of getting lost.
- The wake lock is `PARTIAL_WAKE_LOCK` and not reference counted. The service
  acquires it in `onStartCommand` with a two-hour ceiling and releases it in
  `onDestroy`. The ceiling only caps a bug. The request itself is the real
  limit, and `IdleTimeout` ends a request after three minutes of engine
  silence.
- `START_NOT_STICKY`. A service Android restarts after a crash has no request
  left to keep alive.

### Holding it

`core/KeepAlive.kt` counts holds. It is pure and unit-tested. The first hold
starts the service, the last release stops it, and releasing the same hold
twice counts once. It runs on the main thread, like `pin()` and `unpin()`.

```kotlin
val awake = TranscribeService.keepAwake(app, label)  // a KeepAlive.Hold
try {
    // the request
} finally {
    awake.release()
}
```

Starting can fail. On Android 12 and later a foreground service can't start
while the app is in the background, and on 15 and later the 6-hour budget can
run out. `keepAwake` catches `IllegalStateException`, the parent of
`ForegroundServiceStartNotAllowedException`, because that class doesn't exist
on Android 8 to 11. The request then carries on the way it does today. Losing
the service is never a reason to lose the dictation.

### When the hold starts

When the request starts, while VaultDrop is on screen. For a dictation that is
in `dictate()`, in the same main-thread turn as `Dictation.start`. Every way
into `dictate()` is a tap, or the mic permission dialog closing over the app.
A mic tap goes through a package query on IO first, so locking the phone in
those few milliseconds gets the start refused, and that one dictation behaves
as it does today.

Starting later, at Stop or when you lock the phone, would look tidier. Stop is
on screen, so Android would allow it there. But locking the phone mid-dictation
is the case that matters, and `onStop` is the moment the app stops being
visible. Android documents no grace period for that, so a start from `onStop`
can be refused.

The cost is a notification while you are still dictating, after 10 seconds
(see [The notification](#the-notification)).

For a shared recording the hold starts in `transcribeShared`, just before the
file goes to the engine. The share has just brought VaultDrop to the screen.

### When it ends

After the transcript is in the note and written to the vault. Not when the
engine answers.

`land()` splices the transcript into the field. The write to the vault comes
600 ms later, from the autosave debounce. If the hold went at the engine's
answer, VaultDrop would drop back into the cache with the transcript only in
memory. A cached app can't hold a wake lock, so the phone may sleep before the
write runs, and the freezer stops the process 10 seconds later if it hasn't.
If Android then kills the process for memory, the transcript is gone.

So the end of a request becomes:

1. land the transcript, or what was heard before a failure, as now
2. `saveNow()?.join()`, inside `withContext(NonCancellable)`
3. `unpin()`
4. `awake.release()`

`saveNow()` returns the `Job` it launches, or null when there is nothing to
write. Its other caller, `onPause`, ignores the result.

The save goes before `unpin()` on purpose. `unpin()` can run `rollOver()`,
which, after midnight, saves the old note and opens the new day. A save after
that finds the new day's note unchanged and skips, and the hold would go while
the old note's write was still in flight.

`transcribeShared` returns its transcript today, and `attachSharedFiles`
inserts it. With the hold inside `transcribeShared` it would go before that
insert, so the insert moves into `transcribeShared`, ahead of the save.

### What stays the same

- **Phase 1.** Nothing new runs at launch. The service, its channel and its
  wake lock exist only after a mic tap or a shared recording.
- **Leaving still closes the mic.** `onStop` still calls `stopDictation()`.
  What was heard goes on to Scrib, which now keeps working on it.
- **System voice input.** That path is another app's window on screen, and
  needs none of this.
- **`IdleTimeout`.** With the wake lock its gap rule should rarely fire. It
  still covers the case where Android refused to start the service.

### The notification

A foreground service has to show one. Channel "Transcribing", at
`IMPORTANCE_LOW`, so it makes no sound and doesn't pop up.

- Title "Scrib is transcribing", from the picked app's label. Text "The text
  goes into the note when it's done." It covers a shared recording as well as
  a dictation.
- No transcript text. The lock screen would show what you just said into your
  journal.
- No action buttons. On Android 12 and later a foreground service's
  notification waits 10 seconds before it shows, unless it has action buttons,
  a media or call type, or opts out. A dictation that ends inside 10 seconds
  never shows one. A Cancel button would make every dictation flash one.
- Tapping it opens VaultDrop through `getLaunchIntentForPackage`, so `voice/`
  doesn't import `MainActivity`. Cancel is on the strip there.
- A monochrome mic icon, `res/drawable/ic_notify_transcribe.xml`.

On Android 13 and later the notification drawer needs `POST_NOTIFICATIONS`,
which VaultDrop doesn't have. Without it the service still runs, and the
notification shows only in the Task Manager in quick settings. See
[open question 2](#open-questions).

Stopping VaultDrop from that Task Manager stops the whole app, with no
callbacks. Typing was saved in `onPause` when the app left the screen. The
dictation in flight is lost.

Swiping VaultDrop out of Recents mid-transcription clears the view model. Its
`finally` cancels the request and releases the hold. Surviving that would mean
moving the request out of the view model, and this plan doesn't.

## Files

| File | Change |
|---|---|
| `voice/TranscribeService.kt` | New. The service, its notification and wake lock, and `keepAwake()`. |
| `core/KeepAlive.kt` | New. The hold counter. |
| `app/src/test/.../core/KeepAliveTest.kt` | New. |
| `EditorViewModel.kt` | A hold in `dictate()` and `transcribeShared()`. `saveNow()` returns its `Job`. The end-of-request order above. The shared transcript's insert moves into `transcribeShared()`. |
| `AndroidManifest.xml` | Four permissions and the service. |
| `res/drawable/ic_notify_transcribe.xml` | New. The notification icon. |
| `MainActivity.kt` | The `onStop` comment, which says the app has no foreground service. |
| `AGENTS.md`, `README.md`, `docs/plans/multimedia.md` | The lines saying leaving pauses the work, or that VaultDrop has no foreground service. |

## Tests

`KeepAliveTest`, in the style of `IdleTimeoutTest`:

- the first hold starts the service, and a second doesn't
- releasing one of two holds doesn't stop it
- the last release stops it, once
- releasing the same hold twice counts once
- a refused start leaves nothing to stop, and the next hold tries again
- a hold after a full stop starts it again

The service itself can't run in a JVM test, and this repo has no instrumented
tests. CI runs the unit tests, lint and `assembleDebug` on the pull request.
Lint's `WakelockTimeout` check catches a wake lock acquired without a ceiling.

## Checks on a phone

These need Scrib with a model downloaded. Use the preview build, so the copy
pointed at your everyday vault is untouched.

1. Dictate for a minute or two, press Stop, and lock the phone at once. Wait as
   long as Scrib normally takes, then unlock. The text should already be in the
   note, with the strip gone.
2. Again, but lock the phone while still recording. The mic stops as it does
   now, and the transcript lands without unlocking.
3. While it transcribes, the service is in the foreground and the wake lock is
   held. Both are gone once the text lands.
4. Force Doze while it transcribes. It still finishes.
5. A dictation under 10 seconds shows no notification.
6. Share a voice memo into VaultDrop and lock the phone. Its transcript lands
   under the embed.
7. On Android 13 or later with VaultDrop's notifications off, a dictation
   still finishes, and the Task Manager lists VaultDrop while it works.
8. On an Android 10 to 14 phone, if there is one, check 1 again. That is the
   `dataSync` path.
9. Swipe VaultDrop out of Recents mid-transcription. The notification goes,
   and `dumpsys power` shows no VaultDrop wake lock.

Linux (fish or bash):

```fish
./gradlew :app:installPreview
adb shell dumpsys activity services dev.vaultdrop.preview | grep -i -E 'isForeground|foregroundServiceType'
adb shell dumpsys power | grep -i vaultdrop
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
# afterwards
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

Windows (PowerShell):

```powershell
.\gradlew.bat :app:installPreview
adb shell dumpsys activity services dev.vaultdrop.preview | Select-String -Pattern 'isForeground|foregroundServiceType'
adb shell dumpsys power | Select-String vaultdrop
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
# afterwards
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

## Not in this plan

- Recording with the screen off. See open question 1.
- A Cancel button on the notification, for the reasons above.
- A "transcript saved" notification. The ongoing one going away says the same
  thing, when notifications are on.
- Surviving a swipe out of Recents. That needs the request owned outside the
  view model.
- Changing Scrib. Its maintainers could start their own foreground service for
  Open Transcribe requests, and VaultDrop would still need this one.

## Open questions

1. **Keep recording when the phone locks?** Today locking stops the mic, and
   this plan keeps that. Recording on would need the `microphone` service type
   and its permission, and the mic indicator would stay lit with the screen
   off. Recommendation: not in this change. It is a separate decision with a
   privacy side, and this service makes it a small step later.
2. **Ask for notification permission?** The service works without it, but on
   Android 13 and later you won't see the notification, and its going away is
   the only sign the work is done. Recommendation: ask once, the first time
   you press Stop on a Scrib dictation. That is when the answer starts to
   matter, and it follows the mic permission's rule of asking the first time
   it is needed and never before. It costs `POST_NOTIFICATIONS` in the
   manifest, one more `VoiceLaunch`, and a flag in `ConfigStore` that
   `clear()` keeps, as it keeps `voice_engine`, because it is a fact about the
   phone. The alternative is never asking, plus a README line on turning
   notifications on in Android's settings.
3. **The notification's wording.** "Scrib is transcribing" and "The text goes
   into the note when it's done." Change it if you'd put it differently.

## Review

I checked the design against the code and Android's rules before settling on
it. This is what that changed.

- **Starting from `onStop`.** My first idea was to start the service only when
  you leave mid-transcription. Android 12 and later refuse foreground service
  starts from the background, and `onStop` is where the app stops being
  visible. The start moved to the mic tap.
- **Releasing when the engine answers.** `EngineLink.settle()` is where every
  request ends, so it looked like the owner. But the transcript is only in
  memory until the debounced save, and a cached app can't hold a wake lock.
  The release moved after `saveNow()?.join()`, which puts the hold in the view
  model, next to the pin.
- **Midnight.** `unpin()` can roll the note over to the new day, so the save
  has to come before it.
- **Shared recordings.** The transcript went in after the function that would
  hold the service had returned. The insert moved inside it.
- **Stopping before `startForeground`.** A request that ends a moment after it
  starts could stop the service before Android delivered `onStartCommand`,
  and that crashes. `startForeground` now comes first in every
  `onStartCommand`, and the service stops itself afterwards.
- **Catching `ForegroundServiceStartNotAllowedException` by name.** It doesn't
  exist on Android 8 to 11. The catch takes its parent.
- **A Cancel action.** It would have made every notification show at once.
  Dropped.
- **Transcript text in the notification.** It would have put the journal on
  the lock screen. Dropped.
- **Android 17.** VaultDrop targets API 37. The changes for that target touch
  background audio playback, focus and volume, not recording or this kind of
  service.
- **Scrib's keyboard.** If Scrib is the active keyboard, Android may keep its
  process up through that binding, and the freeze may not happen. The CPU
  still sleeps without a wake lock. The plan covers both.
- **`mediaProcessing` in the manifest on Android 10 to 14.** Those versions
  don't know the type. Android checks the type passed to `startForeground`
  against the manifest, so the extra flag should be ignored, but I haven't
  found that in writing. Check 8 is there for it. If it fails, the manifest
  drops to `dataSync` alone, which is what Scrib ships.
- **Swiping out of Recents.** This had no check. It is the path where a
  missed release would leave a wake lock behind, so check 9 covers it.

## Sources

- Android: [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
  [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout),
  [launching a service](https://developer.android.com/develop/background-work/services/fgs/launch),
  [the 10-second notification delay](https://developer.android.com/guide/components/foreground-services),
  [the Task Manager](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping),
  [troubleshooting](https://developer.android.com/develop/background-work/services/fgs/troubleshooting),
  [`dataSync` migration](https://developer.android.com/about/versions/15/changes/datasync-migration),
  [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle),
  [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
  [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby),
  [Android 17 changes for apps targeting it](https://developer.android.com/about/versions/17/behavior-changes-17).
- AOSP: [cached apps freezer](https://source.android.com/docs/core/perf/cached-apps-freezer),
  [binder and frozen apps](https://source.android.com/docs/core/architecture/ipc/binder-freezer),
  `PowerManagerService.setWakeLockDisabledStateLocked` in
  [frameworks/base](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/power/PowerManagerService.java).
- Scrib at [`43b2e8b`](https://github.com/23rd/Scrib):
  `TranscriptionService.kt`, `TranscriptionForegroundService.kt`,
  `TranscriptionRun.kt`.
