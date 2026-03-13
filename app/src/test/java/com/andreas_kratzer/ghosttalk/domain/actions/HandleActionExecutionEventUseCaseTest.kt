package com.andreas_kratzer.ghosttalk.domain.actions

import android.content.Intent
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HandleActionExecutionEventUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: HandleActionExecutionEventUseCase

    @Before
    fun setup() {
        pageRepository = mockk()
        settingsRepository = mockk()
        useCase = HandleActionExecutionEventUseCase(pageRepository, settingsRepository)
    }

    @Test
    fun `NavigateToPage returns LoadPage effect when page exists`() = runTest {
        val pageId = "testPageId"
        val page = mockk<Page> {
            every { name } returns "TestPage"
        }
        every { settingsRepository.showPageIdInLog } returns false
        coEvery { pageRepository.getPageById(pageId) } returns page

        val event = ActionExecutor.ExecutionEvent.NavigateToPage(pageId)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.LoadPage)
        val loadPageEffect = effect as HandleActionExecutionEventUseCase.Effect.LoadPage
        assertEquals(page, loadPageEffect.page)
        assertEquals("Navigiert zu Seite: TestPage", loadPageEffect.logMessage)
    }

    @Test
    fun `NavigateToPage returns LoadPage effect with ID suffix when setting is enabled`() = runTest {
        val pageId = "testPageId"
        val page = mockk<Page> {
            every { name } returns "TestPage"
        }
        every { settingsRepository.showPageIdInLog } returns true
        coEvery { pageRepository.getPageById(pageId) } returns page

        val event = ActionExecutor.ExecutionEvent.NavigateToPage(pageId)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.LoadPage)
        val loadPageEffect = effect as HandleActionExecutionEventUseCase.Effect.LoadPage
        assertEquals("Navigiert zu Seite: TestPage (ID: testPageId)", loadPageEffect.logMessage)
    }

    @Test
    fun `NavigateToPage returns SpeakError effect when page does not exist`() = runTest {
        val pageId = "missingPageId"
        every { settingsRepository.showPageIdInLog } returns false
        coEvery { pageRepository.getPageById(pageId) } returns null

        val event = ActionExecutor.ExecutionEvent.NavigateToPage(pageId)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.SpeakError)
        val errorEffect = effect as HandleActionExecutionEventUseCase.Effect.SpeakError
        assertEquals(R.string.error_page_not_found, errorEffect.messageResId)
        assertEquals("Fehler: Seite nicht gefunden.", errorEffect.logMessage)
    }

    @Test
    fun `NavigateToPage returns SpeakError effect with ID when page does not exist and setting is enabled`() = runTest {
        val pageId = "missingPageId"
        every { settingsRepository.showPageIdInLog } returns true
        coEvery { pageRepository.getPageById(pageId) } returns null

        val event = ActionExecutor.ExecutionEvent.NavigateToPage(pageId)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.SpeakError)
        val errorEffect = effect as HandleActionExecutionEventUseCase.Effect.SpeakError
        assertEquals("Fehler: Seite mit ID 'missingPageId' nicht gefunden.", errorEffect.logMessage)
    }

    @Test
    fun `Log event returns LogAction effect`() = runTest {
        val message = "Test log message"
        val event = ActionExecutor.ExecutionEvent.Log(message)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.LogAction)
        assertEquals(message, (effect as HandleActionExecutionEventUseCase.Effect.LogAction).message)
    }

    @Test
    fun `Error event returns LogAction effect with prefix`() = runTest {
        val message = "Critical error"
        val event = ActionExecutor.ExecutionEvent.Error(message)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.LogAction)
        assertEquals("Fehler: Critical error", (effect as HandleActionExecutionEventUseCase.Effect.LogAction).message)
    }

    @Test
    fun `RecoverableAuthError event returns EmitAuthIntent effect`() = runTest {
        val intent = mockk<Intent>()
        val event = ActionExecutor.ExecutionEvent.RecoverableAuthError(intent)
        val effect = useCase.execute(event)

        assertTrue(effect is HandleActionExecutionEventUseCase.Effect.EmitAuthIntent)
        assertEquals(intent, (effect as HandleActionExecutionEventUseCase.Effect.EmitAuthIntent).intent)
    }
}
