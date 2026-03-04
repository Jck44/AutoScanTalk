package com.andreas_kratzer.ghosttalk.ui.settings

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.coVerify
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var cloudSyncUseCase: CloudSyncUseCase
    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var tempTtsHelper: TextToSpeechHelper
    private lateinit var geminiUseCaseFactory: com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
    private lateinit var workManager: androidx.work.WorkManager
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        cloudSyncUseCase = mockk(relaxed = true)
        geminiUseCaseFactory = mockk(relaxed = true)
        tempTtsHelper = mockk(relaxed = true)
        audioDeviceManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)

        // Mock default flows and properties from SettingsRepository
        every { settingsRepository.ttsLanguage } returns "de"
        every { settingsRepository.ttsVoiceName } returns null
        every { settingsRepository.autoStartScanning } returns true
        every { settingsRepository.scanDelayMillis } returns 2000L
        every { settingsRepository.resumeScanningFromStart } returns true
        every { settingsRepository.defaultScanPattern } returns "linear"
        every { settingsRepository.defaultStartPageId } returns null
        every { settingsRepository.ttsAudioDeviceAddress } returns null
        every { settingsRepository.cuesAudioDeviceAddress } returns null
        every { settingsRepository.persistActionLogs } returns false
        every { settingsRepository.switchActivationKey } returns "Space"
        every { settingsRepository.volumeKeysActivate } returns false
        every { settingsRepository.holdingTimeMillis } returns 0L
        every { settingsRepository.isCloudSyncEnabled } returns false
        every { settingsRepository.isGeminiEnabled } returns false
        every { settingsRepository.appLanguage } returns "en"
        every { settingsRepository.syncIntervalMinutes } returns 15L
        every { settingsRepository.syncMode } returns "TWO_WAY"
        every { settingsRepository.smartPredictionDelayMillisFlow } returns MutableStateFlow(2000L)
        every { settingsRepository.bluetoothDelayFlow } returns MutableStateFlow(1500L)
        every { settingsRepository.lastSuccessfulSyncTimeFlow } returns MutableStateFlow(0L)
        every { settingsRepository.ttsVolumeMultiplierFlow } returns MutableStateFlow<Float>(1.0f)
        every { settingsRepository.cuesVolumeMultiplierFlow } returns MutableStateFlow<Float>(1.0f)
        // ttsModeFlow removed

        // Mock GoogleAuthManager flow
        every { googleAuthManager.userEmail } returns MutableStateFlow(null)

        // Mock static Android methods that throw in local JVM tests
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        io.mockk.mockkStatic(android.widget.Toast::class)
        val mockToast = mockk<android.widget.Toast>(relaxed = true)
        every { android.widget.Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            googleAuthManager = googleAuthManager,
            cloudSyncUseCase = cloudSyncUseCase,
            geminiUseCaseFactory = geminiUseCaseFactory,
            tempTtsHelper = tempTtsHelper,
            audioDeviceManager = audioDeviceManager,
            workManager = workManager,
            buttonUsageRepository = buttonUsageRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
        io.mockk.clearAllMocks()
    }

    @Test
    fun testInitialValuesLoadedFromRepository() = runTest {
        // Assert that the viewmodel initialized its stateflows with repo values
        assertEquals("de", viewModel.selectedLanguageTag.value)
        assertEquals("2000", viewModel.scanDelayInput.value)
        assertEquals(true, viewModel.autoStartScanning.value)
        assertEquals("linear", viewModel.defaultScanPattern.value)
        assertEquals("Space", viewModel.switchActivationKey.value)
        assertEquals("en", viewModel.selectedAppLanguage.value)
    }

    @Test
    fun testSetScanDelayInput_FiltersLettersAndValidates() = runTest {
        // Test filtering of non-digits
        viewModel.setScanDelayInput("a1b0c00d")
        assertEquals("1000", viewModel.scanDelayInput.value)
        verify { settingsRepository.scanDelayMillis = 1000L }

        // Test boundary limits (delay shouldn't be saved if < 100ms)
        viewModel.setScanDelayInput("50")
        assertEquals("50", viewModel.scanDelayInput.value) // the UI holds the typed value
        verify(exactly = 0) { settingsRepository.scanDelayMillis = 50L } // but it doesn't get saved
    }

    @Test
    fun testSetTtsLanguage_ResetsVoice() = runTest {
        // Setting a new language should clear the voice, as it's language-dependent
        viewModel.setTtsLanguage("en-US")
        
        assertEquals("en-US", viewModel.selectedLanguageTag.value)
        verify { settingsRepository.ttsLanguage = "en-US" }
        
        assertEquals(null, viewModel.selectedVoiceName.value)
        verify { settingsRepository.ttsVoiceName = null }
    }

    @Test
    fun testSetSyncIntervalMinutesInput_FiltersLettersAndValidates() = runTest {
        // Test filtering of non-digits
        viewModel.setSyncIntervalMinutesInput("1a5b")
        assertEquals("15", viewModel.syncIntervalMinutesInput.value)
        verify { settingsRepository.syncIntervalMinutes = 15L }

        // Test boundary limits (must be >= 15 for WorkManager constraint)
        viewModel.setSyncIntervalMinutesInput("5")
        // The UI should hold the clamped value
        assertEquals("15", viewModel.syncIntervalMinutesInput.value)
        verify(exactly = 2) { settingsRepository.syncIntervalMinutes = 15L } 
    }

    @Test
    fun testSetSyncMode() = runTest {
        viewModel.setSyncMode("BACKUP_ONLY")
        assertEquals("BACKUP_ONLY", viewModel.syncMode.value)
        verify { settingsRepository.syncMode = "BACKUP_ONLY" }
    }

    @Test
    fun testSetCloudSyncEnabled_schedulesOrCancelsWork() = runTest {
        viewModel.setCloudSyncEnabled(true)
        verify { settingsRepository.isCloudSyncEnabled = true }
        verify { workManager.enqueueUniquePeriodicWork("CloudSyncWorker", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, any()) }

        viewModel.setCloudSyncEnabled(false)
        verify { settingsRepository.isCloudSyncEnabled = false }
        verify { workManager.cancelUniqueWork("CloudSyncWorker") }
    }

    @Test
    fun testBackupNow() = runTest {
        val driveMock = mockk<Drive>()
        val bookId = "test-book"
        every { settingsRepository.activeBookId } returns bookId
        every { googleAuthManager.getGoogleCredential() } returns mockk(relaxed = true)

        viewModel.backupNow(driveMock)
        advanceUntilIdle()
        coVerify { cloudSyncUseCase.syncBook(driveMock, bookId, com.andreas_kratzer.ghosttalk.domain.SyncMode.BACKUP_ONLY) }
    }

    @Test
    fun testRestoreNow() = runTest {
        val driveMock = mockk<Drive>()
        val bookId = "test-book"
        every { settingsRepository.activeBookId } returns bookId
        every { googleAuthManager.getGoogleCredential() } returns mockk(relaxed = true)

        viewModel.restoreNow(driveMock)
        advanceUntilIdle()
        coVerify { cloudSyncUseCase.syncBook(driveMock, bookId, com.andreas_kratzer.ghosttalk.domain.SyncMode.RESTORE_ONLY) }
    }

    @Test
    fun testSetThemeMode() = runTest {
        viewModel.setThemeMode("DARK")
        assertEquals("DARK", viewModel.themeMode.value)
        verify { settingsRepository.themeMode = "DARK" }
    }

    @Test
    fun testAudioDeviceList_includesGhostEntries() = runTest {
        val activeDevice = com.andreas_kratzer.ghosttalk.model.AudioOutputDevice("mac1", "Active Speaker", 0, true)
        val cachedMac = "mac2"
        val cachedName = "Old Bluetooth"
        
        every { audioDeviceManager.getAvailableOutputDevices() } returns listOf(activeDevice)
        every { settingsRepository.getDeviceName(any()) } returns cachedName
        every { settingsRepository.ttsAudioDeviceAddress } returns cachedMac
        
        // Trigger a refresh/load
        viewModel.refresh()
        
        val devices = viewModel.availableAudioDevices.value
        assertEquals(2, devices.size)
        // Check if the ghost entry is present
        val ghost = devices.find { it.address == cachedMac }
        assert(ghost != null)
        assert(ghost?.name?.contains("(Inaktiv)") == true)
    }

    @Test
    fun testSetGeminiEnabled() = runTest {
        viewModel.setGeminiEnabled(true)
        assertEquals(true, viewModel.isGeminiEnabled.value)
        verify { settingsRepository.isGeminiEnabled = true }
    }

    @Test
    fun testActivateGemini_success() = runTest {
        val geminiMock = mockk<com.andreas_kratzer.ghosttalk.domain.GeminiUseCase>(relaxed = true)
        every { geminiUseCaseFactory.create(any()) } returns geminiMock
        io.mockk.coEvery { geminiMock.generateResponse("Ping") } returns "Pong"

        viewModel.activateGemini(application)
        advanceUntilIdle()

        verify { settingsRepository.isGeminiEnabled = true }
        assertEquals(true, viewModel.isGeminiEnabled.value)
    }

    @Test
    fun testActivateGemini_failure() = runTest {
        val geminiMock = mockk<com.andreas_kratzer.ghosttalk.domain.GeminiUseCase>(relaxed = true)
        every { geminiUseCaseFactory.create(any()) } returns geminiMock
        io.mockk.coEvery { geminiMock.generateResponse("Ping") } throws Exception("API Error")
        io.mockk.coEvery { geminiMock.listModels() } returns "[]"

        viewModel.activateGemini(application)
        advanceUntilIdle()

        // Should not enable if test call fails
        verify(exactly = 0) { settingsRepository.isGeminiEnabled = true }
    }

    @Test
    fun testSetTtsVolumeMultiplier() = runTest {
        val volumeFlow = MutableStateFlow(1.0f)
        every { settingsRepository.ttsVolumeMultiplierFlow } returns volumeFlow
        
        viewModel.setTtsVolumeMultiplier(1.5f)
        testDispatcher.scheduler.advanceUntilIdle() // Ensure effects are processed
        
        assertEquals(1.5f, viewModel.ttsVolumeMultiplier.value)
        verify { settingsRepository.ttsVolumeMultiplier = 1.5f }
    }

    @Test
    fun testSetCuesVolumeMultiplier() = runTest {
        val volumeFlow = MutableStateFlow(1.0f)
        every { settingsRepository.cuesVolumeMultiplierFlow } returns volumeFlow

        viewModel.setCuesVolumeMultiplier(0.8f)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(0.8f, viewModel.cuesVolumeMultiplier.value)
        verify { settingsRepository.cuesVolumeMultiplier = 0.8f }
    }
}
