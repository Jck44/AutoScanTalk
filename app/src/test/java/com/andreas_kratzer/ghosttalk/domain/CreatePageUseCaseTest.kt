package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
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
    private val templateRepository: TemplateRepository = mockk(relaxed = true)
    private val useCase = CreatePageUseCase(pageRepository, settingsRepository, templateRepository)

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

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws exception if grid exceeds 6x6`() = runTest {
        useCase.execute("Too Big", 7, 6, "book1", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws exception if template size exceeds 6x6`() = runTest {
        val badTemplate = PageTemplate(
            id = "bad", name = "Bad", rows = 6, columns = 7, buttonConfigs = emptyList(), isBuiltIn = false
        )
        coEvery { templateRepository.getById("bad") } returns badTemplate

        useCase.execute("Template Too Big", 1, 1, "book1", emptyList(), templateId = "bad")
    }

    @Test
    fun `execute applies template and replaces empty home id`() = runTest {
        val action = NavigateToPageButtonAction("") // Empty pageId
        val templateConfig = ButtonConfig("b1", "Home", buttonAction = action, auditoryCue = null)
        val template = PageTemplate(
            id = "t1", name = "Test", rows = 2, columns = 2, buttonConfigs = listOf(templateConfig), isBuiltIn = false
        )
        
        coEvery { templateRepository.getById("t1") } returns template
        every { settingsRepository.defaultStartPageId } returns "global-home"

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        useCase.execute("From Template", 1, 1, "book1", emptyList(), templateId = "t1")

        val page = pageSlot.captured
        assertEquals(2, page.rows)
        assertEquals(2, page.columns)
        assertEquals(1, page.buttonConfigs.size)

        val replacedAction = page.buttonConfigs[0]?.buttonAction as NavigateToPageButtonAction
        assertEquals("global-home", replacedAction.pageId)
    }
}
