package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.ai.domain.ActivateGeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.GetGeminiToolStatusUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.HandleGenAiExceptionUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenAiSettingsDelegate @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    private val getGeminiToolStatusUseCase: GetGeminiToolStatusUseCase,
    private val activateGeminiUseCase: ActivateGeminiUseCase,
    private val handleGenAiExceptionUseCase: HandleGenAiExceptionUseCase,
    private val signInUseCase: SignInUseCase
) {
    private val _geminiToolStatus = MutableStateFlow<Map<String, GeminiUseCase.ToolStatus>>(emptyMap())
    val geminiToolStatus: StateFlow<Map<String, GeminiUseCase.ToolStatus>> = _geminiToolStatus.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<android.content.Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    fun updateGeminiToolStatus() {
        _geminiToolStatus.value = getGeminiToolStatusUseCase()
    }

    fun setGeminiCloudEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
        val useApiKey = settingsRepository.useGeminiApiKey
        if (enabled) {
            if (useApiKey) {
                settingsRepository.isGeminiEnabled = true
                updateGeminiToolStatus()
            } else {
                if (authManager.userEmail.value == null) {
                    val activity = findActivity(context) ?: return
                    scope.launch {
                        val result = signInUseCase.execute(activity)
                        if (result) {
                            settingsRepository.isGeminiEnabled = true
                            updateGeminiToolStatus()
                        }
                    }
                } else {
                    settingsRepository.isGeminiEnabled = true
                    updateGeminiToolStatus()
                }
            }
        } else {
            settingsRepository.isGeminiEnabled = false
            updateGeminiToolStatus()
        }
    }

    fun activateGemini(context: Context, scope: CoroutineScope) {
        scope.launch {
            activateGeminiUseCase.execute(
                onSuccess = {
                    updateGeminiToolStatus()
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, application.getString(R.string.settings_gemini_activation_success), Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { e ->
                    val effect = handleGenAiExceptionUseCase.execute(e)
                    if (effect is HandleGenAiExceptionUseCase.Effect.EmitAuthIntent) {
                        scope.launch {
                            _authIntentFlow.emit(effect.intent)
                        }
                    }
                }
            )
        }
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    suspend fun saveGeminiApiKeyToGoogle(activity: android.app.Activity): PasswordManagerResult {
        val key = settingsRepository.geminiApiKey
        if (key.isNullOrEmpty()) return PasswordManagerResult.Error("API Key is empty")
        
        return authManager.saveApiKeyToPasswordManager(activity, key, "gemini").fold(
            onSuccess = { PasswordManagerResult.Success },
            onFailure = { e -> e.toPasswordManagerResult() }
        )
    }

    suspend fun importGeminiApiKeyFromGoogle(activity: android.app.Activity): PasswordManagerResult {
        return authManager.getApiKeyFromPasswordManager(activity, "gemini").fold(
            onSuccess = { key ->
                if (!key.isNullOrEmpty()) {
                    settingsRepository.geminiApiKey = key
                    PasswordManagerResult.Success
                } else {
                    PasswordManagerResult.NoKeyFound
                }
            },
            onFailure = { e -> e.toPasswordManagerResult() }
        )
    }

    private fun Throwable.toPasswordManagerResult(): PasswordManagerResult {
        return when (this) {
            is androidx.credentials.exceptions.GetCredentialCancellationException,
            is androidx.credentials.exceptions.CreateCredentialCancellationException -> {
                PasswordManagerResult.Cancelled
            }
            is androidx.credentials.exceptions.NoCredentialException -> {
                PasswordManagerResult.NoKeyFound
            }
            is androidx.credentials.exceptions.GetCredentialProviderConfigurationException,
            is androidx.credentials.exceptions.CreateCredentialProviderConfigurationException -> {
                PasswordManagerResult.NoManager
            }
            else -> {
                PasswordManagerResult.Error(this.message ?: "Unknown error")
            }
        }
    }
}
