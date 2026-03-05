package com.andreas_kratzer.ghosttalk.ui.settings

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.TtsSettingsDelegate
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
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    
    private lateinit var ttsDelegate: TtsSettingsDelegate
    private lateinit var scanningDelegate: ScanningSettingsDelegate
    private lateinit var cloudSyncDelegate: CloudSyncSettingsDelegate
    private lateinit var genAiDelegate: GenAiSettingsDelegate
    
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)
        
        ttsDelegate = mockk(relaxed = true)
        scanningDelegate = mockk(relaxed = true)
        cloudSyncDelegate = mockk(relaxed = true)
        genAiDelegate = mockk(relaxed = true)

        // Mock common flows
        every { settingsRepository.ttsLanguageFlow } returns MutableStateFlow("de")
        every { settingsRepository.ttsVoiceNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.autoStartScanningFlow } returns MutableStateFlow(true)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow(2000L)
        every { settingsRepository.appLanguageFlow } returns MutableStateFlow("en")
        every { cloudSyncDelegate.isSyncing } returns MutableStateFlow(false)
        every { cloudSyncDelegate.userEmail } returns MutableStateFlow(null)

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            ttsDelegate = ttsDelegate,
            scanningDelegate = scanningDelegate,
            cloudSyncDelegate = cloudSyncDelegate,
            genAiDelegate = genAiDelegate
        )
    }

    @After
    fun tearDown() {
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
}
