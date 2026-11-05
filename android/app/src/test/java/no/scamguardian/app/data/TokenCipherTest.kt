package no.scamguardian.app.data

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The cipher with a software AES key. The Keystore part (key generation in AndroidKeyStore) only
 * runs on a device; the app exercises it on the emulator when it saves a token.
 */
class TokenCipherTest {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun cipher(k: SecretKey = key) = TokenCipher(
        key = { k },
        encode = { Base64.getEncoder().encodeToString(it) },
        decode = { Base64.getDecoder().decode(it) },
    )

    private val token = "0123456789abcdef".repeat(4)

    @Test
    fun roundTrip() {
        val c = cipher()
        val blob = c.encrypt(token)
        assertFalse(blob.contains(token))
        assertEquals(token, c.decrypt(blob))
    }

    @Test
    fun freshIvEveryTime() {
        val c = cipher()
        assertNotEquals(c.encrypt(token), c.encrypt(token))
    }

    @Test(expected = GeneralSecurityException::class)
    fun tamperedCiphertextFails() {
        val c = cipher()
        val bytes = Base64.getDecoder().decode(c.encrypt(token))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        c.decrypt(Base64.getEncoder().encodeToString(bytes))
    }

    @Test(expected = GeneralSecurityException::class)
    fun otherKeyFails() {
        val blob = cipher().encrypt(token)
        cipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()).decrypt(blob)
    }

    @Test(expected = GeneralSecurityException::class)
    fun tooShortFails() {
        cipher().decrypt(Base64.getEncoder().encodeToString(ByteArray(8)))
    }
}
