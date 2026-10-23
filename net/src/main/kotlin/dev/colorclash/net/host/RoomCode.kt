package dev.colorclash.net.host

import java.security.SecureRandom

object RoomCode {
    // No 0/O or 1/I so codes are easy to read out loud.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(length: Int = 4): String = buildString {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    internal fun newSessionToken(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
