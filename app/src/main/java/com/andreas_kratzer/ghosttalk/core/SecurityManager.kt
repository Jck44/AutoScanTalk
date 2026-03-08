package com.andreas_kratzer.ghosttalk.core

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * A flow that emits true if the app is currently unlocked.
     */
    val isGlobalUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastActivityTime: Long = 0

    fun isUnlocked(): Boolean {
        return _isUnlocked.value
    }

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
        val correctPin = settingsRepository.securityPin
        
        return if (!correctPin.isNullOrEmpty() && pin == correctPin) {
            _isUnlocked.value = true
            updateActivity()
            true
        } else {
            false
        }
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
        return !settingsRepository.securityPin.isNullOrEmpty()
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
