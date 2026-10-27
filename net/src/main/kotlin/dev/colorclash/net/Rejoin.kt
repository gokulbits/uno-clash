package dev.colorclash.net

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Getting a seat back without handing the seat's token to anyone listening. The client makes up a
 * nonce and HMACs it under the token; the host checks the proof against each seat and refuses a
 * nonce that seat has already been reclaimed with.
 *
 * This is not a stand-in for a secure transport — the game talks plain ws:// on a LAN by design.
 * What it buys: watching a rejoin no longer yields a credential that can be replayed at will, and a
 * captured pair is good for at most one use.
 */
object Rejoin {
    const val NONCE_CHARS = 32

    private val random = SecureRandom()

    fun newNonce(): String {
        val bytes = ByteArray(NONCE_CHARS / 2).also { random.nextBytes(it) }
        return bytes.hex()
    }

    /** Tied to the room as well, so a nonce from one room is no use in another. */
    fun proof(token: String, nonce: String, roomCode: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(token.toByteArray(), ALGORITHM))
        return mac.doFinal("$nonce|${roomCode.uppercase()}".toByteArray()).hex()
    }

    /** Compared without an early exit, so a near miss isn't given away by how long it took. */
    fun matches(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private const val ALGORITHM = "HmacSHA256"
}
