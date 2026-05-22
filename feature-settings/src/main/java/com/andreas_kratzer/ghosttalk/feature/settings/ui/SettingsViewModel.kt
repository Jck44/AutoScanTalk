package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
import com.andreas_kratzer.ghosttalk.core.tts.CachedAudioItem
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.domain.DeleteBookUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActionLogLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActiveBookNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    val securityManager: SecurityManager,
    getPagesUseCase: GetPagesUseCase,
    val ttsDelegate: TtsSettingsDelegate,
    val scanningDelegate: ScanningSettingsDelegate,
    val cloudSyncDelegate: CloudSyncSettingsDelegate,
    val genAiDelegate: GenAiSettingsDelegate,
    val experimentalDelegate: ExperimentalSettingsDelegate,
    private val updateActiveBookNameUseCase: UpdateActiveBookNameUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val updateActionLogLimitUseCase: UpdateActionLogLimitUseCase,
    private val importExportManager: PageImportExportProvider,
    private val hueManager: PhilipsHueManager,
    private val ttsHelper: TextToSpeechHelper,
    private val audioCacheRepository: AudioCacheRepository,
    private val pageRepository: PageRepository,
    private val syncLogProvider: SyncLogProvider
) : AndroidViewModel(application) {

    private val _activeBookId = settingsRepository.activeBookIdFlow
    val allPages: StateFlow<List<Page>> = getPagesUseCase.execute(_activeBookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeBook: StateFlow<Book?> = settingsRepository.activeBookIdFlow
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(null) else bookRepository.getBookByIdFlow(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Observable State from Repository ---
    val availableLanguages = ttsDelegate.availableLanguages
    val availableVoices = ttsDelegate.availableVoices
    val availableAudioDevices = ttsDelegate.availableAudioDevices
    val cachedAudioDevices = ttsDelegate.cachedAudioDevices
    
    val selectedLanguageTag = settingsRepository.ttsLanguageFlow
    val selectedVoiceName = settingsRepository.ttsVoiceNameFlow
    
    val autoStartScanning = settingsRepository.autoStartScanningFlow
    val scanDelayMillis = settingsRepository.scanDelayFlow
    val resumeScanningFromStart = settingsRepository.resumeScanningFromStartFlow
    val defaultScanPattern = settingsRepository.defaultScanPatternFlow
    val holdingTimeMillis = settingsRepository.holdingTimeMillisFlow
    val bluetoothDelay = settingsRepository.bluetoothDelayFlow
    
    val defaultStartPageId = settingsRepository.defaultStartPageIdFlow
    val selectedTtsAudioDeviceAddress = settingsRepository.ttsAudioDeviceAddressFlow
    val selectedCuesAudioDeviceAddress = settingsRepository.cuesAudioDeviceAddressFlow
    val recordingAudioSource = settingsRepository.recordingAudioSourceFlow
    
    val persistActionLogs = settingsRepository.persistActionLogsFlow
    val switchActivationKey = settingsRepository.switchActivationKeyFlow
    val volumeKeysActivate = settingsRepository.volumeKeysActivateFlow
    val showTestButtons = settingsRepository.showTestButtonsFlow
    val showPageIdInLog = settingsRepository.showPageIdInLogFlow
    
    val isCloudSyncEnabled = settingsRepository.isCloudSyncEnabledFlow
    val syncMode = settingsRepository.syncModeFlow
    val lastSuccessfulSyncTime = settingsRepository.lastSuccessfulSyncTimeFlow
    val syncIntervalMinutes = settingsRepository.syncIntervalMinutesFlow
    val googleHomeProjectId = settingsRepository.googleHomeProjectIdFlow
    val hueBridgeIp = settingsRepository.hueBridgeIpFlow
    val hueUsername = settingsRepository.hueUsernameFlow
    val hueAccessToken = settingsRepository.hueAccessTokenFlow
    val hueClientId = settingsRepository.hueClientIdFlow
    val hueClientSecret = settingsRepository.hueClientSecretFlow
    val isSyncing = cloudSyncDelegate.isSyncing
    val userEmail = cloudSyncDelegate.userEmail
    
    val availableBackups = cloudSyncDelegate.availableBackups
    val showBackupSelectionDialog = cloudSyncDelegate.showBackupSelectionDialog
    val syncLogs = cloudSyncDelegate.syncLogs
    
    val isGeminiEnabled = settingsRepository.isGeminiEnabledFlow
    val useLocalGenerativeAi = settingsRepository.useLocalGenerativeAiFlow
    val geminiToolStatus = genAiDelegate.geminiToolStatus
    
    val isSmartPredictionEnabled = settingsRepository.isSmartPredictionEnabledFlow
    
    val isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabledFlow
    val monitoredNotificationApps = settingsRepository.monitoredNotificationAppsFlow
    
    val selectedAppLanguage = settingsRepository.appLanguageFlow
    val themeMode = settingsRepository.themeModeFlow
    val buttonHistory = buttonUsageRepository.buttonHistory

    val keepScreenOnUserMode = settingsRepository.keepScreenOnUserModeFlow
    val userModeScreenBehavior = settingsRepository.userModeScreenBehaviorFlow
    val geminiTimeout = settingsRepository.geminiTimeoutFlow
    val geminiRedoPrediction = settingsRepository.geminiRedoPredictionFlow

    val securityPin = settingsRepository.securityPinFlow
    val securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutesFlow
    val isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletionFlow
    val isBiometricEnabled = settingsRepository.isBiometricEnabledFlow
    val isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEditFlow
    val isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettingsFlow
    val startupBehavior = settingsRepository.startupBehaviorFlow
    val logIgnoredActions = settingsRepository.logIgnoredActionsFlow
    val logStopActions = settingsRepository.logStopActionsFlow
    
    val limitScanCycles = settingsRepository.limitScanCyclesFlow
    val scanCycleLimit = settingsRepository.scanCycleLimitFlow
    val actionLogLimit = settingsRepository.actionLogLimitFlow
    val forceSoftKeyboard = settingsRepository.forceSoftKeyboardFlow
    val ttsEngine = settingsRepository.ttsEngineFlow
    val elevenLabsApiKey = settingsRepository.elevenLabsApiKeyFlow
    val elevenLabsModel = settingsRepository.elevenLabsModelFlow
    val elevenLabsStability = settingsRepository.elevenLabsStabilityFlow
    val elevenLabsSimilarityBoost = settingsRepository.elevenLabsSimilarityBoostFlow
    
    private val _showActionHistoryDialog = MutableStateFlow(false)
    val showActionHistoryDialog = _showActionHistoryDialog.asStateFlow()

    private val _showUsageStatsDialog = MutableStateFlow(false)
    val showUsageStatsDialog = _showUsageStatsDialog.asStateFlow()

    private val _showPrefetchDialog = MutableStateFlow(false)
    val showPrefetchDialog = _showPrefetchDialog.asStateFlow()

    private val _topButtonUsage = MutableStateFlow<List<com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat>>(emptyList())
    val topButtonUsage = _topButtonUsage.asStateFlow()

    // --- TTS Prefetch State ---
    private val _selectedPagesForPrefetch = MutableStateFlow<Set<String>>(emptySet())
    val selectedPagesForPrefetch = _selectedPagesForPrefetch.asStateFlow()

    private val _isPrefetching = MutableStateFlow(false)
    val isPrefetching = _isPrefetching.asStateFlow()

    private val _prefetchProgress = MutableStateFlow(0f)
    val prefetchProgress = _prefetchProgress.asStateFlow()

    private val _prefetchCurrentCount = MutableStateFlow(0)
    val prefetchCurrentCount = _prefetchCurrentCount.asStateFlow()

    private val _prefetchTotalCount = MutableStateFlow(0)
    val prefetchTotalCount = _prefetchTotalCount.asStateFlow()

    private val _currentPrefetchText = MutableStateFlow<String?>(null)
    val currentPrefetchText = _currentPrefetchText.asStateFlow()

    private val _prefetchStats = MutableStateFlow<com.andreas_kratzer.ghosttalk.core.model.PrefetchStats?>(null)
    val prefetchStats = _prefetchStats.asStateFlow()

    // --- Backup & Restore State ---
    private val _isBackupRestoreRunning = MutableStateFlow(false)
    val isBackupRestoreRunning = _isBackupRestoreRunning.asStateFlow()

    private val _backupRestoreProgress = MutableStateFlow(0f)
    val backupRestoreProgress = _backupRestoreProgress.asStateFlow()

    private val _backupRestoreStatus = MutableStateFlow<String?>(null)
    val backupRestoreStatus = _backupRestoreStatus.asStateFlow()

    private val _manualUpdateCheckTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val manualUpdateCheckTrigger = _manualUpdateCheckTrigger.asSharedFlow()

    private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus?>(null)
    val updateCheckStatus = _updateCheckStatus.asStateFlow()
    
    private var prefetchJob: kotlinx.coroutines.Job? = null

    init {
        ttsDelegate.initialize(viewModelScope) { original, fallback ->
            val message = if (fallback != null) {
                "Stimme $original nicht verfügbar. Fallback auf $fallback."
            } else {
                "Stimme $original nicht verfügbar. Fallback auf System-Standard."
            }
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
        genAiDelegate.updateGeminiToolStatus()
        viewModelScope.launch {
            genAiDelegate.performGeminiNanoIntegrityCheck()
        }
        initializeDefaultMessagingAppsIfNeeded()
    }
    
    private val _navigationEvent = kotlinx.coroutines.flow.MutableSharedFlow<SettingsNavigationEvent>()
    val navigationEvents = _navigationEvent.asSharedFlow()

    private val _selectedHistoryItem = kotlinx.coroutines.flow.MutableStateFlow<ButtonUsageRepository.ButtonUsageEvent?>(null)
    val selectedHistoryItem = _selectedHistoryItem.asStateFlow()

    sealed class SettingsNavigationEvent {
        data class EditButton(val pageId: String, val buttonId: String) : SettingsNavigationEvent()
        data class JumpToPage(val pageId: String) : SettingsNavigationEvent()
    }

    sealed class UpdateCheckStatus {
        object Checking : UpdateCheckStatus()
        object UpToDate : UpdateCheckStatus()
        data class Error(val message: String) : UpdateCheckStatus()
    }
    
    val isBiometricSupported: Boolean = securityManager.isBiometricSupported(application)

    val authIntentFlow = kotlinx.coroutines.flow.merge(
        cloudSyncDelegate.authIntentFlow,
        genAiDelegate.authIntentFlow
    )
    
    val signInErrorMessage = cloudSyncDelegate.signInErrorMessage

    /* init block moved up */

    // --- Delegation Methods (UI Actions) ---
    fun refresh() {
        ttsDelegate.loadAvailableLanguages()
        ttsDelegate.loadAvailableAudioDevices()
        genAiDelegate.updateGeminiToolStatus()
        viewModelScope.launch {
            genAiDelegate.performGeminiNanoIntegrityCheck()
        }
        refreshTopButtonUsage()
    }

    fun setTtsLanguage(tag: String) = ttsDelegate.setTtsLanguage(tag)
    fun setTtsVoice(name: String?) = ttsDelegate.setTtsVoice(name)
    fun setTtsAudioDevice(addr: String?) = ttsDelegate.setTtsAudioDevice(addr)
    fun setCuesAudioDevice(addr: String?) = ttsDelegate.setCuesAudioDevice(addr)
    fun setRecordingAudioSource(source: Int) {
        settingsRepository.recordingAudioSource = source
    }
    fun getResolvedDeviceName(addr: String?) = ttsDelegate.getResolvedDeviceName(addr)

    fun setAutoStartScanning(enabled: Boolean) = scanningDelegate.setAutoStartScanning(enabled)
    fun setScanDelayInput(input: String) = scanningDelegate.setScanDelayInput(input)
    fun setResumeScanningFromStart(b: Boolean) = scanningDelegate.setResumeScanningFromStart(b)
    fun setDefaultScanPattern(p: String) = scanningDelegate.setDefaultScanPattern(p)
    fun setHoldingTimeInput(i: String) = scanningDelegate.setHoldingTimeInput(i)
    fun setBluetoothDelay(d: String) = scanningDelegate.setBluetoothDelay(d)

    fun signIn(ctx: Context) = cloudSyncDelegate.signIn(ctx, viewModelScope)
    
    fun signOut() {
        cloudSyncDelegate.signOut(viewModelScope)
        // Disable cloud-dependent features on sign out
        settingsRepository.isCloudSyncEnabled = false
        settingsRepository.isGeminiEnabled = false
        genAiDelegate.updateGeminiToolStatus()
    }

    fun setCloudSyncEnabled(ctx: Context, e: Boolean) = cloudSyncDelegate.setCloudSyncEnabled(ctx, e, viewModelScope)
    
    fun syncNow() {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        val modeStr = settingsRepository.syncMode
        val mode = try {
            com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.valueOf(modeStr)
        } catch (_: Exception) {
            com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.TWO_WAY
        }
        cloudSyncDelegate.performManualSync(
            mode = mode,
            scope = viewModelScope,
            onProgress = { p, s -> handleCloudProgress(p, s) },
            onComplete = { finishBackupRestoreProgress() }
        )
    }
    
    fun backupNow() {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.BACKUP_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> handleCloudProgress(p, s) },
            onComplete = { finishBackupRestoreProgress() }
        )
    }
    
    fun restoreNow() {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.RESTORE_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> handleCloudProgress(p, s) },
            onComplete = { finishBackupRestoreProgress() }
        )
    }
    
    fun fetchAvailableBackupsForImport() = cloudSyncDelegate.fetchAvailableBackupsForImport(viewModelScope)
    
    fun importCloudBackup(backupInfo: com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        cloudSyncDelegate.importCloudBackup(backupInfo, viewModelScope, { p, s -> 
            handleCloudProgress(p, s)
        }) { _ ->
            // On completion, dialog is hidden by the CloudSyncSettingsDelegate's isSyncing flow logic if we wanted, 
            // but we'll use a finally block in the delegate. 
            // Actually, we'll manually unset isBackupRestoreRunning here after a delay.
            viewModelScope.launch {
                delay(1000)
                _isBackupRestoreRunning.value = false
            }
        }
    }
    fun dismissBackupSelectionDialog() = cloudSyncDelegate.dismissBackupSelectionDialog()
    
    fun loadSyncLogs() = cloudSyncDelegate.loadSyncLogs(viewModelScope)
    fun clearSyncLogs() = cloudSyncDelegate.clearSyncLogs(viewModelScope)
    
    fun setSyncMode(m: String) { settingsRepository.syncMode = m }
    fun setSyncIntervalMinutes(minutes: Long) { settingsRepository.syncIntervalMinutes = minutes }
    fun setGoogleHomeProjectId(id: String) { settingsRepository.googleHomeProjectId = id }
    fun setHueBridgeIp(ip: String) { settingsRepository.hueBridgeIp = ip }
    fun setHueUsername(username: String) { settingsRepository.hueUsername = username }
    fun setHueAccessToken(token: String) { settingsRepository.hueAccessToken = token }
    fun setHueClientId(id: String) { settingsRepository.hueClientId = id }
    fun setHueClientSecret(secret: String) { settingsRepository.hueClientSecret = secret }

    fun setTtsEngine(engine: String?) { 
        ttsDelegate.setTtsEngine(engine)
    }
    fun setElevenLabsApiKey(key: String) = ttsDelegate.setElevenLabsApiKey(key)

    fun saveApiKeyToGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = ttsDelegate.saveApiKeyToGoogle(activity)
            handlePasswordManagerResult(result, isImport = false)
        }
    }

    fun importApiKeyFromGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = ttsDelegate.importApiKeyFromGoogle(activity)
            handlePasswordManagerResult(result, isImport = true)
        }
    }

    private fun handlePasswordManagerResult(
        result: com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult,
        isImport: Boolean
    ) {
        val message = when (result) {
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Success -> {
                if (isImport) {
                    application.getString(R.string.elevenlabs_api_key_imported_google)
                } else {
                    application.getString(R.string.elevenlabs_api_key_saved_google)
                }
            }
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoKeyFound -> 
                application.getString(R.string.elevenlabs_api_key_not_found_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoManager -> 
                application.getString(R.string.elevenlabs_api_key_no_manager_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Cancelled -> 
                null // Don't show anything on cancel
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Error -> 
                application.getString(R.string.elevenlabs_api_key_error_google, result.message)
        }

        if (message != null) {
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
    }

    fun setElevenLabsModel(model: String) { ttsDelegate.setElevenLabsModel(model) }
    fun setElevenLabsStability(value: Float) = ttsDelegate.setElevenLabsStability(value)
    fun setElevenLabsSimilarityBoost(value: Float) = ttsDelegate.setElevenLabsSimilarityBoost(value)

    fun testElevenLabsConnection() {
        viewModelScope.launch {
            val currentEngine = settingsRepository.ttsEngine
            // Temporary switch engine to elevenlabs for the test
            settingsRepository.ttsEngine = "elevenlabs"
            
            withContext(Dispatchers.Main) {
                Toast.makeText(application, "ElevenLabs Verbindung wird getestet...", Toast.LENGTH_SHORT).show()
            }
            
            // Give the helper time to switch the provider
            // The engine flow collection happens in TextToSpeechHelper's scope
            delay(800)
            
            val sampleText = application.getString(R.string.settings_elevenlabs_test_sample_text)
            ttsHelper.speak(sampleText, onDone = {
                // Restore engine
                settingsRepository.ttsEngine = currentEngine
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, application.getString(R.string.settings_elevenlabs_test_success), Toast.LENGTH_LONG).show()
                    }
                }
            }, onError = { error ->
                // Restore engine
                settingsRepository.ttsEngine = currentEngine
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        val message = application.getString(R.string.settings_elevenlabs_test_failure, error)
                        Toast.makeText(application, message, Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    fun playVoicePreview() {
        viewModelScope.launch {
            val sampleText = application.getString(R.string.settings_voice_preview_sample_text)
            ttsHelper.speak(sampleText)
        }
    }


    fun discoverHueBridges() {
        viewModelScope.launch {
            Toast.makeText(application, "Suche nach Hue Bridges...", Toast.LENGTH_SHORT).show()
            val bridges = hueManager.discoverBridges()
            if (bridges.isNotEmpty()) {
                settingsRepository.hueBridgeIp = bridges.first()
                Toast.makeText(application, "Bridge gefunden: ${bridges.first()}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(application, "Keine Hue Bridge im Netzwerk gefunden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startHueOAuth() {
        if (hueClientId.value.isBlank()) {
            Toast.makeText(application, "Bitte zuerst die Client ID eintragen.", Toast.LENGTH_LONG).show()
            return
        }

        val authUrl = "https://api.meethue.com/v2/oauth2/authorize" +
                "?client_id=${hueClientId}" +
                "&response_type=code" +
                "&state=hue_auth_state" // In a real app, use a random state

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUrl.toUri()).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun setGeminiEnabled(ctx: Context, e: Boolean) {
        genAiDelegate.setGeminiCloudEnabled(ctx, e, viewModelScope)
    }
    
    fun setGeminiNanoEnabled(ctx: Context, e: Boolean) {
        genAiDelegate.setGeminiNanoEnabled(ctx, e, viewModelScope)
    }

    fun activateGemini(ctx: Context) = genAiDelegate.activateGemini(ctx, viewModelScope)
    fun testGeminiNano(ctx: Context) = genAiDelegate.testGeminiNano(ctx, viewModelScope)
    
    val isDownloadDialogVisible = genAiDelegate.isDownloadDialogVisible
    val downloadProgress = genAiDelegate.downloadProgress
    val downloadStatusMessage = genAiDelegate.downloadStatusMessage
    val isDownloading = genAiDelegate.isDownloading
    val nanoFeatureStatus = genAiDelegate.nanoFeatureStatus

    fun startGeminiDownload() = genAiDelegate.startGeminiDownload(viewModelScope)
    fun dismissDownloadDialog() = genAiDelegate.dismissDownloadDialog()

    val isDeactivationDialogVisible = genAiDelegate.isDeactivationDialogVisible
    fun dismissDeactivationDialog() = genAiDelegate.dismissDeactivationDialog()

    fun setUseLocalGenerativeAi(e: Boolean, ctx: Context? = null) {
        if (e && ctx != null) {
            genAiDelegate.setGeminiNanoEnabled(ctx, e, viewModelScope)
        } else {
            settingsRepository.useLocalGenerativeAi = e
        }
    }

    fun setPersistActionLogs(e: Boolean) { settingsRepository.persistActionLogs = e }
    fun setSwitchActivationKey(k: String) { settingsRepository.switchActivationKey = k }
    fun setVolumeKeysActivate(e: Boolean) { settingsRepository.volumeKeysActivate = e }
    fun setShowTestButtons(e: Boolean) { settingsRepository.showTestButtons = e }
    fun setShowPageIdInLog(e: Boolean) { settingsRepository.showPageIdInLog = e }
    fun setSmartPredictionEnabled(e: Boolean) = experimentalDelegate.setSmartPredictionEnabled(e)
    fun setDefaultStartPageId(id: String?) { settingsRepository.defaultStartPageId = id }

    fun setAppLanguage(code: String?) {
        val finalCode = if (code == "default") null else code
        settingsRepository.appLanguage = finalCode
        val appLocale = if (finalCode == null) androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(finalCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
    }
    fun setThemeMode(m: String) { settingsRepository.themeMode = m }

    fun setNotificationReadingEnabled(e: Boolean) { settingsRepository.isNotificationReadingEnabled = e }
    fun toggleMonitoredNotificationApp(pkg: String, e: Boolean) {
        val current = settingsRepository.monitoredNotificationApps.toMutableSet()
        if (e) current.add(pkg) else current.remove(pkg)
        settingsRepository.monitoredNotificationApps = current
    }
    fun setMonitoredNotificationApps(apps: Set<String>) {
        settingsRepository.monitoredNotificationApps = apps
    }

    fun clearButtonUsageStats(bookId: String) {
        viewModelScope.launch { 
            buttonUsageRepository.clearStats(bookId)
            refreshTopButtonUsage()
        }
    }

    fun setShowActionHistoryDialog(show: Boolean) {
        _showActionHistoryDialog.value = show
    }

    fun setShowUsageStatsDialog(show: Boolean) {
        _showUsageStatsDialog.value = show
        if (show) {
            refreshTopButtonUsage()
        }
    }

    fun setShowPrefetchDialog(show: Boolean) {
        _showPrefetchDialog.value = show
    }

    fun refreshTopButtonUsage() {
        viewModelScope.launch {
            val stats = buttonUsageRepository.getGroupedUsageStats(activeBookId)
            _topButtonUsage.value = stats.take(50) // Show top 50 groups
        }
    }

    fun checkManualUpdate() {
        viewModelScope.launch {
            _updateCheckStatus.value = UpdateCheckStatus.Checking
            _manualUpdateCheckTrigger.emit(Unit)
        }
    }

    fun setUpdateCheckStatus(status: UpdateCheckStatus?) {
        _updateCheckStatus.value = status
    }

    fun setKeepScreenOnUserMode(e: Boolean) { settingsRepository.keepScreenOnUserMode = e }
    fun setUserModeScreenBehavior(m: String) { settingsRepository.userModeScreenBehavior = m }
    fun setGeminiTimeoutInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.geminiTimeout = it }
    }
    fun setGeminiRedoPrediction(e: Boolean) { settingsRepository.geminiRedoPrediction = e }

    fun setLimitScanCycles(e: Boolean) = scanningDelegate.setLimitScanCycles(e)
    fun setScanCycleLimitInput(input: String) = scanningDelegate.setScanCycleLimitInput(input)
    fun setActionLogLimitInput(input: String) {
        updateActionLogLimitUseCase(input)
    }

    fun setSecurityPin(pin: String) {
        settingsRepository.securityPin = pin
    }

    fun clearSecurityPin() {
        securityManager.clearPin()
    }

    fun setSecurityPinTimeoutMinutes(minutes: Long) {
        settingsRepository.securityPinTimeoutMinutes = minutes
    }

    fun setPinRequiredForDeletion(required: Boolean) {
        settingsRepository.isPinRequiredForDeletion = required
    }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.isBiometricEnabled = enabled
    }

    fun setSecurityRequiredForEdit(required: Boolean) {
        settingsRepository.isSecurityRequiredForEdit = required
    }

    fun setSecurityRequiredForSettings(required: Boolean) {
        settingsRepository.isSecurityRequiredForSettings = required
    }

    fun setStartupBehavior(behavior: String) {
        settingsRepository.startupBehavior = behavior
    }

    fun setForceSoftKeyboard(enabled: Boolean) {
        settingsRepository.forceSoftKeyboard = enabled
    }

    fun lock() {
        securityManager.lock()
    }

    val weatherCacheTimeout = settingsRepository.weatherCacheTimeoutFlow
    fun setWeatherCacheTimeoutInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.weatherCacheTimeout = it }
    }

    val activeBookId: String
        get() = settingsRepository.activeBookId

    override fun onCleared() {
        super.onCleared()
        // Aggressive cache cleanup removed to allow offline device selection
    }

    suspend fun exportLocalBackup(): String {
        return importExportManager.exportBookToJson(activeBookId)
    }

    suspend fun exportLocalBackupZip(outputStream: java.io.OutputStream) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = application.getString(R.string.backup_progress_exporting)
        
        try {
            importExportManager.exportBookToZip(activeBookId, outputStream) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Exporting database..." -> application.getString(R.string.backup_progress_exporting)
                    status.startsWith("Compressing audio:") -> application.getString(R.string.backup_progress_compressing, status.substringAfter(": "))
                    status == "Backup complete." -> application.getString(R.string.backup_progress_complete)
                    else -> status
                }
            }
            syncLogProvider.addLogEntry("Lokale Sicherung erstellt", activeBookId, activeBook.value?.name)
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Lokale Sicherung fehlgeschlagen: ${e.message}", activeBookId, activeBook.value?.name, isError = true)
            throw e
        } finally {
            delay(1000) // Show complete message briefly
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    suspend fun importLocalBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = application.getString(R.string.restore_progress_importing)

        try {
            val result = importExportManager.importFromZip(
                inputStream = inputStream,
                bookId = activeBookId,
                regenerateIds = false,
                restoreSyncSettings = false
            ) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Importing data..." -> application.getString(R.string.restore_progress_importing)
                    status.startsWith("Extracting:") -> application.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
                    status == "Import complete." -> application.getString(R.string.restore_progress_complete)
                    else -> status
                }
            }
            result.onSuccess { 
                syncLogProvider.addLogEntry("Lokale Wiederherstellung (ZIP) erfolgreich", activeBookId, activeBook.value?.name)
                onSuccess() 
            }
                .onFailure { e -> 
                    syncLogProvider.addLogEntry("Lokale Wiederherstellung (ZIP) fehlgeschlagen: ${e.message}", activeBookId, activeBook.value?.name, isError = true)
                    onError("Fehler beim ZIP-Import: ${e.message}") 
                }
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Fehler bei lokaler Wiederherstellung (ZIP): ${e.message}", activeBookId, activeBook.value?.name, isError = true)
            throw e
        } finally {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    suspend fun importLocalBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val importBookId = importExportManager.extractBookIdFromJson(json)
        
        if (importBookId != activeBookId) {
            onError("Fehler: Buch-IDs stimmen nicht überein. Dieses Backup gehört zu einem anderen Buch.")
            return
        }

        val result = importExportManager.importFromJson(
            jsonString = json,
            bookId = activeBookId,
            regenerateIds = false, // Preserve IDs for matching book
            restoreSyncSettings = false
        )
        result.onSuccess { 
            syncLogProvider.addLogEntry("Lokale Wiederherstellung (JSON) erfolgreich", activeBookId, activeBook.value?.name)
            onSuccess() 
        }
            .onFailure { e -> 
                syncLogProvider.addLogEntry("Lokale Wiederherstellung (JSON) fehlgeschlagen: ${e.message}", activeBookId, activeBook.value?.name, isError = true)
                onError("Fehler beim Import: ${e.message}") 
            }
    }

    suspend fun importGlobalManualBackup(json: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val result = importExportManager.importCloudBackup(json, null)
        result.onSuccess { bookId -> 
            syncLogProvider.addLogEntry("Globaler Import (JSON) erfolgreich", bookId, null)
            onSuccess(bookId) 
        }
            .onFailure { e -> 
                syncLogProvider.addLogEntry("Globaler Import (JSON) fehlgeschlagen: ${e.message}", null, null, isError = true)
                onError("Fehler beim globalen Import: ${e.message}") 
            }
    }

    suspend fun importGlobalManualBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = application.getString(R.string.restore_progress_importing)

        try {
            val result = importExportManager.importCloudBackupFromZip(
                inputStream = inputStream,
                cloudFileId = null
            ) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Importing data..." || status == "Importing book..." -> application.getString(R.string.restore_progress_importing)
                    status.startsWith("Extracting:") -> application.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
                    status == "Import complete." -> application.getString(R.string.restore_progress_complete)
                    else -> status
                }
            }
            result.onSuccess { bookId -> 
                syncLogProvider.addLogEntry("Globaler Import (ZIP) erfolgreich", bookId, null)
                onSuccess(bookId) 
            }
                .onFailure { e -> 
                    syncLogProvider.addLogEntry("Globaler Import (ZIP) fehlgeschlagen: ${e.message}", null, null, isError = true)
                    onError("Fehler beim globalen ZIP-Import: ${e.message}") 
                }
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Fehler beim globalen ZIP-Import: ${e.message}", null, null, isError = true)
            throw e
        } finally {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    fun onEditButtonFromHistory(pageId: String, buttonId: String) {
        viewModelScope.launch {
            _showActionHistoryDialog.value = false
            _showUsageStatsDialog.value = false
            _navigationEvent.emit(SettingsNavigationEvent.EditButton(pageId, buttonId))
        }
    }

    fun onJumpToPageFromHistory(pageId: String) {
        viewModelScope.launch {
            _showActionHistoryDialog.value = false
            _showUsageStatsDialog.value = false
            _navigationEvent.emit(SettingsNavigationEvent.JumpToPage(pageId))
        }
    }

    fun onDeleteHistoryItem(event: ButtonUsageRepository.ButtonUsageEvent) {
        viewModelScope.launch {
            buttonUsageRepository.deleteUsageEvent(event.timestamp)
        }
    }

    fun onShowHistoryDetail(event: ButtonUsageRepository.ButtonUsageEvent?) {
        _selectedHistoryItem.value = event
    }

    fun updateActiveBookName(newName: String) {
        viewModelScope.launch {
            updateActiveBookNameUseCase.execute(newName)
        }
    }

    fun deleteActiveBook(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = deleteBookUseCase.execute()
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    onDeleted()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Fehler beim Löschen des Buches", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setLogIgnoredActionsInput(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.logIgnoredActions = enabled
        }
    }

    fun setLogStopActionsInput(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.logStopActions = enabled
        }
    }

    // --- Audio Cache Management ---
    private val _audioCacheItems = MutableStateFlow<List<CachedAudioItem>>(emptyList())
    val audioCacheItems: StateFlow<List<CachedAudioItem>> = _audioCacheItems.asStateFlow()

    fun loadAudioCache() {
        viewModelScope.launch {
            _audioCacheItems.value = audioCacheRepository.getCachedAudios()
        }
    }

    fun deleteAudioCacheItem(item: CachedAudioItem) {
        viewModelScope.launch {
            if (audioCacheRepository.deleteFile(item.file)) {
                loadAudioCache()
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            if (audioCacheRepository.deleteAll()) {
                loadAudioCache()
            }
        }
    }

    // --- TTS Prefetch Actions ---
    fun togglePageSelectionForPrefetch(pageId: String) {
        val current = _selectedPagesForPrefetch.value.toMutableSet()
        if (current.contains(pageId)) {
            current.remove(pageId)
        } else {
            current.add(pageId)
        }
        _selectedPagesForPrefetch.value = current
        _prefetchStats.value = null // Reset stats when selection changes
    }

    fun selectAllPagesForPrefetch(pages: List<Page>) {
        _selectedPagesForPrefetch.value = pages.map { it.id }.toSet()
        _prefetchStats.value = null
    }

    fun deselectAllPagesForPrefetch() {
        _selectedPagesForPrefetch.value = emptySet()
        _prefetchStats.value = null
    }

    fun calculatePrefetchStats(allPages: List<Page>): com.andreas_kratzer.ghosttalk.core.model.PrefetchStats {
        val selectedIds = _selectedPagesForPrefetch.value
        val selectedPages = allPages.filter { it.id in selectedIds }
        
        val allButtons = selectedPages.flatMap { it.buttonConfigs.filterNotNull() }
        val textsToSpeak = allButtons.mapNotNull { config ->
            val text = config.spokenText?.takeIf { it.isNotBlank() }
                ?: if (config.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction) config.label else null
            
            text?.takeIf { it.isNotBlank() }
        }

        val totalButtons = textsToSpeak.size
        val uniqueTexts = textsToSpeak.distinct()
        val uniqueStringsCount = uniqueTexts.size
        val duplicateCount = totalButtons - uniqueStringsCount
        
        val totalWords = uniqueTexts.sumOf { it.split(Regex("\\s+")).filter { s -> s.isNotBlank() }.size }
        val totalCharacters = uniqueTexts.sumOf { it.length }
        
        val alreadyCached = uniqueTexts.count { ttsHelper.isCached(it) }

        val stats = com.andreas_kratzer.ghosttalk.core.model.PrefetchStats(
            totalButtons = totalButtons,
            uniqueStrings = uniqueStringsCount,
            duplicateStrings = duplicateCount,
            totalWords = totalWords,
            totalCharacters = totalCharacters,
            alreadyCached = alreadyCached
        )
        
        _prefetchStats.value = stats
        return stats
    }

    fun startPrefetch(allPages: List<Page>) {
        val stats = _prefetchStats.value ?: calculatePrefetchStats(allPages)
        val selectedIds = _selectedPagesForPrefetch.value
        val selectedPages = allPages.filter { it.id in selectedIds }
        
        val uniqueTexts = selectedPages.flatMap { it.buttonConfigs.filterNotNull() }
            .mapNotNull { config ->
                config.spokenText?.takeIf { it.isNotBlank() }
                    ?: if (config.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction) config.label else null
            }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { !ttsHelper.isCached(it) }

        if (uniqueTexts.isEmpty()) {
            _prefetchProgress.value = 1f
            _prefetchCurrentCount.value = 0
            _prefetchTotalCount.value = 0
            return
        }

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            _isPrefetching.value = true
            _prefetchProgress.value = 0f
            _prefetchCurrentCount.value = 0
            _prefetchTotalCount.value = uniqueTexts.size
            
            uniqueTexts.forEachIndexed { index, text ->
                _currentPrefetchText.value = text
                ttsHelper.prefetch(text) // This now suspends until finished
                _prefetchCurrentCount.value = index + 1
                _prefetchProgress.value = (index + 1).toFloat() / uniqueTexts.size
                delay(100) // Small delay to allow UI to breathe
            }
            
            _isPrefetching.value = false
            _currentPrefetchText.value = null
            // Refresh stats to show everything is cached now
            calculatePrefetchStats(allPages)
        }
    }

    fun cancelPrefetch() {
        prefetchJob?.cancel()
        _isPrefetching.value = false
        _currentPrefetchText.value = null
    }

    private fun handleCloudProgress(progress: Float, status: String) {
        _backupRestoreProgress.value = progress
        _backupRestoreStatus.value = when {
            status == "Uploading to Drive..." -> application.getString(R.string.cloud_progress_uploading)
            status == "Downloading from Drive..." -> application.getString(R.string.cloud_progress_downloading)
            status == "Exporting database..." -> application.getString(R.string.backup_progress_exporting)
            status.startsWith("Compressing audio:") -> application.getString(R.string.backup_progress_compressing, status.substringAfter(": "))
            status.startsWith("Extracting:") -> application.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
            status == "Importing data..." -> application.getString(R.string.restore_progress_importing)
            status == "Import complete." -> application.getString(R.string.restore_progress_complete)
            status == "Backup complete." -> application.getString(R.string.backup_progress_complete)
            else -> status
        }
    }

    private fun finishBackupRestoreProgress() {
        viewModelScope.launch {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    fun deleteEmptyButtons(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                pageRepository.deleteEmptyButtons()
            }
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun initializeDefaultMessagingAppsIfNeeded() {
        try {
            val sharedPrefs = application.getSharedPreferences("ghosttalk_app_meta", android.content.Context.MODE_PRIVATE) ?: return
            val hasInitialized = sharedPrefs.getBoolean("has_initialized_monitored_apps", false)
            if (!hasInitialized) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val pm = application.packageManager ?: return@launch
                        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                        val detectedApps = mutableSetOf<String>()
                        
                        for (resolveInfo in resolveInfos) {
                            val packageName = resolveInfo.activityInfo?.packageName ?: continue
                            if (packageName.isNotEmpty()) {
                                try {
                                    val appInfo = pm.getApplicationInfo(packageName, 0)
                                    if (isMessagingOrSocialApp(pm, appInfo)) {
                                        detectedApps.add(packageName)
                                    }
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (detectedApps.isNotEmpty()) {
                                val current = settingsRepository.monitoredNotificationApps.toMutableSet()
                                current.addAll(detectedApps)
                                settingsRepository.monitoredNotificationApps = current
                            }
                            sharedPrefs.edit { putBoolean("has_initialized_monitored_apps", true) }
                        }
                    } catch (e: Exception) {
                        // ignore background thread exceptions under test/mock environment
                    }
                }
            }
        } catch (e: Exception) {
            // ignore exceptions under test/mock environment
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun resetMonitoredNotificationAppsToMessagingDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = application.packageManager ?: return@launch
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val detectedApps = mutableSetOf<String>()
                
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo?.packageName ?: continue
                    if (packageName.isNotEmpty()) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            if (isMessagingOrSocialApp(pm, appInfo)) {
                                detectedApps.add(packageName)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    settingsRepository.monitoredNotificationApps = detectedApps
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun isMessagingOrSocialApp(pm: PackageManager, appInfo: android.content.pm.ApplicationInfo): Boolean {
        val pkg = appInfo.packageName.lowercase()
        
        // Exclude system, utility, mail, browser, contacts, dialer, accessibility, and companion apps that might match CATEGORY_SOCIAL
        val excludeKeywords = listOf(
            "browser", "watch", "wear", "companion", "launcher", "keyboard", 
            "weather", "clock", "email", "mail", "gmail", "calendar", "chrome", 
            "firefox", "opera", "edge", "safari", "system", "service", "provider",
            "contacts", "dialer", "phone", "accessibility", "hearing", "speech", 
            "transcribe", "translate"
        )
        if (excludeKeywords.any { pkg.contains(it) }) {
            return false
        }

        // 1. Exact or prefix matches for well-known messaging app package names
        val knownPackages = listOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
            "org.thoughtcrime.securesms", // Signal
            "com.facebook.orca", "com.facebook.mlite", // Messenger
            "com.discord",
            "com.skype.raider", "com.skype.m2",
            "com.viber.voip",
            "ch.threema.app", "ch.threema.app.work",
            "jp.naver.line.android",
            "com.tencent.mm", // WeChat
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.dynamite", // Google Chat
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms" // Default System SMS
        )
        if (knownPackages.any { pkg == it || pkg.startsWith("$it.") }) {
            return true
        }

        // 2. Check if category is social
        if (appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL) {
            return true
        }
        
        // 3. Fallback to general keywords (only if not excluded by excludeKeywords)
        val knownKeywords = listOf(
            "whatsapp", "telegram", "signal", "messenger", "discord", "skype", 
            "viber", "threema", "wechat", "imessage", "sms"
        )
        if (knownKeywords.any { pkg.contains(it) }) {
            return true
        }
        
        return false
    }
}

