package dev.colorclash.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the one secret this phone keeps: the token that gets a seat back after the app was killed.
 * The key lives in the platform keystore and never comes out of it, so the stored value is no use
 * on its own. No new dependency for this — AndroidKeyStore has done AES-GCM since long before this
 * app's minSdk, and the Jetpack wrapper for it is deprecated.
 *
 * Both calls are total: anything unexpected means no stored session rather than a crash.
 */
object SecretStore {

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray())
        // The IV goes first; GCM needs it back and it isn't a secret.
        Base64.getEncoder().encodeToString(cipher.iv + body)
    }.getOrNull()

    fun decrypt(stored: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(stored)
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES))
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "color_clash_session"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
}
