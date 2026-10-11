package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {

    private val header = ByteBuffer.wrap(Wav.header(dataBytes = 32_000, sampleRate = 16_000, channels = 1))
        .order(ByteOrder.LITTLE_ENDIAN)

    private fun tag(at: Int) = String(header.array(), at, 4, Charsets.US_ASCII)

    @Test
    fun `it is 44 bytes of RIFF WAVE with a fmt and a data chunk`() {
        assertEquals(44, header.capacity())
        assertEquals("RIFF", tag(0))
        assertEquals("WAVE", tag(8))
        assertEquals("fmt ", tag(12))
        assertEquals("data", tag(36))
    }

    @Test
    fun `the sizes count what follows them`() {
        assertEquals(36 + 32_000, header.getInt(4))
        assertEquals(16, header.getInt(16))
        assertEquals(32_000, header.getInt(40))
    }

    @Test
    fun `it describes 16 kHz mono 16-bit PCM`() {
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))
        assertEquals(2, header.getShort(32).toInt())
        assertEquals(16, header.getShort(34).toInt())
    }

    @Test
    fun `stereo doubles the byte rate and the block`() {
        val stereo = ByteBuffer.wrap(Wav.header(0, 16_000, 2)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64_000, stereo.getInt(28))
        assertEquals(4, stereo.getShort(32).toInt())
    }
}
