package com.andreas_kratzer.ghosttalk.core.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for AES-GCM encryption and decryption with PBKDF2 key derivation.
 * Designed for cross-device portability when using the same salt (e.g. Google User ID).
 */
object EncryptionUtils {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 10000
    
    // Application-level pepper to add another layer of security to the salt
    private const val PEPPER = "GhostTalk_Secure_Pepper_2026"

    /**
     * Encrypts the [plainText] using a key derived from the [salt].
     * Returns a Base64 encoded string containing the IV and the encrypted data.
     */
    fun encrypt(plainText: String, salt: String): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        // Combine IV and CipherText: [IV (12 bytes)][CipherText]
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts the [encryptedBase64] using a key derived from the [salt].
     */
    fun decrypt(encryptedBase64: String, salt: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        
        if (combined.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted data")
        }
        
        val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
        val cipherText = combined.sliceArray(GCM_IV_LENGTH until combined.size)
        
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        val plainTextBytes = cipher.doFinal(cipherText)
        return String(plainTextBytes, Charsets.UTF_8)
    }

    private fun deriveKey(salt: String): SecretKeySpec {
        val combinedSalt = (salt + PEPPER).toByteArray(Charsets.UTF_8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        // We use a fixed "password" for the derivation, and the varying salt (User ID/Email)
        // This ensures the key is unique per user account.
        val spec = PBEKeySpec("GhostTalk_Internal_PBKDF2_Secret".toCharArray(), combinedSalt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
