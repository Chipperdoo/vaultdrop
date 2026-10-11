package dev.vaultdrop.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import dev.vaultdrop.core.IdleTimeout
import dev.vaultdrop.core.VoiceEngine
import dev.vaultdrop.core.VoiceEvent
import dev.vaultdrop.core.VoiceFailure
import org.opentranscribe.api.ErrorType
import org.opentranscribe.api.ITranscriptionCallback
import org.opentranscribe.api.ITranscriptionService
import org.opentranscribe.api.TranscriberCapabilities
import org.opentranscribe.api.TranscriptionError
import org.opentranscribe.api.TranscriptionRequest
import kotlin.concurrent.thread

/**
 * One request's connection to an Open Transcribe engine, from bind to unbind.
 *
 * This is the part a live dictation and a shared file have in common: binding
 * to exactly the service the user picked, the callback the engine reports
 * through, the idle timeout, and ending exactly once. Every way a request can
 * end goes through [settle], which unbinds and runs whatever cleanup was left
 * with [afterwards], so nothing holds the engine after the terminal callback.
 *
 * Callbacks arrive on binder threads. Each one is posted to the main thread
 * before it touches anything, so [onEvent] only ever runs there.
 *
 * Main thread unless a member says otherwise.
 */
internal class EngineLink(
    context: Context,
    private val engine: VoiceEngine.OpenTranscribe,
    private val onEvent: (VoiceEvent) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val idle = IdleTimeout(IDLE_LIMIT_MS, CHECK_EVERY_MS)
    private val cleanups = mutableListOf<() -> Unit>()

    private var work: ((ITranscriptionService) -> Unit)? = null
    private var bound = false
    private var settled = false

    /** [settled], for the worker threads. Any thread. */
    @Volatile
    var finished = false
        private set

    /** Stops the engine's half of the request, once the engine has one. Set from the worker. */
    @Volatile
    private var stopEngine: (() -> Unit)? = null

    private val tick = object : Runnable {
        override fun run() {
            if (settled) return
            if (idle.check(SystemClock.elapsedRealtime())) {
                settle(VoiceEvent.Failure(VoiceFailure.TimedOut), stop = true)
            } else {
                main.postDelayed(this, CHECK_EVERY_MS)
            }
        }
    }

    /** What the engine reports through. Binder threads, so everything hops to main. */
    val callback: ITranscriptionCallback = object : ITranscriptionCallback.Stub() {
        override fun onTranscriptionProgress(text: String?) {
            main.post { progress(text.orEmpty()) }
        }

        override fun onTranscriptionResult(text: String?) {
            main.post { settle(VoiceEvent.Final(text.orEmpty()), stop = false) }
        }

        override fun onTranscriptionError(error: TranscriptionError?) {
            main.post { settle(VoiceEvent.Failure(failureOf(error)), stop = false) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val job = work
            if (settled || job == null || binder == null) return
            val service = ITranscriptionService.Stub.asInterface(binder)
            // Every call into the engine blocks until it answers, so none of
            // them happen here.
            thread(name = "vaultdrop-transcribe", isDaemon = true) {
                try {
                    job(service)
                } catch (_: RemoteException) {
                    fail(VoiceFailure.Died)
                } catch (e: Exception) {
                    fail(VoiceFailure.Engine(e.message))
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            settle(VoiceEvent.Failure(VoiceFailure.Died), stop = false)
        }

        override fun onBindingDied(name: ComponentName?) {
            settle(VoiceEvent.Failure(VoiceFailure.Died), stop = false)
        }

        override fun onNullBinding(name: ComponentName?) {
            settle(VoiceEvent.Failure(VoiceFailure.Unreachable), stop = false)
        }
    }

    /**
     * Binds to the engine, and runs [work] on a thread of its own once it is
     * there. Only ever the component the user picked: the contract forbids
     * binding to whatever answers the action, because the engine gets the
     * user's audio.
     */
    fun open(work: (ITranscriptionService) -> Unit) {
        this.work = work
        val intent = Intent(OPEN_TRANSCRIBE_ACTION).setClassName(engine.packageName, engine.className)
        bound = try {
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            // A refused bind still registered the connection.
            runCatching { app.unbindService(connection) }
            main.post { settle(VoiceEvent.Failure(VoiceFailure.Unreachable), stop = false) }
        }
    }

    /**
     * The engine's capabilities, or null after failing the request when it
     * cannot take one yet. Worker thread.
     */
    fun ready(service: ITranscriptionService): TranscriberCapabilities? {
        val capabilities = service.capabilities
        when {
            capabilities == null -> fail(VoiceFailure.Engine(null))
            !capabilities.modelReady -> fail(VoiceFailure.ModelNotReady)
            else -> return capabilities
        }
        return null
    }

    /**
     * The engine has the request now, and [stop] cancels it. False when the
     * request ended while the engine was being asked, in which case [stop] has
     * already been called. Worker thread.
     */
    fun hold(stop: () -> Unit): Boolean {
        stopEngine = stop
        if (!finished) return true
        runCatching(stop)
        return false
    }

    /**
     * Hands [audio] over as a file and starts waiting on it. Worker thread.
     * The caller closes [audio] through [afterwards]: the contract leaves the
     * descriptor with the client until the request settles.
     */
    fun sendFile(service: ITranscriptionService, audio: ParcelFileDescriptor, fileName: String?, mimeType: String?) {
        val request = TranscriptionRequest().apply {
            this.fileName = fileName
            this.mimeType = mimeType
            languageHint = null
        }
        val session = service.transcribe(audio, request, callback)
        if (session == null) {
            fail(VoiceFailure.Busy)
            return
        }
        if (hold { session.cancel() }) waiting()
    }

    /** Runs [block] off the main thread once the request has ended, or now if it already has. Any thread. */
    fun afterwards(block: () -> Unit) {
        val late = synchronized(cleanups) {
            if (!finished) cleanups += block
            finished
        }
        if (late) runCatching(block)
    }

    /** Everything is with the engine now, so its silence starts to count. Any thread. */
    fun waiting() {
        main.post {
            if (settled) return@post
            idle.waiting(SystemClock.elapsedRealtime())
            main.removeCallbacks(tick)
            main.postDelayed(tick, CHECK_EVERY_MS)
        }
    }

    /** Ends the request without a transcript. Any thread. */
    fun fail(reason: VoiceFailure) {
        main.post { settle(VoiceEvent.Failure(reason), stop = true) }
    }

    /** The user does not want the answer any more. The engine is told to stop working on it. */
    fun cancel() = settle(VoiceEvent.Failure(VoiceFailure.Cancelled), stop = true)

    private fun progress(text: String) {
        if (settled) return
        idle.heard(SystemClock.elapsedRealtime())
        onEvent(VoiceEvent.Progress(text))
    }

    private fun settle(event: VoiceEvent, stop: Boolean) {
        if (settled) return
        settled = true
        // Before reading stopEngine, so a worker that sets it after this still sees the end; see hold.
        finished = true
        main.removeCallbacks(tick)
        if (stop) stopEngine?.let { runCatching(it) }
        if (bound) {
            bound = false
            runCatching { app.unbindService(connection) }
        }
        val jobs = synchronized(cleanups) { cleanups.toList().also { cleanups.clear() } }
        if (jobs.isNotEmpty()) {
            thread(name = "vaultdrop-transcribe-cleanup", isDaemon = true) { jobs.forEach { runCatching(it) } }
        }
        onEvent(event)
    }

    private fun failureOf(error: TranscriptionError?): VoiceFailure = when (error?.type) {
        ErrorType.CANCELLED -> VoiceFailure.Cancelled
        ErrorType.MODEL_NOT_AVAILABLE -> VoiceFailure.ModelNotReady
        else -> VoiceFailure.Engine(error?.message?.takeIf { it.isNotBlank() })
    }

    private companion object {
        /**
         * Long, because Cancel is on screen the whole time and a timeout throws
         * the transcript away. Scrib cuts a stream into pieces of at most 20 s
         * and reports each one, and a large model on a slow phone can take a
         * while over a piece.
         */
        const val IDLE_LIMIT_MS = 3 * 60_000L
        const val CHECK_EVERY_MS = 5_000L
    }
}
