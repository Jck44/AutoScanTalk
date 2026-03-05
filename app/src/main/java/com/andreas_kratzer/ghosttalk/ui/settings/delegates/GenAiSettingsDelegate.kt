package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.R
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenAiSettingsDelegate @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val googleAuthManager: com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager,
    private val geminiUseCaseFactory: GeminiUseCaseFactory,
    private val localIntentRouter: LocalIntentRouter
) {
    private val _geminiToolStatus = MutableStateFlow<Map<String, GeminiUseCase.ToolStatus>>(emptyMap())
    val geminiToolStatus: StateFlow<Map<String, GeminiUseCase.ToolStatus>> = _geminiToolStatus.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<android.content.Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    fun updateGeminiToolStatus() {
        val gemini = geminiUseCaseFactory.create { null }
        _geminiToolStatus.value = gemini.getToolStatus(googleAuthManager.userEmail.value != null)
    }

    fun activateGemini(context: Context, scope: CoroutineScope) {
        val gemini = geminiUseCaseFactory.create { googleAuthManager.getGoogleCredential()?.getToken() }
        scope.launch {
            try {
                gemini.generateResponse("Ping")
                settingsRepository.isGeminiEnabled = true
                updateGeminiToolStatus()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, application.getString(R.string.settings_gemini_activation_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                handleGenAiException(e)
            }
        }
    }

    fun testGeminiNano(context: Context, scope: CoroutineScope) {
        scope.launch {
            try {
                localIntentRouter.routeIntent("Ping") { response ->
                    // Ensure UI updates happen on the Main thread
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Gemini Nano bereit: $response", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gemini Nano Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun handleGenAiException(e: Exception) {
        var cause: Throwable? = e
        while (cause != null) {
            when (cause) {
                is UserRecoverableAuthIOException -> cause.intent?.let { _authIntentFlow.emit(it) }
                is UserRecoverableAuthException -> cause.intent?.let { _authIntentFlow.emit(it) }
            }
            cause = cause.cause
        }
    }
}
