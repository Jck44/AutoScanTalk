package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.domain.genai.ActivateGeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GetGeminiToolStatusUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.HandleGenAiExceptionUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.TestGeminiNanoUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
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
    private val getGeminiToolStatusUseCase: GetGeminiToolStatusUseCase,
    private val activateGeminiUseCase: ActivateGeminiUseCase,
    private val handleGenAiExceptionUseCase: HandleGenAiExceptionUseCase,
    private val testGeminiNanoUseCase: TestGeminiNanoUseCase
) {
    private val _geminiToolStatus = MutableStateFlow<Map<String, GeminiUseCase.ToolStatus>>(emptyMap())
    val geminiToolStatus: StateFlow<Map<String, GeminiUseCase.ToolStatus>> = _geminiToolStatus.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<android.content.Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    fun updateGeminiToolStatus() {
        _geminiToolStatus.value = getGeminiToolStatusUseCase()
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
}
