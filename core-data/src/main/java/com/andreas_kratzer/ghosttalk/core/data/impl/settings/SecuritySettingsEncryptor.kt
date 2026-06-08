package com.andreas_kratzer.ghosttalk.core.data.impl.settings
 
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
 
object SecuritySettingsEncryptor {
 
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val PREFIX_LOCAL = "enc_local:"
    private const val PREFIX_TRANSIT = "enc_transit:"
 
    private fun deriveKey(seed: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
 
    fun encryptLocal(value: String, context: android.content.Context): String {
        if (value.isEmpty()) return ""
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "ghosttalk_default_id"
            val keySpec = deriveKey(androidId)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivBytes = ByteArray(16)
            SecureRandom().nextBytes(ivBytes)
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ivBytes + encryptedBytes
            return PREFIX_LOCAL + Base64.encodeToString(combined, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            return value
        }
    }
 
    fun decryptLocal(encryptedValue: String, context: android.content.Context): String {
        if (encryptedValue.isEmpty()) return ""
        if (!encryptedValue.startsWith(PREFIX_LOCAL)) return encryptedValue
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "ghosttalk_default_id"
            val keySpec = deriveKey(androidId)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            val rawValue = encryptedValue.removePrefix(PREFIX_LOCAL)
            val decoded = Base64.decode(rawValue, Base64.DEFAULT)
            if (decoded.size <= 16) return encryptedValue
            val ivBytes = decoded.copyOfRange(0, 16)
            val ciphertext = decoded.copyOfRange(16, decoded.size)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return encryptedValue
        }
    }
 
    fun encryptForTransit(value: String, userSeed: String): String {
        if (value.isEmpty()) return ""
        try {
            val keySpec = deriveKey(userSeed)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivBytes = ByteArray(16)
            SecureRandom().nextBytes(ivBytes)
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ivBytes + encryptedBytes
            return PREFIX_TRANSIT + Base64.encodeToString(combined, Base64.NO_WRAP).trim()
        } catch (e: Exception) {
            return value
        }
    }
 
    fun decryptFromTransit(encryptedValue: String, userSeed: String): String {
        if (encryptedValue.isEmpty()) return ""
        if (!encryptedValue.startsWith(PREFIX_TRANSIT)) return encryptedValue
        try {
            val keySpec = deriveKey(userSeed)
            val cipher = Cipher.getInstance(ALGORITHM)
            
            val rawEncrypted = encryptedValue.removePrefix(PREFIX_TRANSIT)
            val decoded = Base64.decode(rawEncrypted, Base64.NO_WRAP)
            if (decoded.size <= 16) return encryptedValue
            val ivBytes = decoded.copyOfRange(0, 16)
            val ciphertext = decoded.copyOfRange(16, decoded.size)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return encryptedValue
        }
    }
}
