package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var driveAuthManager: DriveAuthManager
    private lateinit var cloudSyncUseCase: CloudSyncUseCase
    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var tempTtsHelper: TextToSpeechHelper
    private lateinit var geminiUseCaseFactory: com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        driveAuthManager = mockk(relaxed = true)
        cloudSyncUseCase = mockk(relaxed = true)
        geminiUseCaseFactory = mockk(relaxed = true)
        tempTtsHelper = mockk(relaxed = true)
        audioDeviceManager = mockk(relaxed = true)

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

        // Mock DriveAuthManager flow
        every { driveAuthManager.userEmail } returns MutableStateFlow(null)

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            driveAuthManager = driveAuthManager,
            cloudSyncUseCase = cloudSyncUseCase,
            geminiUseCaseFactory = geminiUseCaseFactory,
            tempTtsHelper = tempTtsHelper,
            audioDeviceManager = audioDeviceManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}
