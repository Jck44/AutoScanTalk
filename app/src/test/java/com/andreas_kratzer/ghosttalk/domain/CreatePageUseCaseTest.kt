package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePageUseCaseTest {

    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val useCase = CreatePageUseCase(pageRepository, settingsRepository)

    @Test
    fun `execute creates page with correct dimensions`() = runTest {
        every { settingsRepository.defaultStartPageId } returns null

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        useCase.execute("TestPage", 3, 4, "book1", emptyList())

        val page = pageSlot.captured
        assertEquals("TestPage", page.name)
        assertEquals(3, page.rows)
        assertEquals(4, page.columns)
        assertEquals("book1", page.bookId)
        assertEquals(12, page.buttonConfigs.size)
    }

    @Test
    fun `execute adds home button when start page is set`() = runTest {
        every { settingsRepository.defaultStartPageId } returns "home-page-id"

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        useCase.execute("New", 2, 2, "book1", emptyList())

        val page = pageSlot.captured
        // Home button is at the last position
        val lastButton = page.buttonConfigs[3]
        assertNotNull(lastButton)
        assertTrue(lastButton!!.buttonAction is NavigateToPageButtonAction)
        assertEquals("home-page-id", (lastButton.buttonAction as NavigateToPageButtonAction).pageId)
        assertTrue(lastButton.auditoryCue is AuditoryCue.TextToSpeechCue)
    }

    @Test
    fun `execute adds home button from first existing page when no default set`() = runTest {
        every { settingsRepository.defaultStartPageId } returns null
        val existingPages = listOf(
            Page(id = "existing-1", bookId = "book1", name = "First", rows = 1, columns = 1, buttonConfigs = emptyList())
        )

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        useCase.execute("New", 1, 2, "book1", existingPages)

        val lastButton = pageSlot.captured.buttonConfigs[1]
        assertNotNull(lastButton)
        assertEquals("existing-1", (lastButton!!.buttonAction as NavigateToPageButtonAction).pageId)
    }

    @Test
    fun `execute creates all null buttons when no home page available`() = runTest {
        every { settingsRepository.defaultStartPageId } returns null

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        useCase.execute("Empty", 1, 2, "book1", emptyList())

        val page = pageSlot.captured
        // All buttons should be null except possibly the last one
        // Since no home page, even the last is null
        assertNull(page.buttonConfigs[0])
        assertNull(page.buttonConfigs[1])
    }
}
