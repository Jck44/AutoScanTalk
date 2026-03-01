package com.example.gostalk.ui

import android.app.Application
import com.example.gostalk.core.PageImportExportManager
import com.example.gostalk.core.ScannerEngine
import com.example.gostalk.core.util.TestLogger
import com.example.gostalk.data.PageRepository
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        
        // Mock default flows mapped inside ViewModel init
        every { settingsRepository.ttsLanguageFlow } returns MutableStateFlow("default")
        every { settingsRepository.ttsVoiceNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow(1000L)
        every { settingsRepository.scanDelayMillis } returns 1000L
        every { settingsRepository.autoStartScanning } returns false
        every { settingsRepository.persistActionLogs } returns false
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow(false)
        every { settingsRepository.actionLogsStorage } returns null
        every { settingsRepository.actionLogsStorageFlow } returns MutableStateFlow(null)
        every { pageRepository.getAllPagesFlow() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createNewPage injects a Home Button at the last grid slot pointing to defaultStartPageId`() = runTest {
        val expectedStartPageId = "page-home-999"
        every { settingsRepository.defaultStartPageId } returns expectedStartPageId
        
        // We need to capture the Page object being inserted into the DB
        val insertedPageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(insertedPageSlot)) } returns Unit
        
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)

        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts, logger = TestLogger, importExportManager = importExportManager)
        viewModel.createNewPage("Test Page", rows = 2, columns = 2, bookId = "test-book-id")

        // Let Coroutines process
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { pageRepository.insertPage(any()) }
        val capturedPage = insertedPageSlot.captured
        
        // 2x2 = 4 slots (indices 0, 1, 2, 3)
        assertEquals(4, capturedPage.buttonConfigs.size)
        
        // Ensure other slots are null
        assertNull(capturedPage.buttonConfigs[0])
        assertNull(capturedPage.buttonConfigs[1])
        assertNull(capturedPage.buttonConfigs[2])
        
        // Verify last slot has the injected button
        val lastButton = capturedPage.buttonConfigs[3]
        assertNotNull(lastButton)
        assertEquals("zurück zum Start", lastButton!!.label)
        
        // Verify it navigates to the expected defaultStartPageId
        assertTrue(lastButton.buttonAction is NavigateToPageButtonAction)
        val navAction = lastButton.buttonAction as NavigateToPageButtonAction
        assertEquals(expectedStartPageId, navAction.pageId)
    }

    @Test
    fun `init loads action logs when persistActionLogs is true`() = runTest {
        val savedLogsJson = "[\"[12:00:00] First Log\",\"[12:05:00] Second Log\"]"
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns savedLogsJson
        
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts, logger = TestLogger, importExportManager = importExportManager)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(2, viewModel.lastActions.value.size)
        assertEquals("[12:00:00] First Log", viewModel.lastActions.value[0])
    }

    @Test
    fun `logAction saves to storage when persistActionLogs is true`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns null
        
        // We capture what gets set to actionLogsStorage
        val storageSlot = slot<String>()
        every { settingsRepository.actionLogsStorage = capture(storageSlot) } returns Unit

        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts, logger = TestLogger, importExportManager = importExportManager)
        
        testDispatcher.scheduler.advanceUntilIdle()

        val btnConfig = com.example.gostalk.model.ButtonConfig(
            id = "b1",
            label = "Test Action",
            buttonAction = com.example.gostalk.model.SpeakTextButtonAction("Test Speech"),
            auditoryCue = null
        )
        viewModel.loadPage(Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = listOf(btnConfig)))
        viewModel.activateButtonAtIndex(0)
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.lastActions.value.isNotEmpty())
        assertTrue(viewModel.lastActions.value[0].contains("Test Speech"))
        
        assertTrue(storageSlot.isCaptured)
        assertTrue(storageSlot.captured.contains("Test Speech"))
    }

    @Test
    fun `clearActionLog clears state and storage`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        
        val storageSlot = slot<String?>()
        every { settingsRepository.actionLogsStorage = captureNullable(storageSlot) } returns Unit

        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts, logger = TestLogger, importExportManager = importExportManager)
        
        testDispatcher.scheduler.runCurrent()

        viewModel.clearActionLogs()
        testDispatcher.scheduler.runCurrent()
        
        assertTrue(viewModel.lastActions.value.isEmpty())
        assertTrue("Storage should have been updated", storageSlot.isCaptured)
        val captured = storageSlot.captured
        assertTrue(captured == null || captured == "[]")
    }

    @Test
    fun `importFromJson delegates to importExportManager`() = runTest {
        val jsonString = "{}"
        coEvery { importExportManager.importFromJson(any(), any()) } returns Result.success(1)

        var successCalled = false
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts, logger = TestLogger, importExportManager = importExportManager)
        viewModel.importFromJson(jsonString, "test_book", onSuccess = { successCalled = true }, onError = {})
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Success callback should be called", successCalled)
        coVerify { importExportManager.importFromJson(jsonString, "test_book") }
    }
}
