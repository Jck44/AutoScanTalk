package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.cloud.domain.ExportLogsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.LogUploadResult
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleLogUploadUseCase
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.CloudAuthType
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
import com.andreas_kratzer.ghosttalk.core.tts.CachedAudioItem
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.domain.DeleteBookUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActionLogLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActiveBookNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.BackupSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.HueSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.SpotifySettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsPrefetchSettingsDelegate
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

@Suppress("unused")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val userModeSessionRepository: UserModeSessionRepository,
    val securityManager: SecurityManager,
    getPagesUseCase: GetPagesUseCase,
    val ttsDelegate: TtsSettingsDelegate,
    val scanningDelegate: ScanningSettingsDelegate,
    val cloudSyncDelegate: CloudSyncSettingsDelegate,
    val genAiDelegate: GenAiSettingsDelegate,
    val experimentalDelegate: ExperimentalSettingsDelegate,
    val hueDelegate: HueSettingsDelegate,
    val spotifyDelegate: SpotifySettingsDelegate,
    val prefetchDelegate: TtsPrefetchSettingsDelegate,
    val backupDelegate: BackupSettingsDelegate,
    private val backgroundScheduler: com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler,
    private val updateActiveBookNameUseCase: UpdateActiveBookNameUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val updateActionLogLimitUseCase: UpdateActionLogLimitUseCase,
    private val importExportManager: PageImportExportProvider,
    private val hueManager: PhilipsHueManager,
    private val spotifyManager: SpotifyManager,
    private val ttsHelper: TextToSpeechHelper,
    private val audioCacheRepository: AudioCacheRepository,
    private val pageRepository: PageRepository,
    private val syncLogProvider: SyncLogProvider,
    private val callActionProxy: dagger.Lazy<CallActionProxy>,
    private val exportLogsUseCase: ExportLogsUseCase,
    private val rescheduleLogUploadUseCase: RescheduleLogUploadUseCase
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
    val lateClickThresholdMillis = settingsRepository.lateClickThresholdFlow
    val isVocalSwitchEnabled = settingsRepository.isVocalSwitchEnabledFlow
    
    val staticRowEnabled = settingsRepository.staticRowEnabledFlow
    val staticRowScanPattern = settingsRepository.staticRowScanPatternFlow
    
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
    val syncModeBook = settingsRepository.syncModeBookFlow
    val syncModeTts = settingsRepository.syncModeTtsFlow
    val syncModeStats = settingsRepository.syncModeStatsFlow
    val syncModeSettings = settingsRepository.syncModeSettingsFlow
    val lastSuccessfulSyncTime = settingsRepository.lastSuccessfulSyncTimeFlow
    val syncIntervalMinutes = settingsRepository.syncIntervalMinutesFlow
    val googleDriveFolderId = settingsRepository.googleDriveFolderIdFlow
    val googleDriveFolderName = settingsRepository.googleDriveFolderNameFlow
    val syncTargetType = settingsRepository.syncTargetTypeFlow
    val googleAuthType = settingsRepository.googleAuthTypeFlow
    val localFolderSafUri = settingsRepository.localFolderSafUriFlow
    val localFolderSafName = settingsRepository.localFolderSafNameFlow
    val hueBridgeIp = settingsRepository.hueBridgeIpFlow
    val hueUsername = settingsRepository.hueUsernameFlow
    val huePairingStatus: StateFlow<String?> = hueDelegate.huePairingStatus
    val pendingCertificateInfo: StateFlow<com.andreas_kratzer.ghosttalk.core.cloud.BridgeCertificateInfo?> = hueDelegate.pendingCertificateInfo
    val hueCachedDevices = settingsRepository.hueCachedDevicesFlow
    val isUpdatingHueCache: StateFlow<Boolean> = hueDelegate.isUpdatingHueCache
    val isSyncing = cloudSyncDelegate.isSyncing
    val userEmail = cloudSyncDelegate.userEmail
    
    val availableBackups = cloudSyncDelegate.availableBackups
    val showBackupSelectionDialog = cloudSyncDelegate.showBackupSelectionDialog
    val syncLogs = cloudSyncDelegate.syncLogs
    val driveFolders = cloudSyncDelegate.driveFolders
    val isBrowsingFolders = cloudSyncDelegate.isBrowsingFolders
    
    val isGeminiEnabled = settingsRepository.isGeminiEnabledFlow
    val geminiToolStatus = genAiDelegate.geminiToolStatus
    val geminiApiKey = settingsRepository.geminiApiKeyFlow
    val useGeminiApiKey = settingsRepository.useGeminiApiKeyFlow

    val spotifyUserDisplayName = settingsRepository.spotifyUserDisplayNameFlow
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = spotifyDelegate.spotifyPlaylists
    val isLoadingPlaylists: StateFlow<Boolean> = spotifyDelegate.isLoadingPlaylists
    
    val isSmartPredictionEnabled = settingsRepository.isSmartPredictionEnabledFlow
    
    val isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabledFlow
    val monitoredNotificationApps = settingsRepository.monitoredNotificationAppsFlow
    val autoReadMode = settingsRepository.autoReadModeFlow
    val autoReadOnlyInUserMode = settingsRepository.autoReadOnlyInUserModeFlow
    val autoReadInStandby = settingsRepository.autoReadInStandbyFlow
    
    val selectedAppLanguage = settingsRepository.appLanguageFlow
    val themeMode = settingsRepository.themeModeFlow
    val buttonHistory = buttonUsageRepository.buttonHistory

    val keepScreenOnUserMode = settingsRepository.keepScreenOnUserModeFlow
    val userModeScreenBehavior = settingsRepository.userModeScreenBehaviorFlow
    val statsRetentionDays = settingsRepository.statsRetentionDaysFlow
    val statsAggregationHours = settingsRepository.statsAggregationHoursFlow
    val onlyRecordHardwareStats = settingsRepository.onlyRecordHardwareStatsFlow
    val geminiTimeout = settingsRepository.geminiTimeoutFlow
    val geminiRedoPrediction = settingsRepository.geminiRedoPredictionFlow
    val blockVolumeKeys = settingsRepository.blockVolumeKeysFlow
    val speakerVolume = settingsRepository.speakerVolumeFlow
    val headphoneVolume = settingsRepository.headphoneVolumeFlow

    val securityPin = settingsRepository.securityPinFlow
    val securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutesFlow
    val isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletionFlow
    val isBiometricEnabled = settingsRepository.isBiometricEnabledFlow
    val isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEditFlow
    val isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettingsFlow
    val isSecurityRequiredForAnalytics = settingsRepository.isSecurityRequiredForAnalyticsFlow
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
    val ttsPlaybackSpeed = settingsRepository.ttsPlaybackSpeedFlow

    // --- CallSettings ---
    val maxCallDurationSeconds = settingsRepository.maxCallDurationSecondsFlow
    val callDurationFeedbackIntervalSeconds = settingsRepository.callDurationFeedbackIntervalSecondsFlow
    val outgoingCallIntro = settingsRepository.outgoingCallIntroFlow
    val incomingCallIntro = settingsRepository.incomingCallIntroFlow
    val incomingCallScanLimitUserModeActive = settingsRepository.incomingCallScanLimitUserModeActiveFlow
    val incomingCallAutoActionUserModeActive = settingsRepository.incomingCallAutoActionUserModeActiveFlow
    val incomingCallDelayUserModeInactive = settingsRepository.incomingCallDelayUserModeInactiveFlow
    val incomingCallAutoActionUserModeInactive = settingsRepository.incomingCallAutoActionUserModeInactiveFlow
    val callAnnouncementAsCue = settingsRepository.callAnnouncementAsCueFlow
    val autoEnableSpeakerphone = settingsRepository.autoEnableSpeakerphoneFlow
    val simulateCallsEnabled = settingsRepository.simulateCallsEnabledFlow
    val hangUpPressesRequired = settingsRepository.hangUpPressesRequiredFlow
    val filterCallsNotInContacts = settingsRepository.filterCallsNotInContactsFlow
    
    private val _showActionHistoryDialog = MutableStateFlow(false)
    val showActionHistoryDialog = _showActionHistoryDialog.asStateFlow()

    private val _showUsageStatsDialog = MutableStateFlow(false)
    val showUsageStatsDialog = _showUsageStatsDialog.asStateFlow()

    private val _showPrefetchDialog = MutableStateFlow(false)
    val showPrefetchDialog = _showPrefetchDialog.asStateFlow()

    private val _topButtonUsage = MutableStateFlow<List<com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat>>(emptyList())
    val topButtonUsage = _topButtonUsage.asStateFlow()

    // --- TTS Prefetch State ---
    val selectedPagesForPrefetch = prefetchDelegate.selectedPagesForPrefetch
    val isPrefetching = prefetchDelegate.isPrefetching
    val prefetchProgress = prefetchDelegate.prefetchProgress
    val prefetchCurrentCount = prefetchDelegate.prefetchCurrentCount
    val prefetchTotalCount = prefetchDelegate.prefetchTotalCount
    val currentPrefetchText = prefetchDelegate.currentPrefetchText
    val prefetchStats = prefetchDelegate.prefetchStats

    // --- Backup & Restore State ---
    val isBackupRestoreRunning = backupDelegate.isBackupRestoreRunning
    val backupRestoreProgress = backupDelegate.backupRestoreProgress
    val backupRestoreStatus = backupDelegate.backupRestoreStatus

    // --- Log Sync State ---
    val syncModeLogs = settingsRepository.syncModeLogsFlow
    val syncLogsIntervalHours = settingsRepository.syncLogsIntervalHoursFlow
    val lastLogsSyncTime = settingsRepository.lastLogsSyncTimeFlow

    private val _manualUpdateCheckTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val manualUpdateCheckTrigger = _manualUpdateCheckTrigger.asSharedFlow()

    private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus?>(null)
    val updateCheckStatus = _updateCheckStatus.asStateFlow()
    
    private var prefetchJob: kotlinx.coroutines.Job? = null

    init {
        ttsDelegate.initialize(viewModelScope)
        genAiDelegate.updateGeminiToolStatus()
        initializeDefaultMessagingAppsIfNeeded()
        hueDelegate.initialize(viewModelScope)
        spotifyDelegate.initialize(viewModelScope)
        prefetchDelegate.initialize(viewModelScope)
        backupDelegate.initialize(viewModelScope)
    }
    
    fun triggerStartSetupWizard() {
        viewModelScope.launch {
            _navigationEvent.emit(SettingsNavigationEvent.StartSetup)
        }
    }

    private val _navigationEvent = kotlinx.coroutines.flow.MutableSharedFlow<SettingsNavigationEvent>()
    val navigationEvents = _navigationEvent.asSharedFlow()

    private val _selectedHistoryItem = MutableStateFlow<ButtonUsageRepository.ButtonUsageEvent?>(null)
    val selectedHistoryItem = _selectedHistoryItem.asStateFlow()

    sealed class SettingsNavigationEvent {
        data class EditButton(val pageId: String, val buttonId: String) : SettingsNavigationEvent()
        data class JumpToPage(val pageId: String) : SettingsNavigationEvent()
        object StartSetup : SettingsNavigationEvent()
    }

    sealed class UpdateCheckStatus {
        object Checking : UpdateCheckStatus()
        object UpToDate : UpdateCheckStatus()
        /** Update found and Play Store download dialog was triggered. */
        object UpdateFound : UpdateCheckStatus()
        /** App is not installed via Play Store (sideloaded / ADB). */
        object NotFromPlayStore : UpdateCheckStatus()
        data class Error(val message: String) : UpdateCheckStatus()
    }
    
    val isBiometricSupported: Boolean = securityManager.isBiometricSupported(application)

    val authIntentFlow = kotlinx.coroutines.flow.merge(
        cloudSyncDelegate.authIntentFlow,
        genAiDelegate.authIntentFlow
    )
    
    val signInErrorMessage = cloudSyncDelegate.signInErrorMessage

    // --- Delegation Methods (UI Actions) ---
    fun refresh() {
        ttsDelegate.loadAvailableLanguages()
        ttsDelegate.loadAvailableAudioDevices()
        genAiDelegate.updateGeminiToolStatus()
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
    val isCaregiverDevice = settingsRepository.isCaregiverDeviceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.isCaregiverDevice)

    fun setCaregiverDevice(enabled: Boolean) {
        settingsRepository.isCaregiverDevice = enabled
    }

    fun setBluetoothDelay(d: String) = scanningDelegate.setBluetoothDelay(d)

    fun signIn(ctx: Context) = cloudSyncDelegate.signIn(ctx, viewModelScope)
    
    fun signOut() {
        cloudSyncDelegate.signOut(viewModelScope)
        // Disable cloud-dependent features on sign out
        settingsRepository.isCloudSyncEnabled = false
        settingsRepository.isGeminiEnabled = false
        settingsRepository.googleDriveFolderId = null
        settingsRepository.googleDriveFolderName = null
        genAiDelegate.updateGeminiToolStatus()
    }

    fun switchAccount(ctx: Context) = cloudSyncDelegate.switchAccount(ctx, viewModelScope)

    fun setCloudSyncEnabled(ctx: Context, e: Boolean) = cloudSyncDelegate.setCloudSyncEnabled(ctx, e, viewModelScope)
       fun syncNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.TWO_WAY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = {
                backupDelegate.finishBackupRestoreProgress()
                if (settingsRepository.syncModeLogs == "BACKUP_ONLY") {
                    viewModelScope.launch {
                        exportLogsUseCase.performAutoUpload()
                    }
                }
            }
        )
    }
    
    fun backupNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.BACKUP_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = {
                backupDelegate.finishBackupRestoreProgress()
                if (settingsRepository.syncModeLogs == "BACKUP_ONLY") {
                    viewModelScope.launch {
                        exportLogsUseCase.performAutoUpload()
                    }
                }
            }
        )
    }
    
    fun restoreNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.RESTORE_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = { backupDelegate.finishBackupRestoreProgress() }
        )
    }
    
    fun fetchAvailableBackupsForImport(folderId: String? = null) = cloudSyncDelegate.fetchAvailableBackupsForImport(folderId, viewModelScope)
    fun fetchAvailableBackupsForImportByUrlOrId(urlOrId: String) = cloudSyncDelegate.fetchAvailableBackupsForImportByUrlOrId(urlOrId, viewModelScope)
    fun fetchAvailableBackupsFromSaf(uri: String, name: String) = cloudSyncDelegate.fetchAvailableBackupsFromSaf(uri, name, viewModelScope)
    
    fun importCloudBackup(backupInfo: com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo) {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.importCloudBackup(
            backupInfo = backupInfo,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onImported = { _ -> },
            onComplete = {
                viewModelScope.launch {
                    delay(500)
                    backupDelegate.setBackupRestoreRunning(false)
                }
            }
        )
    }
    fun dismissBackupSelectionDialog() = cloudSyncDelegate.dismissBackupSelectionDialog()

    fun fetchDriveFolders(parentFolderId: String = "root") = cloudSyncDelegate.fetchDriveFolders(parentFolderId, viewModelScope)
    fun selectDriveFolder(folderId: String?, folderName: String?) = cloudSyncDelegate.selectDriveFolder(folderId, folderName)
    fun selectDriveFolderByUrlOrId(urlOrId: String, onResult: (Boolean, String?) -> Unit) = 
        cloudSyncDelegate.selectDriveFolderByUrlOrId(urlOrId, viewModelScope, onResult)
    
    fun loadSyncLogs() = cloudSyncDelegate.loadSyncLogs(viewModelScope)
    fun clearSyncLogs() = cloudSyncDelegate.clearSyncLogs(viewModelScope)
    
    fun setSyncModeBook(m: String) { settingsRepository.syncModeBook = m }
    fun setSyncModeTts(m: String) { settingsRepository.syncModeTts = m }
    fun setSyncModeStats(m: String) { settingsRepository.syncModeStats = m }
    fun setSyncModeSettings(m: String) { settingsRepository.syncModeSettings = m }
    fun setGoogleAuthType(type: CloudAuthType) {
        settingsRepository.googleAuthType = type
    }
    fun setSyncIntervalMinutes(minutes: Long) {
        settingsRepository.syncIntervalMinutes = minutes
        cloudSyncDelegate.reschedule()
    }
    fun setSyncTargetType(type: String) {
        settingsRepository.syncTargetType = type
        cloudSyncDelegate.reschedule()
    }
    fun selectLocalFolderSaf(uri: String, name: String) {
        settingsRepository.localFolderSafUri = uri
        settingsRepository.localFolderSafName = name
        cloudSyncDelegate.reschedule()
    }
    fun setHueBridgeIp(ip: String) { settingsRepository.hueBridgeIp = ip }
    fun setHueUsername(username: String) { settingsRepository.hueUsername = username }

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
    fun setTtsPlaybackSpeed(value: Float) = ttsDelegate.setTtsPlaybackSpeed(value)

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
        hueDelegate.discoverHueBridges()
    }

    fun registerLocalHueBridge() {
        hueDelegate.registerLocalHueBridge()
    }

    fun confirmHueBridgeCertificate() {
        hueDelegate.confirmHueBridgeCertificate()
    }

    fun cancelHueBridgeCertificate() {
        hueDelegate.cancelHueBridgeCertificate()
    }

    fun refreshHueDevicesCache(silentOnFailure: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        hueDelegate.refreshHueDevicesCache(silentOnFailure, onResult)
    }

    fun setGeminiEnabled(ctx: Context, e: Boolean) {
        genAiDelegate.setGeminiCloudEnabled(ctx, e, viewModelScope)
    }

    fun activateGemini(ctx: Context) = genAiDelegate.activateGemini(ctx, viewModelScope)

    fun setPersistActionLogs(e: Boolean) { settingsRepository.persistActionLogs = e }
    fun setSwitchActivationKey(k: String) { settingsRepository.switchActivationKey = k }
    fun setVolumeKeysActivate(e: Boolean) { settingsRepository.volumeKeysActivate = e }
    fun setBlockVolumeKeys(e: Boolean) { settingsRepository.blockVolumeKeys = e }
    fun setSpeakerVolume(v: Int) { settingsRepository.speakerVolume = v }
    fun setHeadphoneVolume(v: Int) { settingsRepository.headphoneVolume = v }
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
    fun setAutoReadMode(mode: com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode) {
        settingsRepository.autoReadMode = mode
    }
    fun setAutoReadOnlyInUserMode(e: Boolean) {
        settingsRepository.autoReadOnlyInUserMode = e
    }
    fun setAutoReadInStandby(e: Boolean) {
        settingsRepository.autoReadInStandby = e
    }
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
    fun setStatsRetentionDays(days: Int) { 
        settingsRepository.statsRetentionDays = days 
        viewModelScope.launch(Dispatchers.IO) {
            buttonUsageRepository.cleanupOldStats(days)
        }
    }
    fun setStatsAggregationHours(hours: Int) { settingsRepository.statsAggregationHours = hours }
    fun setOnlyRecordHardwareStats(e: Boolean) { settingsRepository.onlyRecordHardwareStats = e }
    fun setGeminiTimeoutInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.geminiTimeout = it }
    }
    fun setGeminiRedoPrediction(e: Boolean) { settingsRepository.geminiRedoPrediction = e }
    fun setGeminiApiKey(key: String?) {
        settingsRepository.geminiApiKey = key
    }
    fun setUseGeminiApiKey(useKey: Boolean) {
        settingsRepository.useGeminiApiKey = useKey
    }

    fun connectSpotify(ctx: Context) {
        spotifyDelegate.connectSpotify(ctx)
    }

    fun disconnectSpotify() {
        spotifyDelegate.disconnectSpotify()
    }

    fun loadSpotifyPlaylists() {
        spotifyDelegate.loadSpotifyPlaylists()
    }

    fun setLimitScanCycles(enabled: Boolean) = scanningDelegate.setLimitScanCycles(enabled)
    fun setScanCycleLimitInput(input: String) = scanningDelegate.setScanCycleLimitInput(input)
    
    fun setStaticRowEnabled(enabled: Boolean) = scanningDelegate.setStaticRowEnabled(enabled)
    fun setStaticRowScanPattern(pattern: String) = scanningDelegate.setStaticRowScanPattern(pattern)
    fun setLateClickThresholdInput(input: String) = scanningDelegate.setLateClickThresholdInput(input)
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

    fun setSecurityRequiredForAnalytics(required: Boolean) {
        settingsRepository.isSecurityRequiredForAnalytics = required
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

    val backgroundLocationEnabled = settingsRepository.backgroundLocationEnabledFlow
    val backgroundLocationInterval = settingsRepository.backgroundLocationIntervalFlow
    val backgroundWeatherEnabled = settingsRepository.backgroundWeatherEnabledFlow
    val backgroundWeatherInterval = settingsRepository.backgroundWeatherIntervalFlow

    fun setBackgroundLocationEnabled(enabled: Boolean) {
        settingsRepository.backgroundLocationEnabled = enabled
        backgroundScheduler.scheduleLocationUpdate()
    }

    fun setBackgroundLocationInterval(hours: Long) {
        settingsRepository.backgroundLocationInterval = hours
        backgroundScheduler.scheduleLocationUpdate()
    }

    fun setBackgroundWeatherEnabled(enabled: Boolean) {
        settingsRepository.backgroundWeatherEnabled = enabled
        backgroundScheduler.scheduleWeatherUpdate()
    }

    fun setBackgroundWeatherInterval(hours: Long) {
        settingsRepository.backgroundWeatherInterval = hours
        backgroundScheduler.scheduleWeatherUpdate()
    }

    // --- CallSettings Setters ---
    fun setMaxCallDurationSeconds(seconds: Int) {
        settingsRepository.maxCallDurationSeconds = seconds
    }
    fun setCallDurationFeedbackIntervalSeconds(seconds: Int) {
        settingsRepository.callDurationFeedbackIntervalSeconds = seconds
    }
    fun setOutgoingCallIntro(text: String) {
        settingsRepository.outgoingCallIntro = text
    }
    fun setIncomingCallIntro(text: String) {
        settingsRepository.incomingCallIntro = text
    }
    fun setIncomingCallScanLimitUserModeActive(limit: Int) {
        settingsRepository.incomingCallScanLimitUserModeActive = limit
    }
    fun setIncomingCallAutoActionUserModeActive(action: String) {
        settingsRepository.incomingCallAutoActionUserModeActive = action
    }
    fun setIncomingCallDelayUserModeInactive(seconds: Int) {
        settingsRepository.incomingCallDelayUserModeInactive = seconds
    }
    fun setIncomingCallAutoActionUserModeInactive(action: String) {
        settingsRepository.incomingCallAutoActionUserModeInactive = action
    }
    fun setCallAnnouncementAsCue(asCue: Boolean) {
        settingsRepository.callAnnouncementAsCue = asCue
    }
    fun setAutoEnableSpeakerphone(enable: Boolean) {
        settingsRepository.autoEnableSpeakerphone = enable
    }
    fun setSimulateCallsEnabled(enable: Boolean) {
        settingsRepository.simulateCallsEnabled = enable
    }
    fun setHangUpPressesRequired(presses: Int) {
        settingsRepository.hangUpPressesRequired = presses
    }
    fun setFilterCallsNotInContacts(filter: Boolean) {
        settingsRepository.filterCallsNotInContacts = filter
    }

    val isDefaultDialer: Boolean
        get() {
            val telecomManager = application.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            return telecomManager.defaultDialerPackage == application.packageName
        }

    fun requestDefaultDialer(activity: android.app.Activity) {
        val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
            activity.startActivityForResult(intent, 123)
            return
        }
        val telecomManager = activity.getSystemService(android.telecom.TelecomManager::class.java)
        if (telecomManager != null && telecomManager.defaultDialerPackage != activity.packageName) {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, 123)
        }
    }

    val activeBookId: String
        get() = settingsRepository.activeBookId

    suspend fun exportLocalBackup(): String {
        return backupDelegate.exportLocalBackup()
    }

    suspend fun exportLocalBackupZip(outputStream: java.io.OutputStream) {
        backupDelegate.exportLocalBackupZip(outputStream)
    }

    suspend fun importLocalBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        backupDelegate.importLocalBackupZip(inputStream, onSuccess, onError)
    }

    suspend fun importLocalBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        backupDelegate.importLocalBackup(json, onSuccess, onError)
    }

    suspend fun importGlobalManualBackup(json: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        backupDelegate.importGlobalManualBackup(json, onSuccess, onError)
    }

    suspend fun importGlobalManualBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        backupDelegate.importGlobalManualBackupZip(inputStream, onSuccess, onError)
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
        prefetchDelegate.togglePageSelectionForPrefetch(pageId)
    }

    fun selectAllPagesForPrefetch(pages: List<Page>) {
        prefetchDelegate.selectAllPagesForPrefetch(pages)
    }

    fun deselectAllPagesForPrefetch() {
        prefetchDelegate.deselectAllPagesForPrefetch()
    }

    fun calculatePrefetchStats(allPages: List<Page>): com.andreas_kratzer.ghosttalk.core.model.PrefetchStats {
        return prefetchDelegate.calculatePrefetchStats(allPages)
    }

    fun startPrefetch(allPages: List<Page>) {
        prefetchDelegate.startPrefetch(allPages)
    }

    fun cancelPrefetch() {
        prefetchDelegate.cancelPrefetch()
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
            val sharedPrefs = application.getSharedPreferences("ghosttalk_app_meta", Context.MODE_PRIVATE) ?: return
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
                                    if (isMessagingOrSocialApp(appInfo)) {
                                        detectedApps.add(packageName)
                                    }
                                } catch (_: Exception) {
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
                    } catch (_: Exception) {
                        // ignore background thread exceptions under test/mock environment
                    }
                }
            }
        } catch (_: Exception) {
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
                            if (isMessagingOrSocialApp(appInfo)) {
                                detectedApps.add(packageName)
                            }
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    settingsRepository.monitoredNotificationApps = detectedApps
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun isMessagingOrSocialApp(appInfo: android.content.pm.ApplicationInfo): Boolean {
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
        return knownKeywords.any { pkg.contains(it) }
    }

    fun simulateIncomingCall(name: String, phone: String) {
        callActionProxy.get().simulateIncomingCall(name, phone)
    }

    fun simulateOutgoingCall(name: String, phone: String) {
        callActionProxy.get().simulateOutgoingCall(name, phone)
    }

    fun setSyncModeLogs(mode: String) {
        settingsRepository.syncModeLogs = mode
        rescheduleLogUploadUseCase()
    }

    fun setSyncLogsIntervalHours(hours: Long) {
        settingsRepository.syncLogsIntervalHours = hours
        rescheduleLogUploadUseCase()
    }

    fun shareLogs(ctx: Context) {
        viewModelScope.launch {
            try {
                exportLogsUseCase.shareLogs(ctx)
            } catch (e: Exception) {
                Toast.makeText(application, "Fehler beim Teilen des Fehlerberichts: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun uploadLogsNow() {
        viewModelScope.launch {
            Toast.makeText(application, "Fehlerbericht wird hochgeladen...", Toast.LENGTH_SHORT).show()
            when (val result = exportLogsUseCase.performAutoUpload(force = true)) {
                is LogUploadResult.Success -> {
                    Toast.makeText(application, R.string.settings_logs_upload_success, Toast.LENGTH_LONG).show()
                }
                is LogUploadResult.Skipped -> {
                    Toast.makeText(application, R.string.settings_logs_upload_skipped, Toast.LENGTH_LONG).show()
                }
                is LogUploadResult.Error -> {
                    Toast.makeText(application, "Upload fehlgeschlagen: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Profile Management ---
    val activeProfileIdFlow = settingsRepository.activeProfileIdFlow
    val allSettingsProfilesFlow = settingsRepository.getAllProfilesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setActiveProfileId(profileId: String) {
        settingsRepository.activeProfileId = profileId
    }

    fun createNewProfile(name: String) {
        viewModelScope.launch {
            val currentConfig = settingsRepository.getProfileById(settingsRepository.activeProfileId)?.config 
                ?: com.andreas_kratzer.ghosttalk.core.model.ProfileConfig()
            val newProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
                id = "profile-${java.util.UUID.randomUUID()}",
                name = name,
                config = currentConfig,
                profileVersionSequence = 1L,
                updatedAt = System.currentTimeMillis()
            )
            settingsRepository.insertProfile(newProfile)
            settingsRepository.activeProfileId = newProfile.id
        }
    }

    fun deleteProfile(profile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile) {
        viewModelScope.launch {
            if (profile.id != "profile-default") {
                settingsRepository.deleteProfile(profile)
                if (settingsRepository.activeProfileId == profile.id) {
                    settingsRepository.activeProfileId = "profile-default"
                }
            }
        }
    }
}

