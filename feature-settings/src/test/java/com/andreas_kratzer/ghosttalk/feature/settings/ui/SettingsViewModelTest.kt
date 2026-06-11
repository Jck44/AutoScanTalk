package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.app.Application
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.export.ImportResult
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var userModeSessionRepository: UserModeSessionRepository
    private lateinit var securityManager: SecurityManager
    private lateinit var getPagesUseCase: GetPagesUseCase
    
    private lateinit var ttsDelegate: TtsSettingsDelegate
    private lateinit var scanningDelegate: ScanningSettingsDelegate
    private lateinit var cloudSyncDelegate: CloudSyncSettingsDelegate
    private lateinit var genAiDelegate: GenAiSettingsDelegate
    private lateinit var experimentalDelegate: ExperimentalSettingsDelegate
    private lateinit var hueDelegate: HueSettingsDelegate
    private lateinit var spotifyDelegate: SpotifySettingsDelegate
    private lateinit var prefetchDelegate: TtsPrefetchSettingsDelegate
    private lateinit var backupDelegate: BackupSettingsDelegate
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var hueManager: PhilipsHueManager
    private lateinit var spotifyManager: com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
    private lateinit var updateActionLogLimitUseCase: UpdateActionLogLimitUseCase
    private lateinit var updateActiveBookNameUseCase: UpdateActiveBookNameUseCase
    private lateinit var deleteBookUseCase: DeleteBookUseCase
    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
    private lateinit var audioCacheRepository: com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
    
    private lateinit var backgroundScheduler: com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler
    
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        getPagesUseCase = mockk(relaxed = true)
        // Mock all Flow fields explicitly to prevent combine/flatMapLatest from receiving nulls and crashing
            every { settingsRepository.activeBookIdFlow } returns MutableStateFlow("test-book")
            every { settingsRepository.activeProfileIdFlow } returns MutableStateFlow("profile-default")
            every { settingsRepository.getAllProfilesFlow() } returns flowOf(emptyList())
            every { getPagesUseCase.execute(any()) } returns flowOf(emptyList())
            every { settingsRepository.ttsLanguageFlow } returns MutableStateFlow("de")
            every { settingsRepository.ttsVoiceNameFlow } returns MutableStateFlow(null)
            every { settingsRepository.autoStartScanningFlow } returns MutableStateFlow(true)
            every { settingsRepository.scanDelayFlow } returns MutableStateFlow(2000L)
            every { settingsRepository.resumeScanningFromStartFlow } returns MutableStateFlow(true)
            every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow("linear")
            every { settingsRepository.holdingTimeMillisFlow } returns MutableStateFlow(250L)
            every { settingsRepository.bluetoothDelayFlow } returns MutableStateFlow(100L)
            every { settingsRepository.lateClickThresholdFlow } returns MutableStateFlow(250L)
            every { settingsRepository.isVocalSwitchEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.staticRowEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.defaultStartPageIdFlow } returns MutableStateFlow(null)
            every { settingsRepository.ttsAudioDeviceAddressFlow } returns MutableStateFlow(null)
            every { settingsRepository.cuesAudioDeviceAddressFlow } returns MutableStateFlow(null)
            every { settingsRepository.recordingAudioSourceFlow } returns MutableStateFlow(0)
            every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow(true)
            every { settingsRepository.switchActivationKeyFlow } returns MutableStateFlow("~3")
            every { settingsRepository.volumeKeysActivateFlow } returns MutableStateFlow(false)
            every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow(false)
            every { settingsRepository.showPageIdInLogFlow } returns MutableStateFlow(false)
            every { settingsRepository.isDataCloudSyncEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.syncModeBookFlow } returns MutableStateFlow("TWO_WAY")
            every { settingsRepository.syncModeTtsFlow } returns MutableStateFlow("TWO_WAY")
            every { settingsRepository.syncModeStatsFlow } returns MutableStateFlow("RESTORE_ONLY")
            every { settingsRepository.syncModeSettingsFlow } returns MutableStateFlow("TWO_WAY")
            every { settingsRepository.lastSuccessfulSyncTimeFlow } returns MutableStateFlow(0L)
            every { settingsRepository.syncIntervalMinutesFlow } returns MutableStateFlow(60L)
            every { settingsRepository.foregroundSyncIntervalMinutesFlow } returns MutableStateFlow(5L)
            every { settingsRepository.googleDriveFolderIdFlow } returns MutableStateFlow(null)
            every { settingsRepository.googleDriveFolderNameFlow } returns MutableStateFlow(null)
            every { settingsRepository.syncTargetTypeFlow } returns MutableStateFlow("google_drive")
            every { settingsRepository.googleAuthTypeFlow } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM)
            every { settingsRepository.localFolderSafUriFlow } returns MutableStateFlow(null)
            every { settingsRepository.localFolderSafNameFlow } returns MutableStateFlow(null)
            every { settingsRepository.hueBridgeIpFlow } returns MutableStateFlow("")
            every { settingsRepository.hueUsernameFlow } returns MutableStateFlow("")
            every { settingsRepository.hueCachedDevicesFlow } returns MutableStateFlow("")
            every { settingsRepository.isGeminiEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.geminiApiKeyFlow } returns MutableStateFlow(null)
            every { settingsRepository.useGeminiApiKeyFlow } returns MutableStateFlow(false)
            every { settingsRepository.isGeminiVerifiedFlow } returns MutableStateFlow(false)
            every { settingsRepository.spotifyUserDisplayNameFlow } returns MutableStateFlow(null)
            every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.isNotificationReadingEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.monitoredNotificationAppsFlow } returns MutableStateFlow(emptySet())
            every { settingsRepository.autoReadModeFlow } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF)
            every { settingsRepository.autoReadOnlyInUserModeFlow } returns MutableStateFlow(true)
            every { settingsRepository.autoReadInStandbyFlow } returns MutableStateFlow(false)
            every { settingsRepository.appLanguageFlow } returns MutableStateFlow("en")
            every { settingsRepository.themeModeFlow } returns MutableStateFlow("LIGHT")
            every { settingsRepository.keepScreenOnUserModeFlow } returns MutableStateFlow(true)
            every { settingsRepository.userModeScreenBehaviorFlow } returns MutableStateFlow("GRID")
            every { settingsRepository.statsRetentionDaysFlow } returns MutableStateFlow(30)
            every { settingsRepository.statsAggregationHoursFlow } returns MutableStateFlow(24)
            every { settingsRepository.onlyRecordHardwareStatsFlow } returns MutableStateFlow(false)
            every { settingsRepository.firebaseAnalyticsEnabledFlow } returns MutableStateFlow(true)
            every { settingsRepository.geminiTimeoutFlow } returns MutableStateFlow(10000L)
            every { settingsRepository.geminiRedoPredictionFlow } returns MutableStateFlow(false)
            every { settingsRepository.blockVolumeKeysFlow } returns MutableStateFlow(false)
            every { settingsRepository.speakerVolumeFlow } returns MutableStateFlow(100)
            every { settingsRepository.headphoneVolumeFlow } returns MutableStateFlow(100)
            every { settingsRepository.securityPinFlow } returns MutableStateFlow("")
            every { settingsRepository.securityPinTimeoutMinutesFlow } returns MutableStateFlow(30L)
            every { settingsRepository.isPinRequiredForDeletionFlow } returns MutableStateFlow(false)
            every { settingsRepository.isBiometricEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.isSecurityRequiredForEditFlow } returns MutableStateFlow(false)
            every { settingsRepository.isSecurityRequiredForSettingsFlow } returns MutableStateFlow(false)
            every { settingsRepository.isSecurityRequiredForAnalyticsFlow } returns MutableStateFlow(false)
            every { settingsRepository.startupBehaviorFlow } returns MutableStateFlow("BOOK_SELECTION")
            every { settingsRepository.logIgnoredActionsFlow } returns MutableStateFlow(false)
            every { settingsRepository.logStopActionsFlow } returns MutableStateFlow(false)
            every { settingsRepository.limitScanCyclesFlow } returns MutableStateFlow(false)
            every { settingsRepository.scanCycleLimitFlow } returns MutableStateFlow(2)
            every { settingsRepository.actionLogLimitFlow } returns MutableStateFlow(100)
            every { settingsRepository.forceSoftKeyboardFlow } returns MutableStateFlow(true)
            every { settingsRepository.ttsEngineFlow } returns MutableStateFlow(null)
            every { settingsRepository.elevenLabsApiKeyFlow } returns MutableStateFlow(null)
            every { settingsRepository.elevenLabsModelFlow } returns MutableStateFlow("eleven_multilingual_v2")
            every { settingsRepository.elevenLabsStabilityFlow } returns MutableStateFlow(0.5f)
            every { settingsRepository.elevenLabsSimilarityBoostFlow } returns MutableStateFlow(0.75f)
            every { settingsRepository.ttsPlaybackSpeedFlow } returns MutableStateFlow(1.0f)
            every { settingsRepository.maxCallDurationSecondsFlow } returns MutableStateFlow(300)
            every { settingsRepository.callDurationFeedbackIntervalSecondsFlow } returns MutableStateFlow(60)
            every { settingsRepository.outgoingCallIntroFlow } returns MutableStateFlow("")
            every { settingsRepository.incomingCallIntroFlow } returns MutableStateFlow("")
            every { settingsRepository.incomingCallScanLimitUserModeActiveFlow } returns MutableStateFlow(2)
            every { settingsRepository.incomingCallAutoActionUserModeActiveFlow } returns MutableStateFlow("NONE")
            every { settingsRepository.incomingCallDelayUserModeInactiveFlow } returns MutableStateFlow(10)
            every { settingsRepository.incomingCallAutoActionUserModeInactiveFlow } returns MutableStateFlow("NONE")
            every { settingsRepository.callAnnouncementAsCueFlow } returns MutableStateFlow(false)
            every { settingsRepository.autoEnableSpeakerphoneFlow } returns MutableStateFlow(true)
            every { settingsRepository.simulateCallsEnabledFlow } returns MutableStateFlow(false)
            every { settingsRepository.hangUpPressesRequiredFlow } returns MutableStateFlow(1)
            every { settingsRepository.filterCallsNotInContactsFlow } returns MutableStateFlow(false)
        bookRepository = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)
        userModeSessionRepository = mockk(relaxed = true)
        securityManager = mockk(relaxed = true)
        backgroundScheduler = mockk(relaxed = true)
        
        importExportManager = mockk(relaxed = true)
        hueManager = mockk(relaxed = true)
        spotifyManager = mockk(relaxed = true)
        updateActionLogLimitUseCase = mockk(relaxed = true)
        updateActiveBookNameUseCase = mockk(relaxed = true)
        deleteBookUseCase = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        audioCacheRepository = mockk(relaxed = true)

        ttsDelegate = mockk(relaxed = true) {
            every { availableLanguages } returns MutableStateFlow(emptyList())
            every { availableVoices } returns MutableStateFlow(emptyList())
            every { availableAudioDevices } returns MutableStateFlow(emptyList())
            every { cachedAudioDevices } returns MutableStateFlow<Map<String, String>>(emptyMap())
        }
        scanningDelegate = mockk(relaxed = true)
        cloudSyncDelegate = mockk(relaxed = true) {
            every { isSyncing } returns MutableStateFlow(false)
            every { userEmail } returns MutableStateFlow(null)
            every { availableBackups } returns MutableStateFlow(emptyList())
            every { showBackupSelectionDialog } returns MutableStateFlow(false)
            every { syncLogs } returns MutableStateFlow(emptyList())
            every { driveFolders } returns MutableStateFlow(emptyList())
            every { isBrowsingFolders } returns MutableStateFlow(false)
        }
        genAiDelegate = mockk(relaxed = true) {
            every { geminiToolStatus } returns MutableStateFlow<Map<String, com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase.ToolStatus>>(emptyMap())
        }
        experimentalDelegate = mockk(relaxed = true)
        hueDelegate = HueSettingsDelegate(
            context = application,
            settingsRepository = settingsRepository,
            hueManager = hueManager
        )
        hueDelegate.initialize(kotlinx.coroutines.CoroutineScope(testDispatcher))
        spotifyDelegate = SpotifySettingsDelegate(
            settingsRepository = settingsRepository,
            spotifyManager = spotifyManager
        )
        spotifyDelegate.initialize(kotlinx.coroutines.CoroutineScope(testDispatcher))
        prefetchDelegate = TtsPrefetchSettingsDelegate(
            ttsHelper = ttsHelper
        )
        prefetchDelegate.initialize(kotlinx.coroutines.CoroutineScope(testDispatcher))
        backupDelegate = BackupSettingsDelegate(
            context = application,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            importExportManager = importExportManager,
            syncLogProvider = mockk(relaxed = true)
        )
        backupDelegate.initialize(kotlinx.coroutines.CoroutineScope(testDispatcher))

        // Mock common flows
        every { settingsRepository.activeBookId } returns "test-book"
        every { settingsRepository.activeProfileId } returns "profile-default"
        every { settingsRepository.resumeScanningFromStartFlow } returns MutableStateFlow(true)
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow("linear")
        every { settingsRepository.holdingTimeMillisFlow } returns MutableStateFlow(250L)
        every { settingsRepository.bluetoothDelayFlow } returns MutableStateFlow(100L)
        every { settingsRepository.lateClickThresholdFlow } returns MutableStateFlow(250L)
        every { settingsRepository.isVocalSwitchEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.staticRowEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.defaultStartPageIdFlow } returns MutableStateFlow(null)
        every { settingsRepository.ttsAudioDeviceAddressFlow } returns MutableStateFlow(null)
        every { settingsRepository.cuesAudioDeviceAddressFlow } returns MutableStateFlow(null)
        every { settingsRepository.recordingAudioSourceFlow } returns MutableStateFlow(0)
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow(true)
        every { settingsRepository.switchActivationKeyFlow } returns MutableStateFlow("~3")
        every { settingsRepository.volumeKeysActivateFlow } returns MutableStateFlow(false)
        every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow(false)
        every { settingsRepository.showPageIdInLogFlow } returns MutableStateFlow(false)
        every { settingsRepository.isDataCloudSyncEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.syncModeBookFlow } returns MutableStateFlow("TWO_WAY")
        every { settingsRepository.syncModeTtsFlow } returns MutableStateFlow("TWO_WAY")
        every { settingsRepository.syncModeStatsFlow } returns MutableStateFlow("RESTORE_ONLY")
        every { settingsRepository.syncModeSettingsFlow } returns MutableStateFlow("TWO_WAY")
        every { settingsRepository.lastSuccessfulSyncTimeFlow } returns MutableStateFlow(0L)
        every { settingsRepository.syncIntervalMinutesFlow } returns MutableStateFlow(60L)
        every { settingsRepository.foregroundSyncIntervalMinutesFlow } returns MutableStateFlow(5L)
        every { settingsRepository.googleDriveFolderIdFlow } returns MutableStateFlow(null)
        every { settingsRepository.googleDriveFolderNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.syncTargetTypeFlow } returns MutableStateFlow("google_drive")
        every { settingsRepository.googleAuthTypeFlow } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM)
        every { settingsRepository.isCaregiverDeviceFlow } returns MutableStateFlow(false)
        every { settingsRepository.localFolderSafUriFlow } returns MutableStateFlow(null)
        every { settingsRepository.localFolderSafNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.hueBridgeIpFlow } returns MutableStateFlow("")
        every { settingsRepository.hueUsernameFlow } returns MutableStateFlow("")
        every { settingsRepository.hueCachedDevicesFlow } returns MutableStateFlow("")
        every { settingsRepository.isGeminiEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.geminiApiKeyFlow } returns MutableStateFlow(null)
        every { settingsRepository.useGeminiApiKeyFlow } returns MutableStateFlow(false)
        every { settingsRepository.isGeminiVerifiedFlow } returns MutableStateFlow(false)
        every { settingsRepository.spotifyUserDisplayNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.isNotificationReadingEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.monitoredNotificationAppsFlow } returns MutableStateFlow(emptySet())
        every { settingsRepository.autoReadModeFlow } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF)
        every { settingsRepository.autoReadOnlyInUserModeFlow } returns MutableStateFlow(true)
        every { settingsRepository.autoReadInStandbyFlow } returns MutableStateFlow(false)
        every { settingsRepository.themeModeFlow } returns MutableStateFlow("LIGHT")
        every { settingsRepository.keepScreenOnUserModeFlow } returns MutableStateFlow(true)
        every { settingsRepository.userModeScreenBehaviorFlow } returns MutableStateFlow("GRID")
        every { settingsRepository.statsRetentionDaysFlow } returns MutableStateFlow(30)
        every { settingsRepository.statsAggregationHoursFlow } returns MutableStateFlow(24)
        every { settingsRepository.onlyRecordHardwareStatsFlow } returns MutableStateFlow(false)
        every { settingsRepository.firebaseAnalyticsEnabledFlow } returns MutableStateFlow(true)
        every { settingsRepository.geminiTimeoutFlow } returns MutableStateFlow(10000L)
        every { settingsRepository.geminiRedoPredictionFlow } returns MutableStateFlow(false)
        every { settingsRepository.blockVolumeKeysFlow } returns MutableStateFlow(false)
        every { settingsRepository.speakerVolumeFlow } returns MutableStateFlow(100)
        every { settingsRepository.headphoneVolumeFlow } returns MutableStateFlow(100)
        every { settingsRepository.securityPinFlow } returns MutableStateFlow("")
        every { settingsRepository.securityPinTimeoutMinutesFlow } returns MutableStateFlow(30L)
        every { settingsRepository.isPinRequiredForDeletionFlow } returns MutableStateFlow(false)
        every { settingsRepository.isBiometricEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.isSecurityRequiredForEditFlow } returns MutableStateFlow(false)
        every { settingsRepository.isSecurityRequiredForSettingsFlow } returns MutableStateFlow(false)
        every { settingsRepository.isSecurityRequiredForAnalyticsFlow } returns MutableStateFlow(false)
        every { settingsRepository.startupBehaviorFlow } returns MutableStateFlow("BOOK_SELECTION")
        every { settingsRepository.logIgnoredActionsFlow } returns MutableStateFlow(false)
        every { settingsRepository.logStopActionsFlow } returns MutableStateFlow(false)
        every { settingsRepository.limitScanCyclesFlow } returns MutableStateFlow(false)
        every { settingsRepository.scanCycleLimitFlow } returns MutableStateFlow(2)
        every { settingsRepository.actionLogLimitFlow } returns MutableStateFlow(100)
        every { settingsRepository.forceSoftKeyboardFlow } returns MutableStateFlow(true)
        every { settingsRepository.ttsEngineFlow } returns MutableStateFlow(null)
        every { settingsRepository.elevenLabsApiKeyFlow } returns MutableStateFlow(null)
        every { settingsRepository.elevenLabsModelFlow } returns MutableStateFlow("eleven_multilingual_v2")
        every { settingsRepository.elevenLabsStabilityFlow } returns MutableStateFlow(0.5f)
        every { settingsRepository.elevenLabsSimilarityBoostFlow } returns MutableStateFlow(0.75f)
        every { settingsRepository.ttsPlaybackSpeedFlow } returns MutableStateFlow(1.0f)
        every { settingsRepository.maxCallDurationSecondsFlow } returns MutableStateFlow(300)
        every { settingsRepository.callDurationFeedbackIntervalSecondsFlow } returns MutableStateFlow(60)
        every { settingsRepository.outgoingCallIntroFlow } returns MutableStateFlow("")
        every { settingsRepository.incomingCallIntroFlow } returns MutableStateFlow("")
        every { settingsRepository.incomingCallScanLimitUserModeActiveFlow } returns MutableStateFlow(2)
        every { settingsRepository.incomingCallAutoActionUserModeActiveFlow } returns MutableStateFlow("NONE")
        every { settingsRepository.incomingCallDelayUserModeInactiveFlow } returns MutableStateFlow(10)
        every { settingsRepository.incomingCallAutoActionUserModeInactiveFlow } returns MutableStateFlow("NONE")
        every { settingsRepository.callAnnouncementAsCueFlow } returns MutableStateFlow(false)
        every { settingsRepository.autoEnableSpeakerphoneFlow } returns MutableStateFlow(true)
        every { settingsRepository.simulateCallsEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.hangUpPressesRequiredFlow } returns MutableStateFlow(1)
        every { settingsRepository.filterCallsNotInContactsFlow } returns MutableStateFlow(false)
        every { settingsRepository.syncModeLogsFlow } returns MutableStateFlow("RESTORE_ONLY")
        every { settingsRepository.syncLogsIntervalHoursFlow } returns MutableStateFlow(24)
        every { settingsRepository.lastLogsSyncTimeFlow } returns MutableStateFlow(0L)
        every { cloudSyncDelegate.isSyncing } returns MutableStateFlow(false)
        every { cloudSyncDelegate.userEmail } returns MutableStateFlow(null)

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            buttonUsageRepository = buttonUsageRepository,
            userModeSessionRepository = userModeSessionRepository,
            securityManager = securityManager,
            getPagesUseCase = getPagesUseCase,
            ttsDelegate = ttsDelegate,
            scanningDelegate = scanningDelegate,
            cloudSyncDelegate = cloudSyncDelegate,
            genAiDelegate = genAiDelegate,
            experimentalDelegate = experimentalDelegate,
            hueDelegate = hueDelegate,
            spotifyDelegate = spotifyDelegate,
            prefetchDelegate = prefetchDelegate,
            backupDelegate = backupDelegate,
            backgroundScheduler = backgroundScheduler,
            updateActiveBookNameUseCase = updateActiveBookNameUseCase,
            deleteBookUseCase = deleteBookUseCase,
            updateActionLogLimitUseCase = updateActionLogLimitUseCase,
            importExportManager = importExportManager,
            hueManager = hueManager,
            spotifyManager = spotifyManager,
            ttsHelper = ttsHelper,
            audioCacheRepository = audioCacheRepository,
            pageRepository = mockk(relaxed = true),
            syncLogProvider = mockk(relaxed = true),
            callActionProxy = { mockk(relaxed = true) },
            exportLogsUseCase = mockk(relaxed = true),
            rescheduleLogUploadUseCase = mockk(relaxed = true),
            performProfilesSyncUseCase = mockk(relaxed = true),
            authManager = mockk(relaxed = true)
        )

        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<Int>(), any()) } returns mockk(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Toast::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial values are exposed correctly from repo and delegates`() = runTest {
        assertEquals("de", viewModel.selectedLanguageTag.value)
        assertEquals(true, viewModel.autoStartScanning.value)
        assertEquals("en", viewModel.selectedAppLanguage.value)
    }

    @Test
    fun `actions are delegated to correct delegates`() {
        viewModel.setTtsLanguage("en-US")
        verify { ttsDelegate.setTtsLanguage("en-US") }

        viewModel.setAutoStartScanning(false)
        verify { scanningDelegate.setAutoStartScanning(false) }

        viewModel.signIn(mockk())
        verify { cloudSyncDelegate.signIn(any(), any()) }

        viewModel.activateGemini(mockk())
        verify { genAiDelegate.activateGemini(any(), any()) }
    }

    @Test
    fun `exportLocalBackup calls manager correctly`() = runTest {
        coEvery { importExportManager.exportBookToJson("test-book") } returns "{\"json\":true}"
        val result = viewModel.exportLocalBackup()
        assertEquals("{\"json\":true}", result)
    }

    @Test
    fun `importLocalBackup fails if bookId mismatch`() = runTest {
        val json = "{\"bookId\":\"wrong-id\"}"
        coEvery { importExportManager.extractBookIdFromJson(json) } returns "wrong-id"
        
        var errorMsg: String? = null
        viewModel.importLocalBackup(json, onSuccess = {}, onError = { errorMsg = it })
        
        assertEquals("Fehler: Buch-IDs stimmen nicht überein. Dieses Backup gehört zu einem anderen Buch.", errorMsg)
    }

    @Test
    fun `importLocalBackup calls manager if bookId matches`() = runTest {
        val json = "{\"bookId\":\"test-book\"}"
        coEvery { importExportManager.extractBookIdFromJson(json) } returns "test-book"
        coEvery { importExportManager.importFromJson(any(), any(), any(), any()) } returns Result.success(ImportResult(5))
        
        var successCalled = false
        viewModel.importLocalBackup(json, onSuccess = { successCalled = true }, onError = {})
        
        coVerify { importExportManager.importFromJson(json, "test-book", false, false) }
        assertEquals(true, successCalled)
    }

    @Test
    fun `importGlobalManualBackup calls cloud import correctly`() = runTest {
        val json = "{\"json\":true}"
        coEvery { importExportManager.importCloudBackup(json, null) } returns Result.success("new-book-id")
        
        var successId: String? = null
        viewModel.importGlobalManualBackup(json, onSuccess = { successId = it }, onError = {})
        
        coVerify { importExportManager.importCloudBackup(json, null) }
        assertEquals("new-book-id", successId)
    }

    @Test
    fun `resetMonitoredNotificationAppsToMessagingDefaults does not crash under test context`() = runTest {
        viewModel.resetMonitoredNotificationAppsToMessagingDefaults()
    }

    @Test
    fun `setCallDurationFeedbackIntervalSeconds updates repository`() {
        viewModel.setCallDurationFeedbackIntervalSeconds(30)
        verify { settingsRepository.callDurationFeedbackIntervalSeconds = 30 }
    }

    @Test
    fun `refreshHueDevicesCache success updates repository cache`() = runTest(testDispatcher) {
        every { settingsRepository.hueBridgeIp } returns "192.168.1.50"
        every { settingsRepository.hueUsername } returns "some-token"
        val mockDevices = listOf(
            com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice(id = "1", name = "Hue light 1")
        )
        coEvery { hueManager.getLocalLights(any(), any()) } returns mockDevices

        var success: Boolean? = null
        println("TEST DEBUG START")
        viewModel.refreshHueDevicesCache(silentOnFailure = false) {
            println("TEST DEBUG CALLBACK: $it")
            success = it
        }
        println("TEST DEBUG BEFORE ADVANCE")
        testDispatcher.scheduler.advanceUntilIdle()
        println("TEST DEBUG AFTER ADVANCE")

        verify { settingsRepository.hueCachedDevices = any() }
        assertEquals(true, success)
    }

    @Test
    fun `refreshHueDevicesCache failure keeps existing repository cache`() = runTest(testDispatcher) {
        every { settingsRepository.hueBridgeIp } returns "192.168.1.50"
        every { settingsRepository.hueUsername } returns "some-token"
        coEvery { hueManager.getLocalLights(any(), any()) } returns emptyList()

        var success: Boolean? = null
        viewModel.refreshHueDevicesCache(silentOnFailure = true) {
            success = it
        }

        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { settingsRepository.hueCachedDevices = any() }
        assertEquals(false, success)
    }

    @Test
    fun `editing a profile updates draft instead of writing to repository`() = runTest {
        val testProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "test-prof-id",
            name = "AAC User",
            config = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(autoStartScanning = true),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("test-prof-id") } returns testProfile

        viewModel.startEditingProfile("test-prof-id")
        assertEquals("test-prof-id", viewModel.editingProfileId.value)
        assertEquals("AAC User", viewModel.editingProfileName.value)
        assertEquals(false, viewModel.hasUnsavedChanges.value)

        // Modify autoStartScanning setting
        viewModel.setAutoStartScanning(false)
        
        // Ensure repository setter was NOT called
        verify(exactly = 0) { settingsRepository.autoStartScanning = any() }
        verify(exactly = 0) { scanningDelegate.setAutoStartScanning(any()) }
        assertEquals(false, viewModel.autoStartScanning.value)
        assertEquals(true, viewModel.hasUnsavedChanges.value)
    }

    @Test
    fun `saving a profile updates repository and triggers sync`() = runTest {
        val testProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "test-prof-id",
            name = "AAC User",
            config = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(autoStartScanning = true),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("test-prof-id") } returns testProfile

        viewModel.startEditingProfile("test-prof-id")
        viewModel.setAutoStartScanning(false)
        viewModel.saveEditingProfile()

        // Should write update to settingsRepository
        coVerify { settingsRepository.updateProfile(any()) }
        assertEquals(null, viewModel.editingProfileId.value)
    }

    @Test
    fun `canceling profile edit discards draft changes`() = runTest {
        val testProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "test-prof-id",
            name = "AAC User",
            config = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(autoStartScanning = true),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("test-prof-id") } returns testProfile

        viewModel.startEditingProfile("test-prof-id")
        viewModel.setAutoStartScanning(false)
        viewModel.cancelEditingProfile()

        assertEquals(null, viewModel.editingProfileId.value)
        coVerify(exactly = 0) { settingsRepository.updateProfile(any()) }
    }
}
