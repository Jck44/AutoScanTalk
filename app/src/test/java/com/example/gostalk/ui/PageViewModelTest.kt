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
}
