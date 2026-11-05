package no.scamguardian.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the device token with AES-256-GCM. On the phone the key lives in the Android Keystore
 * ([keystore]) and never leaves it; only the ciphertext is written to DataStore. Tests pass a
 * software key and a JVM Base64 codec.
 *
 * Format: base64(12-byte IV || ciphertext with 16-byte tag).
 */
class TokenCipher(
    private val key: () -> SecretKey,
    private val encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) },
    private val decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) },
) {
    fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key()) // the Keystore picks a fresh random IV
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv + ct)
    }

    /** Throws [GeneralSecurityException] if the blob was changed or the key is gone. */
    fun decrypt(blob: String): String {
        val bytes = decode(blob)
        if (bytes.size <= IV_BYTES) throw GeneralSecurityException("ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val AAD = "scam-guardian-device-token-v1".toByteArray()
        private const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "guardian_device_token"

        fun keystore(): TokenCipher = TokenCipher(key = ::keystoreKey)

        @Synchronized
        private fun keystoreKey(): SecretKey {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            gen.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return gen.generateKey()
        }

        /** Deletes the Keystore key ("Forget board"). */
        fun deleteKeystoreKey() {
            runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
        }
    }
}
