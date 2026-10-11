package dev.vaultdrop.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The WAV a dictation is written to when the engine cannot take a live stream:
 * plain 16-bit PCM, which every Open Transcribe engine has to be able to read.
 */
object Wav {
    const val HEADER_BYTES = 44

    /** The header for [dataBytes] of 16-bit little-endian samples. */
    fun header(dataBytes: Int, sampleRate: Int, channels: Int): ByteArray {
        val blockAlign = channels * 2
        return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(HEADER_BYTES - 8 + dataBytes)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1) // PCM
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * blockAlign)
            .putShort(blockAlign.toShort())
            .putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(dataBytes)
            .array()
    }
}
