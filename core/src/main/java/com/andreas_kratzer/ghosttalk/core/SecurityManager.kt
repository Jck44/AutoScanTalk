package com.andreas_kratzer.ghosttalk.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val securitySettings: SecuritySettings,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastActivityTime: Long = 0
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "ghosttalk_biometric_key"
        private const val PREFS_BIOMETRIC_NAME = "security_biometric_prefs"
        private const val KEY_CIPHERTEXT = "biometric_ciphertext"
        private const val KEY_IV = "biometric_iv"
        private const val DUMMY_DATA = "ghosttalk_biometric_verified_token"
    }

    fun isBiometricSupported(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enrollBiometric(
        activity: FragmentActivity,
        onResult: (Boolean) -> Unit
    ) {
        try {
            // Delete old key if it exists to start fresh
            keyStore.deleteEntry(KEY_ALIAS)
            
            // Generate a new key in Android KeyStore
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true) // recommended for security
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        try {
                            val authenticatedCipher = result.cryptoObject?.cipher
                            if (authenticatedCipher != null) {
                                val encryptedBytes = authenticatedCipher.doFinal(DUMMY_DATA.toByteArray(Charsets.UTF_8))
                                val iv = authenticatedCipher.iv
                                
                                val prefs = activity.getSharedPreferences(PREFS_BIOMETRIC_NAME, Context.MODE_PRIVATE)
                                prefs.edit {
                                    putString(KEY_CIPHERTEXT, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
                                    putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                                }
                                
                                onResult(true)
                            } else {
                                onResult(false)
                            }
                        } catch (_: Exception) {
                            onResult(false)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onResult(false)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onResult(false)
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.security_biometric_enroll_title))
                .setSubtitle(activity.getString(R.string.security_biometric_enroll_subtitle))
                .setNegativeButtonText(activity.getString(R.string.security_biometric_cancel))
                .build()

            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            onResult(false)
        }
    }

    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String? = null,
        subtitle: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        try {
            val prefs = activity.getSharedPreferences(PREFS_BIOMETRIC_NAME, Context.MODE_PRIVATE)
            val ciphertextStr = prefs.getString(KEY_CIPHERTEXT, null)
            val ivStr = prefs.getString(KEY_IV, null)

            if (ciphertextStr == null || ivStr == null) {
                // Self-healing migration: disable biometric setting if credentials don't exist yet
                securitySettings.isBiometricEnabled = false
                activity.runOnUiThread {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.security_biometric_migration_toast),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                onResult(false)
                return
            }

            val ciphertext = Base64.decode(ciphertextStr, Base64.DEFAULT)
            val iv = Base64.decode(ivStr, Base64.DEFAULT)

            val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        try {
                            val authenticatedCipher = result.cryptoObject?.cipher
                            if (authenticatedCipher != null) {
                                val decryptedBytes = authenticatedCipher.doFinal(ciphertext)
                                val decryptedStr = String(decryptedBytes, Charsets.UTF_8)
                                if (decryptedStr == DUMMY_DATA) {
                                    setUnlocked(true)
                                    onResult(true)
                                } else {
                                    onResult(false)
                                }
                            } else {
                                onResult(false)
                            }
                        } catch (_: Exception) {
                            onResult(false)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onResult(false)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onResult(false)
                    }
                })

            val resolvedTitle = title ?: activity.getString(R.string.security_biometric_auth_title)
            val resolvedSubtitle = subtitle ?: activity.getString(R.string.security_biometric_auth_subtitle)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(resolvedTitle)
                .setSubtitle(resolvedSubtitle)
                .setNegativeButtonText(activity.getString(R.string.security_biometric_cancel))
                .build()

            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            onResult(false)
        }
    }

    fun clearBiometricKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
            val prefs = context.getSharedPreferences(PREFS_BIOMETRIC_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_CIPHERTEXT).remove(KEY_IV) }
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun unlock(pin: String): Boolean {
        val pinHash = securitySettings.securityPinHash
        val salt = securitySettings.securityPinSalt

        return if (!pinHash.isNullOrEmpty() && !salt.isNullOrEmpty()) {
            val hashedInput = hashPin(pin, salt)
            if (hashedInput == pinHash) {
                _isUnlocked.value = true
                updateActivity()
                true
            } else {
                false
            }
        } else {
            // Fallback for migration or not set
            val legacyPin = securitySettings.securityPin
            if (!legacyPin.isNullOrEmpty() && pin == legacyPin) {
                _isUnlocked.value = true
                updateActivity()
                true
            } else {
                false
            }
        }
    }

    fun hashPin(pin: String, salt: String): String {
        val iterations = 10000
        val keyLength = 256
        val saltBytes = Base64.decode(salt, Base64.DEFAULT)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iterations, keyLength)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.DEFAULT).trim()
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.DEFAULT).trim()
    }

    fun setUnlocked(unlocked: Boolean) {
        if (unlocked) updateActivity()
        _isUnlocked.value = unlocked
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    fun checkTimeout() {
        if (!_isUnlocked.value) return
        
        val timeoutMinutes = securitySettings.securityPinTimeoutMinutes
        if (timeoutMinutes <= 0) return

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - lastActivityTime
        if (elapsedMillis > timeoutMinutes * 60 * 1000) {
            lock()
        }
    }

    fun isPinSet(): Boolean {
        return !securitySettings.securityPinHash.isNullOrEmpty() || !securitySettings.securityPin.isNullOrEmpty()
    }
    
    fun clearPin() {
        securitySettings.securityPin = ""
        securitySettings.securityPinHash = ""
        securitySettings.securityPinSalt = ""
        clearBiometricKey()
        lock()
    }

    fun isSecurityRequiredForDeletion(): Boolean {
        return securitySettings.isPinRequiredForDeletion && isPinSet()
    }

    fun isSecurityRequiredForEdit(): Boolean {
        return securitySettings.isSecurityRequiredForEdit && isPinSet()
    }

    fun isSecurityRequiredForSettings(): Boolean {
        return securitySettings.isSecurityRequiredForSettings && isPinSet()
    }

    fun isSecurityRequiredForAnalytics(): Boolean {
        return securitySettings.isSecurityRequiredForAnalytics && isPinSet()
    }
}
