package dev.vaultdrop.core

import java.security.MessageDigest

object Hashing {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    /** The `sha256 4f9a…c210` line the config screen prints. */
    fun elide(hash: String): String =
        if (hash.length < 12) hash else "${hash.take(4)}…${hash.takeLast(4)}"

    private val HEX = "0123456789abcdef".toCharArray()
}
