package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecuritySettingsEncryptorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val mockedPrefsStore = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        // Mock shared preferences
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.all } answers { mockedPrefsStore }
        
        every { mockPrefs.getString(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as String?
            mockedPrefsStore.getOrDefault(key, default) as String?
        }
        
        every { mockEditor.putString(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as String?
            if (value == null) {
                mockedPrefsStore.remove(key)
            } else {
                mockedPrefsStore[key] = value
            }
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        // Setup mock android ID
        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "test_android_id_12345"

        // Mock Base64
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(args[0] as ByteArray)
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val input = args[0] as String
            // Handle differences in padding or format if any, but standard decoder is fine
            java.util.Base64.getDecoder().decode(input.trim().replace("\n", "").replace("\r", ""))
        }
        every { android.util.Base64.decode(any<ByteArray>(), any()) } answers {
            java.util.Base64.getDecoder().decode(args[0] as ByteArray)
        }

        // Mock KeyStore since the Android Keystore provider does not exist in standard JVM unit tests.
        // We will mock KeyStore static methods or bypass keystore load, or we can use security providers.
        // In our case, `SecuritySettingsEncryptor.getSecretKey()` accesses:
        // KeyStore.getInstance("AndroidKeyStore")
        // Since we are running in a JVM JUnit test environment, AndroidKeyStore is not registered.
        // Let's mock KeyStore.getInstance to return a local KeyStore or simulated KeyStore.
        mockkStatic(KeyStore::class)
        val mockKeyStore = mockk<KeyStore>(relaxed = true)
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        
        // Let's generate a real AES key locally and return it when KeyStore.getKey is called.
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getKey(any(), any()) } returns secretKey
    }

    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val plaintext = "super_secret_api_key_123!"
        val encrypted = SecuritySettingsEncryptor.encryptLocal(plaintext, mockContext)
        
        assertTrue(encrypted.startsWith("enc_keystore:"))
        assertNotEquals(plaintext, encrypted)
        
        val decrypted = SecuritySettingsEncryptor.decryptLocal(encrypted, mockContext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testLegacyDecryptionFallback() {
        // Since we cannot easily invoke encryptLocalLegacy (as it's removed or kept private),
        // we can test legacy decryption by using the hardcoded prefix or manual legacy encryption structure if needed.
        // Let's verify that a legacy prefixed string decrypts correctly.
        // A legacy encrypted string is: PREFIX_LOCAL + Base64(IV + Ciphertext)
        // Let's simulate encrypting under CBC with a derived key.
        val plaintext = "legacy_secret_456"
        
        // Derive key legacy-style:
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("test_android_id_12345".toByteArray(Charsets.UTF_8))
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = ByteArray(16) { 0x01.toByte() } // Mock IV
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.IvParameterSpec(ivBytes))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ivBytes + ciphertext
        val base64Encoded = android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT).trim()
        val legacyEncryptedValue = "enc_local:$base64Encoded"

        // Now decrypt it using the SecuritySettingsEncryptor
        val decrypted = SecuritySettingsEncryptor.decryptLocal(legacyEncryptedValue, mockContext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testPreferenceMigration() {
        val legacyValue = "legacy_secret_999"
        
        // Generate legacy encrypted string
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("test_android_id_12345".toByteArray(Charsets.UTF_8))
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = ByteArray(16) { 0x02.toByte() }
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.IvParameterSpec(ivBytes))
        val ciphertext = cipher.doFinal(legacyValue.toByteArray(Charsets.UTF_8))
        val combined = ivBytes + ciphertext
        val base64Encoded = android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT).trim()
        val legacyEncryptedValue = "enc_local:$base64Encoded"

        // Place in mocked prefs
        mockedPrefsStore["test_setting_key"] = legacyEncryptedValue

        // Migrate preferences
        SecuritySettingsEncryptor.migratePreferences(mockPrefs, mockContext)

        // The preference should now be encrypted using KeyStore AES-GCM
        val migratedValue = mockedPrefsStore["test_setting_key"] as String
        assertTrue(migratedValue.startsWith("enc_keystore:"))
        
        // Decrypt with decryptLocal and verify it equals plaintext
        val decrypted = SecuritySettingsEncryptor.decryptLocal(migratedValue, mockContext)
        assertEquals(legacyValue, decrypted)
    }

    @Test
    fun testDecryptionFailureReturnsEmptyString() {
        val invalidEncrypted = "enc_keystore:invalid_base64_data_here"
        val decrypted = SecuritySettingsEncryptor.decryptLocal(invalidEncrypted, mockContext)
        assertEquals("", decrypted)
    }
}
