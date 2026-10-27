package dev.colorclash.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RejoinTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun `a nonce is long enough to not be guessed and never repeats`() {
        val nonces = List(200) { Rejoin.newNonce() }
        assertTrue(nonces.all { it.length == Rejoin.NONCE_CHARS })
        assertEquals(nonces.size, nonces.distinct().size)
    }

    @Test
    fun `the same token, nonce and room always give the same proof`() {
        val nonce = Rejoin.newNonce()
        assertTrue(Rejoin.matches(Rejoin.proof(token, nonce, "ABCD"), Rejoin.proof(token, nonce, "ABCD")))
        // Room codes travel uppercased, so the proof mustn't care about the case it was given.
        assertTrue(Rejoin.matches(Rejoin.proof(token, nonce, "abcd"), Rejoin.proof(token, nonce, "ABCD")))
    }

    @Test
    fun `a proof is useless with another token, nonce or room`() {
        val nonce = Rejoin.newNonce()
        val proof = Rejoin.proof(token, nonce, "ABCD")
        assertFalse(Rejoin.matches(proof, Rejoin.proof(token.dropLast(1) + "0", nonce, "ABCD")))
        assertFalse(Rejoin.matches(proof, Rejoin.proof(token, Rejoin.newNonce(), "ABCD")))
        assertFalse(Rejoin.matches(proof, Rejoin.proof(token, nonce, "WXYZ")))
    }

    @Test
    fun `the proof gives nothing away about the token`() {
        val nonce = Rejoin.newNonce()
        val proof = Rejoin.proof(token, nonce, "ABCD")
        assertFalse(token in proof)
        assertNotEquals(token, proof)
    }

    @Test
    fun `mismatched lengths compare false rather than throwing`() {
        assertFalse(Rejoin.matches("abc", "abcd"))
        assertFalse(Rejoin.matches("", "a"))
        assertTrue(Rejoin.matches("", ""))
    }
}
