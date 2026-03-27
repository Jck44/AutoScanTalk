package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActionLogLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActiveBookNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.DeleteBookUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var bookRepository: BookRepository
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var securityManager: SecurityManager
    private lateinit var getPagesUseCase: GetPagesUseCase
    
    private lateinit var ttsDelegate: TtsSettingsDelegate
    private lateinit var scanningDelegate: ScanningSettingsDelegate
    private lateinit var cloudSyncDelegate: CloudSyncSettingsDelegate
    private lateinit var genAiDelegate: GenAiSettingsDelegate
    private lateinit var experimentalDelegate: ExperimentalSettingsDelegate
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var hueManager: PhilipsHueManager
    private lateinit var updateActionLogLimitUseCase: UpdateActionLogLimitUseCase
    private lateinit var updateActiveBookNameUseCase: UpdateActiveBookNameUseCase
    private lateinit var deleteBookUseCase: DeleteBookUseCase
    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
    private lateinit var audioCacheRepository: com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
    
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

        // Mock common flows
        every { settingsRepository.activeBookId } returns "test-book"
        every { getPagesUseCase.execute(any()) } returns flowOf(emptyList())
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
            bookRepository = bookRepository,
            buttonUsageRepository = buttonUsageRepository,
            securityManager = securityManager,
            getPagesUseCase = getPagesUseCase,
            ttsDelegate = ttsDelegate,
            scanningDelegate = scanningDelegate,
            cloudSyncDelegate = cloudSyncDelegate,
            genAiDelegate = genAiDelegate,
            experimentalDelegate = experimentalDelegate,
            updateActiveBookNameUseCase = updateActiveBookNameUseCase,
            deleteBookUseCase = deleteBookUseCase,
            updateActionLogLimitUseCase = updateActionLogLimitUseCase,
            importExportManager = importExportManager,
            hueManager = hueManager,
            ttsHelper = ttsHelper,
            audioCacheRepository = audioCacheRepository
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
        coEvery { importExportManager.importFromJson(any(), any(), any(), any()) } returns Result.success(5)
        
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
}
