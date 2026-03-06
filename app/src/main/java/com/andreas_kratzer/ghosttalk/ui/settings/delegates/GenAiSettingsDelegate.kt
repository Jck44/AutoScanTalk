package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.auth.SignInUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.ActivateGeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GetGeminiToolStatusUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.HandleGenAiExceptionUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.TestGeminiNanoUseCase
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
    private val googleAuthManager: GoogleAuthManager,
    private val getGeminiToolStatusUseCase: GetGeminiToolStatusUseCase,
    private val activateGeminiUseCase: ActivateGeminiUseCase,
    private val handleGenAiExceptionUseCase: HandleGenAiExceptionUseCase,
    private val testGeminiNanoUseCase: TestGeminiNanoUseCase,
    private val signInUseCase: SignInUseCase
) {
    private val _geminiToolStatus = MutableStateFlow<Map<String, GeminiUseCase.ToolStatus>>(emptyMap())
    val geminiToolStatus: StateFlow<Map<String, GeminiUseCase.ToolStatus>> = _geminiToolStatus.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<android.content.Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    fun updateGeminiToolStatus() {
        _geminiToolStatus.value = getGeminiToolStatusUseCase()
    }

    fun setGeminiEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
        if (enabled && googleAuthManager.userEmail.value == null) {
            val activity = findActivity(context) ?: return
            scope.launch {
                val result = signInUseCase.execute(activity)
                if (result) {
                    settingsRepository.isGeminiEnabled = true
                    updateGeminiToolStatus()
                }
            }
        } else {
            settingsRepository.isGeminiEnabled = enabled
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

    fun testGeminiNano(context: Context, scope: CoroutineScope) {
        scope.launch {
            testGeminiNanoUseCase.execute(
                onResponse = { response ->
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Gemini Nano bereit: $response", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { e ->
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Gemini Nano Fehler: ${e.message}", Toast.LENGTH_LONG).show()
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
}
