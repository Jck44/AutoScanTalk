package com.example.gostalk.ui

import android.app.Application
import com.example.gostalk.data.PageRepository
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.model.Page
import com.example.gostalk.model.NavigateToPageButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        
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

        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts)
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
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts)
        
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
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts)
        
        testDispatcher.scheduler.advanceUntilIdle()

        // logAction is private, but triggerable via ActionExecutor's executeButtonAction for a simple SpeakText action without ttsReady
        val btnConfig = com.example.gostalk.model.ButtonConfig(
            id = "b1",
            label = "Test Action",
            buttonAction = com.example.gostalk.model.SpeakTextButtonAction("Test Speech"),
            auditoryCue = null
        )
        viewModel.activateButtonAtIndex(0) // this is slightly complex to set up due to internal page state, let's use reflection to call logAction for a direct unit test, or just mock the page

        // A better way without reflection: Load a dummy page, then activate button 0
        val dummyPage = Page("p1", "b1", "Test", 1, 1, listOf(btnConfig))
        viewModel.loadPage(dummyPage)
        viewModel.activateButtonAtIndex(0)
        
        testDispatcher.scheduler.advanceUntilIdle()

        // It should have logged "Sprechen (TTS nicht bereit): "Test Speech""
        assertTrue(viewModel.lastActions.value.isNotEmpty())
        assertTrue(viewModel.lastActions.value[0].contains("Test Speech"))
        
        // Storage should have been updated
        assertTrue(storageSlot.isCaptured)
        assertTrue(storageSlot.captured.contains("Test Speech"))
    }

    @Test
    fun `clearActionLog clears state and storage`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        
        val storageSlot = slot<String?>()
        every { settingsRepository.actionLogsStorage = captureNullable(storageSlot) } returns Unit

        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(application, pageRepository, settingsRepository, ttsHelper = mockTts)
        
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearActionLogs()
        
        assertTrue(viewModel.lastActions.value.isEmpty())
        assertTrue(storageSlot.isCaptured)
        // actionLogsStorage should be set to an empty JSON array "[]" or null
        val captured = storageSlot.captured
        assertTrue(captured == null || captured == "[]")
    }
}
