package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.feature.settings.domain.DeleteBookUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActionLogLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActiveBookNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsSettingsDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsPrefetchTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var securityManager: SecurityManager
    private lateinit var getPagesUseCase: GetPagesUseCase
    
    private lateinit var ttsDelegate: TtsSettingsDelegate
    private lateinit var scanningDelegate: ScanningSettingsDelegate
    private lateinit var cloudSyncDelegate: CloudSyncSettingsDelegate
    private lateinit var genAiDelegate: GenAiSettingsDelegate
    private lateinit var experimentalDelegate: ExperimentalSettingsDelegate
    private lateinit var importExportManager: PageImportExportProvider
    private lateinit var hueManager: PhilipsHueManager
    private lateinit var updateActionLogLimitUseCase: UpdateActionLogLimitUseCase
    private lateinit var updateActiveBookNameUseCase: UpdateActiveBookNameUseCase
    private lateinit var deleteBookUseCase: DeleteBookUseCase
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var audioCacheRepository: AudioCacheRepository
    
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)
        securityManager = mockk(relaxed = true)
        getPagesUseCase = mockk(relaxed = true)
        
        ttsDelegate = mockk(relaxed = true)
        scanningDelegate = mockk(relaxed = true)
        cloudSyncDelegate = mockk(relaxed = true)
        genAiDelegate = mockk(relaxed = true)
        experimentalDelegate = mockk(relaxed = true)
        importExportManager = mockk(relaxed = true)
        hueManager = mockk(relaxed = true)
        updateActionLogLimitUseCase = mockk(relaxed = true)
        updateActiveBookNameUseCase = mockk(relaxed = true)
        deleteBookUseCase = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        audioCacheRepository = mockk(relaxed = true)

        every { settingsRepository.activeBookIdFlow } returns MutableStateFlow("book1")
        every { settingsRepository.activeProfileIdFlow } returns MutableStateFlow("profile-default")
        every { settingsRepository.getAllProfilesFlow() } returns MutableStateFlow(emptyList())
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
        every { settingsRepository.syncModeLogsFlow } returns MutableStateFlow("RESTORE_ONLY")
        every { settingsRepository.syncLogsIntervalHoursFlow } returns MutableStateFlow(24)
        every { settingsRepository.lastLogsSyncTimeFlow } returns MutableStateFlow(0L)
        
        val hueDelegate = mockk<com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.HueSettingsDelegate>(relaxed = true)
        val spotifyDelegate = mockk<com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.SpotifySettingsDelegate>(relaxed = true)
        val prefetchDelegate = com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsPrefetchSettingsDelegate(ttsHelper)
        prefetchDelegate.initialize(kotlinx.coroutines.CoroutineScope(testDispatcher))
        val backupDelegate = mockk<com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.BackupSettingsDelegate>(relaxed = true)
        val backgroundScheduler = mockk<com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler>(relaxed = true)

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            buttonUsageRepository = buttonUsageRepository,
            userModeSessionRepository = mockk(relaxed = true),
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
            spotifyManager = mockk(relaxed = true),
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calculatePrefetchStats correctly counts unique, duplicate and words`() = runTest {
        // Prepare mock pages
        val page1 = Page(id = "p1", bookId = "book1", name = "P1", buttonConfigs = listOf(
            ButtonConfig(id = "b1", label = "Hello", spokenText = null, buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            ButtonConfig(id = "b2", label = "B2", spokenText = "World", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            ButtonConfig(id = "b3", label = "Hello", spokenText = null, buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()) // Duplicate
        ))
        val page2 = Page(id = "p2", bookId = "book1", name = "P2", buttonConfigs = listOf(
            ButtonConfig(id = "b4", label = "Hello World", spokenText = null, buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            null // Empty slot
        ))

        // Set up selected pages
        viewModel.selectAllPagesForPrefetch(listOf(page1, page2))
        
        // Mock ttsHelper.isCached
        every { ttsHelper.isCached(any()) } returns false
        every { ttsHelper.isCached("Hello") } returns true

        // Execute calculation
        val stats = viewModel.calculatePrefetchStats(listOf(page1, page2))

        // Verify stats
        assertEquals(4, stats.totalButtons)
        assertEquals(3, stats.uniqueStrings) // "Hello", "World", "Hello World"
        assertEquals(1, stats.duplicateStrings) // The second "Hello"
        assertEquals(4, stats.totalWords) // "Hello" (1) + "World" (1) + "Hello World" (2)
        assertEquals(1, stats.alreadyCached) // "Hello" is cached
        assertEquals(21, stats.totalCharacters) // "Hello"(5) + "World"(5) + "Hello World"(11) = 21
    }

    @Test
    fun `startPrefetch calls ttsHelper prefetch for non-cached unique strings`() = runTest {
        val page = Page(id = "p1", bookId = "book1", name = "P1", buttonConfigs = listOf(
            ButtonConfig(id = "b1", label = "A", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            ButtonConfig(id = "b2", label = "B", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            ButtonConfig(id = "b3", label = "A", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()) // Duplicate
        ))

        viewModel.selectAllPagesForPrefetch(listOf(page))
        
        // A is cached, B is not
        every { ttsHelper.isCached("A") } returns true
        every { ttsHelper.isCached("B") } returns false
        io.mockk.coEvery { ttsHelper.prefetch(any()) } returns Unit

        viewModel.startPrefetch(listOf(page))
        
        // Wait for the coroutine in startPrefetch (IO dispatcher)
        // Since we use StandardTestDispatcher and set as Main, we might need to advance.
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { ttsHelper.prefetch("B") }
        io.mockk.coVerify(exactly = 0) { ttsHelper.prefetch("A") }
        
        assertEquals(false, viewModel.isPrefetching.value)
        assertEquals(1f, viewModel.prefetchProgress.value)
    }
}
