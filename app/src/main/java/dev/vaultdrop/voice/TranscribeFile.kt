package dev.vaultdrop.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import dev.vaultdrop.core.VoiceEngine
import dev.vaultdrop.core.VoiceEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Transcribes a recording that already exists, such as a voice memo shared
 * into VaultDrop, through [engine].
 *
 * Suspends until the engine is done and returns its [VoiceEvent.Final] or
 * [VoiceEvent.Failure]. [onProgress] gets the running text in between, on the
 * main thread. Cancelling the coroutine cancels the request, and the engine is
 * told to stop.
 *
 * [audio] belongs to this call from here on. It is closed once the request
 * ends, however it ends, because the contract has the client hold it until
 * then. [fileName] and [mimeType] help the engine tell the container apart;
 * either may be null.
 *
 * Call from the main thread. Nothing here blocks it.
 */
suspend fun transcribeFile(
    context: Context,
    engine: VoiceEngine.OpenTranscribe,
    audio: ParcelFileDescriptor,
    fileName: String?,
    mimeType: String?,
    onProgress: (String) -> Unit = {},
): VoiceEvent = suspendCancellableCoroutine { done ->
    val link = EngineLink(context, engine) { event ->
        if (event is VoiceEvent.Progress) onProgress(event.text)
        else if (done.isActive) done.resume(event)
    }
    link.afterwards { audio.close() }
    link.open { service ->
        if (link.ready(service) != null) link.sendFile(service, audio, fileName, mimeType)
    }
    // Cancellation can arrive on any thread, and the link lives on main.
    done.invokeOnCancellation { Handler(Looper.getMainLooper()).post { link.cancel() } }
}
