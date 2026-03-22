package com.andreas_kratzer.ghosttalk.domain.actions


import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveSmartPredictionUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: ResolveSmartPredictionUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        actionExecutor = mockk(relaxed = true)
        useCase = ResolveSmartPredictionUseCase(pageRepository, bookRepository)
        
        val testBook = com.andreas_kratzer.ghosttalk.core.model.Book(id = "b1", name = "Test Book", logIgnoredActions = true)
        coEvery { bookRepository.getBookById(any()) } returns testBook
    }

    @Test
    fun `execute with matching button on current page executes button action`() = runTest {
        val button = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val currentPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(button))

        useCase.execute("btn1", currentPage, "b1", true, actionExecutor)

        coVerify { 
            actionExecutor.executeButtonAction(
                buttonConfig = button, 
                bookId = "b1", 
                pageId = "p1",
                rows = 4, 
                columns = 4,
                skipLog = false
            ) 
        }
    }

    @Test
    fun `execute with matching page id executes navigation action`() = runTest {
        val targetPage = Page(id = "p2", bookId = "b1", name = "Target", buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById("p2") } returns targetPage

        useCase.execute("p2", null, "b1", true, actionExecutor)

        coVerify { 
            actionExecutor.executeButtonAction(
                buttonConfig = match { it.id == "p2" && it.label == "Target" && it.buttonAction is NavigateToPageButtonAction },
                bookId = "b1",
                pageId = "p2",
                rows = 1,
                columns = 1,
                skipLog = false
            ) 
        }
    }

    @Test
    fun `execute does not provide bookId if user mode is inactive`() = runTest {
        val button = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val currentPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(button))

        useCase.execute("btn1", currentPage, "b1", false, actionExecutor)

        coVerify { 
            actionExecutor.executeButtonAction(
                buttonConfig = button, 
                bookId = null, 
                pageId = "p1",
                rows = 4, 
                columns = 4,
                skipLog = false
            ) 
        }
    }
}
