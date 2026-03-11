package com.andreas_kratzer.ghosttalk.core

import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastActivityTime: Long = 0

    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Sicherheits-Check",
        subtitle: String = "Fingerabdruck zum Entsperren verwenden",
        onResult: (Boolean) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setUnlocked(true)
                    onResult(true)
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
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Abbrechen")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    fun unlock(pin: String): Boolean {
        val pinHash = settingsRepository.securityPinHash
        val salt = settingsRepository.securityPinSalt

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
            val legacyPin = settingsRepository.securityPin
            if (!legacyPin.isNullOrEmpty() && pin == legacyPin) {
                // If it matches legacy, we should probably migrate it here too
                // but for now just unlock
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
        
        val timeoutMinutes = settingsRepository.securityPinTimeoutMinutes
        if (timeoutMinutes <= 0) return

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - lastActivityTime
        if (elapsedMillis > timeoutMinutes * 60 * 1000) {
            lock()
        }
    }

    fun isPinSet(): Boolean {
        return !settingsRepository.securityPinHash.isNullOrEmpty() || !settingsRepository.securityPin.isNullOrEmpty()
    }
    
    fun clearPin() {
        settingsRepository.securityPin = ""
        settingsRepository.securityPinHash = ""
        settingsRepository.securityPinSalt = ""
        lock()
    }

    fun isSecurityRequiredForDeletion(): Boolean {
        return settingsRepository.isPinRequiredForDeletion && isPinSet()
    }

    fun isSecurityRequiredForEdit(): Boolean {
        return settingsRepository.isSecurityRequiredForEdit && isPinSet()
    }

    fun isSecurityRequiredForSettings(): Boolean {
        return settingsRepository.isSecurityRequiredForSettings && isPinSet()
    }
}
