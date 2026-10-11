package dev.vaultdrop.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresPermission
import dev.vaultdrop.core.VoiceEngine
import dev.vaultdrop.core.VoiceEvent
import dev.vaultdrop.core.VoiceFailure
import dev.vaultdrop.core.Wav
import org.opentranscribe.api.ITranscriptionService
import org.opentranscribe.api.ITranscriptionStream
import org.opentranscribe.api.StreamRequest
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * A live dictation into an Open Transcribe engine: the microphone, the engine,
 * and the text that comes back through [onEvent], on the main thread.
 *
 * The microphone opens at once and the engine is bound alongside it, so the
 * first word is not lost while the service starts up. Two threads move the
 * audio, with a queue between them. The capture thread only reads the mic. The
 * sender writes to the engine, and `write` blocks on purpose while the engine
 * catches up, for instance while it loads a model on first use. Blocking the
 * capture thread like that would overrun AudioRecord's buffer and drop speech.
 *
 * An engine that streams gets the audio as it is spoken, through `openStream`.
 * One that cannot gets a WAV from the cache once Stop is pressed, through
 * `transcribe`. Either way the audio is gone when the request ends: the WAV is
 * deleted, and nothing is kept.
 *
 * Main thread, apart from the two threads above.
 */
class Dictation private constructor(
    context: Context,
    engine: VoiceEngine.OpenTranscribe,
    onEvent: (VoiceEvent) -> Unit,
) {
    private val cache = File(context.applicationContext.cacheDir, CACHE_DIR)
    private val queue = LinkedBlockingQueue<ByteArray>()

    /** Cleared by Stop, and by the request ending however it ends. */
    @Volatile
    private var recording = true

    private val link = EngineLink(context, engine) { event ->
        if (event !is VoiceEvent.Progress) recording = false
        onEvent(event)
    }

    /**
     * Closes the mic. What it heard goes on to the engine, which finishes and
     * reports [VoiceEvent.Final]. From here on the engine's silence counts
     * against it; see [dev.vaultdrop.core.IdleTimeout].
     */
    fun stop() {
        if (!recording) return
        recording = false
        link.waiting()
    }

    /** Throws the dictation away. Reports [VoiceFailure.Cancelled] at once, and nothing lands. */
    fun cancel() = link.cancel()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun begin() {
        thread(name = "vaultdrop-mic", isDaemon = true) { capture() }
        link.open(::send)
    }

    // ── Capture thread ───────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun capture() {
        val record = openMic()
        if (record == null) {
            link.fail(VoiceFailure.Microphone)
            queue.offer(END)
            return
        }
        try {
            val buffer = ByteArray(READ_BYTES)
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) {
                    link.fail(VoiceFailure.Microphone)
                    break
                }
                if (read > 0) queue.offer(buffer.copyOf(read))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            queue.offer(END)
        }
    }

    /** Null when the mic will not start, which is usually another app holding it. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openMic(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, MIC_BUFFER_BYTES),
            )
        } catch (_: Exception) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        try {
            record.startRecording()
        } catch (_: IllegalStateException) {
            record.release()
            return null
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return null
        }
        return record
    }

    // ── Sender thread ────────────────────────────────────────────────────────

    private fun send(service: ITranscriptionService) {
        val capabilities = link.ready(service) ?: return
        if (capabilities.streaming) stream(service) else file(service)
    }

    private fun stream(service: ITranscriptionService) {
        val request = StreamRequest().apply {
            languageHint = null
            sampleRate = SAMPLE_RATE
            channels = 1
        }
        val stream = service.openStream(request, link.callback)
        if (stream == null) {
            link.fail(VoiceFailure.Busy)
            return
        }
        if (!link.hold { stream.cancel() }) return
        val buffer = ByteArray(WRITE_BYTES)
        while (true) {
            val (length, ended) = fill(buffer) ?: return
            if (length > 0) write(stream, buffer, length)
            if (ended) {
                stream.endOfStream()
                link.waiting()
                return
            }
        }
    }

    /**
     * The engine cannot stream, so the dictation goes to a WAV in the cache and
     * over as a file once the mic closes.
     */
    private fun file(service: ITranscriptionService) {
        cache.mkdirs()
        val wav = File.createTempFile("dictation", ".wav", cache)
        link.afterwards { wav.delete() }
        RandomAccessFile(wav, "rw").use { out ->
            // The header needs the length, so it goes in last.
            out.write(ByteArray(Wav.HEADER_BYTES))
            var bytes = 0
            val buffer = ByteArray(WRITE_BYTES)
            while (true) {
                val (length, ended) = fill(buffer) ?: return
                out.write(buffer, 0, length)
                bytes += length
                if (ended) break
            }
            out.seek(0)
            out.write(Wav.header(bytes, SAMPLE_RATE, 1))
        }
        val audio = ParcelFileDescriptor.open(wav, ParcelFileDescriptor.MODE_READ_ONLY)
        link.afterwards { audio.close() }
        link.sendFile(service, audio, wav.name, "audio/wav")
    }

    /**
     * Waits for audio, then packs whatever else has queued up behind it into
     * [buffer], up to its size. Returns how much went in and whether the mic
     * has closed, or null once the request is over.
     */
    private fun fill(buffer: ByteArray): Pair<Int, Boolean>? {
        var length = 0
        var next = queue.take()
        while (true) {
            if (link.finished) return null
            if (next === END) return length to true
            next.copyInto(buffer, length)
            length += next.size
            val after = queue.peek() ?: return length to false
            if (after !== END && length + after.size > buffer.size) return length to false
            // Only this thread takes from the queue, so what was peeked is what comes off it.
            next = queue.poll() ?: return length to false
        }
    }

    private fun write(stream: ITranscriptionStream, buffer: ByteArray, length: Int) {
        while (!link.finished) {
            try {
                stream.write(buffer, length)
                return
            } catch (_: IllegalStateException) {
                // The engine's backlog did not drain in time. The contract says
                // the stream is still good, so the same audio goes again rather
                // than leaving a hole in what was said.
            }
        }
    }

    companion object {
        /**
         * Starts listening, and binds [engine] alongside. The caller holds
         * `RECORD_AUDIO` already. Main thread; the mic opens on a thread of its own.
         */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun start(context: Context, engine: VoiceEngine.OpenTranscribe, onEvent: (VoiceEvent) -> Unit): Dictation =
            Dictation(context, engine, onEvent).also { it.begin() }

        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000

        /** Each read off the mic: 40 ms. */
        private const val READ_BYTES = 40 * BYTES_PER_MS

        /** At most this much per write to the engine: 100 ms, as the contract suggests, far under binder's limit. */
        private const val WRITE_BYTES = 100 * BYTES_PER_MS

        /** AudioRecord's own buffer, a second of audio, so a slow read does not lose any. */
        private const val MIC_BUFFER_BYTES = 1000 * BYTES_PER_MS

        private const val CACHE_DIR = "dictation"

        /** Put on the queue when the mic has closed. Compared by identity. */
        private val END = ByteArray(0)
    }
}
