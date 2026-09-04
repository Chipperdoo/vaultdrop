package dev.vaultdrop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashingTest {

    @Test
    fun `matches the known sha256 of the empty input`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hashing.sha256(ByteArray(0)),
        )
    }

    @Test
    fun `a config edit changes the hash`() {
        val before = """{"format":"YYYY-MM-DD","folder":"Daily Notes"}"""
        val after = """{"format":"YYYY-MM-DD ddd","folder":"Daily Notes"}"""
        assertNotEquals(
            Hashing.sha256(before.toByteArray()),
            Hashing.sha256(after.toByteArray()),
        )
    }

    @Test
    fun `elide produces the short form the config screen prints`() {
        assertEquals("4f9a…c210", Hashing.elide("4f9a1234567890c210"))
    }
}
