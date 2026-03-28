package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
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
        
        viewModel = SettingsViewModel(
            application, settingsRepository, bookRepository, buttonUsageRepository, 
            securityManager, getPagesUseCase, ttsDelegate, scanningDelegate, 
            cloudSyncDelegate, genAiDelegate, experimentalDelegate, 
            updateActiveBookNameUseCase, deleteBookUseCase, updateActionLogLimitUseCase, 
            importExportManager, hueManager, ttsHelper, audioCacheRepository
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
