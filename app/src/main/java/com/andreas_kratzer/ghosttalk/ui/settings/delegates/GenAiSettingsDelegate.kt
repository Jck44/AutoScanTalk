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

    // Download Dialog State
    private val _isDownloadDialogVisible = MutableStateFlow(false)
    val isDownloadDialogVisible: StateFlow<Boolean> = _isDownloadDialogVisible.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatusMessage = MutableStateFlow("")
    val downloadStatusMessage: StateFlow<String> = _downloadStatusMessage.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null
    private var totalBytesToDownload: Long = 0

    // Deactivation Dialog State
    private val _isDeactivationDialogVisible = MutableStateFlow(false)
    val isDeactivationDialogVisible: StateFlow<Boolean> = _isDeactivationDialogVisible.asStateFlow()

    // Nano Feature Status State (com.google.mlkit.genai.common.FeatureStatus)
    private val _nanoFeatureStatus = MutableStateFlow<Int?>(null)
    val nanoFeatureStatus: StateFlow<Int?> = _nanoFeatureStatus.asStateFlow()

    fun updateGeminiToolStatus() {
        _geminiToolStatus.value = getGeminiToolStatusUseCase()
    }

    suspend fun performGeminiNanoIntegrityCheck() {
        if (settingsRepository.useLocalGenerativeAi) {
            try {
                val model = com.google.mlkit.genai.prompt.Generation.getClient()
                val status = model.checkStatus()
                _nanoFeatureStatus.value = status
                if (status != com.google.mlkit.genai.common.FeatureStatus.AVAILABLE) {
                    settingsRepository.useLocalGenerativeAi = false
                    _isDeactivationDialogVisible.value = true
                }
            } catch (e: Exception) {
                _nanoFeatureStatus.value = com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
                settingsRepository.useLocalGenerativeAi = false
                _isDeactivationDialogVisible.value = true
            }
        } else {
            // Even if not active, update status to handle UI state
            try {
                val model = com.google.mlkit.genai.prompt.Generation.getClient()
                _nanoFeatureStatus.value = model.checkStatus()
            } catch (e: Exception) {
                _nanoFeatureStatus.value = com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
            }
        }
    }

    fun dismissDeactivationDialog() {
        _isDeactivationDialogVisible.value = false
    }

    fun setGeminiCloudEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
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

    fun setGeminiNanoEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
        if (enabled) {
            scope.launch {
                try {
                    val model = com.google.mlkit.genai.prompt.Generation.getClient()
                    val status = model.checkStatus()
                    _nanoFeatureStatus.value = status
                    when (status) {
                        com.google.mlkit.genai.common.FeatureStatus.AVAILABLE -> {
                            settingsRepository.useLocalGenerativeAi = true
                            updateGeminiToolStatus()
                        }
                        com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE -> {
                            showDownloadDialog()
                        }
                        com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE -> {
                            scope.launch(Dispatchers.Main) {
                                Toast.makeText(application, "Gemini Nano wird auf diesem Gerät nicht unterstützt.", Toast.LENGTH_LONG).show()
                            }
                        }
                        com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING -> {
                            showDownloadDialog() // Progress will be shown if already downloading
                        }
                    }
                } catch (e: Exception) {
                    _nanoFeatureStatus.value = com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(application, "Gemini Nano Status konnte nicht geprüft werden.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            settingsRepository.useLocalGenerativeAi = false
            updateGeminiToolStatus()
        }
    }

    fun showDownloadDialog() {
        _isDownloadDialogVisible.value = true
        _downloadProgress.value = 0f
        _downloadStatusMessage.value = "Modell-Download erforderlich (ca. 1-2 GB)"
    }

    fun dismissDownloadDialog() {
        _isDownloadDialogVisible.value = false
        if (_isDownloading.value) {
            cancelGeminiDownload()
        }
    }

    fun startGeminiDownload(scope: CoroutineScope) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadStatusMessage.value = "Download wird gestartet..."
            
            try {
                val model = com.google.mlkit.genai.prompt.Generation.getClient()
                model.download().collect { status ->
                    when (status) {
                        is com.google.mlkit.genai.common.DownloadStatus.DownloadStarted -> {
                            _downloadStatusMessage.value = "Herunterladen..."
                            totalBytesToDownload = status.bytesToDownload
                        }
                        is com.google.mlkit.genai.common.DownloadStatus.DownloadProgress -> {
                            val progress = if (totalBytesToDownload > 0) {
                                status.totalBytesDownloaded.toFloat() / totalBytesToDownload
                            } else 0f
                            _downloadProgress.value = progress
                        }
                        is com.google.mlkit.genai.common.DownloadStatus.DownloadCompleted -> {
                            _downloadStatusMessage.value = "Download abgeschlossen!"
                            _downloadProgress.value = 1f
                            _isDownloading.value = false
                            settingsRepository.isGeminiEnabled = true
                            settingsRepository.useLocalGenerativeAi = true
                            updateGeminiToolStatus()
                            scope.launch(Dispatchers.Main) {
                                _isDownloadDialogVisible.value = false
                            }
                        }
                        is com.google.mlkit.genai.common.DownloadStatus.DownloadFailed -> {
                            _downloadStatusMessage.value = "Download fehlgeschlagen: ${status.e.message}"
                            _isDownloading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                _downloadStatusMessage.value = "Fehler: ${e.message}"
                _isDownloading.value = false
            }
        }
    }

    fun cancelGeminiDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _isDownloading.value = false
        _isDownloadDialogVisible.value = false
        settingsRepository.useLocalGenerativeAi = false
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
