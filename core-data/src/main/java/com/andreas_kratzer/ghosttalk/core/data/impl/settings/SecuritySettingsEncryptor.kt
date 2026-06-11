package com.andreas_kratzer.ghosttalk.core.data.impl.settings
 
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecuritySettingsEncryptor {
 
    private const val LEGACY_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val PREFIX_LOCAL = "enc_local:"
    private const val PREFIX_TRANSIT = "enc_transit:"

    private const val KEYSTORE_ALIAS = "ghosttalk_settings_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val PREFIX_KEYSTORE = "enc_keystore:"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
    }
 
    private fun deriveKey(seed: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
 
    fun encryptLocal(value: String, context: android.content.Context): String {
        if (value.isEmpty()) return ""
        try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(GCM_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            val ivBytes = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ivBytes + encryptedBytes
            return PREFIX_KEYSTORE + Base64.encodeToString(combined, Base64.NO_WRAP).trim()
        } catch (e: Exception) {
            android.util.Log.e("SecurityEncryptor", "Encryption failed", e)
            return ""
        }
    }
 
    fun decryptLocal(encryptedValue: String, context: android.content.Context): String {
        if (encryptedValue.isEmpty()) return ""
        
        // Transparent legacy decryption fallback
        if (encryptedValue.startsWith(PREFIX_LOCAL)) {
            return decryptLocalLegacy(encryptedValue, context)
        }

        if (!encryptedValue.startsWith(PREFIX_KEYSTORE)) return encryptedValue
        try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(GCM_ALGORITHM)
            val rawValue = encryptedValue.removePrefix(PREFIX_KEYSTORE)
            val decoded = Base64.decode(rawValue, Base64.NO_WRAP)
            if (decoded.size <= 12) return "" // GCM IV is normally 12 bytes
            
            val ivBytes = decoded.copyOfRange(0, 12)
            val ciphertext = decoded.copyOfRange(12, decoded.size)
            
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ivBytes))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityEncryptor", "Decryption failed", e)
            return ""
        }
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun decryptLocalLegacy(encryptedValue: String, context: android.content.Context): String {
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "ghosttalk_default_id"
            val keySpec = deriveKey(androidId)
            
            val cipher = Cipher.getInstance(LEGACY_ALGORITHM)
            val rawValue = encryptedValue.removePrefix(PREFIX_LOCAL)
            val decoded = Base64.decode(rawValue, Base64.DEFAULT)
            if (decoded.size <= 16) return ""
            val ivBytes = decoded.copyOfRange(0, 16)
            val ciphertext = decoded.copyOfRange(16, decoded.size)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityEncryptor", "Legacy Decryption failed", e)
            return ""
        }
    }

    fun migratePreferences(prefs: android.content.SharedPreferences, context: android.content.Context) {
        val allEntries = prefs.all
        val toMigrate = mutableMapOf<String, String>()
        for ((key, value) in allEntries) {
            if (value is String && value.startsWith(PREFIX_LOCAL)) {
                val decrypted = decryptLocalLegacy(value, context)
                if (decrypted.isNotEmpty()) {
                    val reEncrypted = encryptLocal(decrypted, context)
                    if (reEncrypted.isNotEmpty()) {
                        toMigrate[key] = reEncrypted
                    }
                }
            }
        }
        if (toMigrate.isNotEmpty()) {
            prefs.edit().apply {
                for ((key, value) in toMigrate) {
                    putString(key, value)
                }
                apply()
            }
        }
    }
 
    fun encryptForTransit(value: String, userSeed: String): String {
        if (value.isEmpty()) return ""
        try {
            val keySpec = deriveKey(userSeed)
            val cipher = Cipher.getInstance(LEGACY_ALGORITHM)
            val ivBytes = ByteArray(16)
            SecureRandom().nextBytes(ivBytes)
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ivBytes + encryptedBytes
            return PREFIX_TRANSIT + Base64.encodeToString(combined, Base64.NO_WRAP).trim()
        } catch (e: Exception) {
            android.util.Log.e("SecurityEncryptor", "Transit encryption failed", e)
            return ""
        }
    }
 
    fun decryptFromTransit(encryptedValue: String, userSeed: String): String {
        if (encryptedValue.isEmpty()) return ""
        if (!encryptedValue.startsWith(PREFIX_TRANSIT)) return encryptedValue
        try {
            val keySpec = deriveKey(userSeed)
            val cipher = Cipher.getInstance(LEGACY_ALGORITHM)
            
            val rawEncrypted = encryptedValue.removePrefix(PREFIX_TRANSIT)
            val decoded = Base64.decode(rawEncrypted, Base64.NO_WRAP)
            if (decoded.size <= 16) return ""
            val ivBytes = decoded.copyOfRange(0, 16)
            val ciphertext = decoded.copyOfRange(16, decoded.size)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityEncryptor", "Transit decryption failed", e)
            return ""
        }
    }
}
