package com.andreas_kratzer.ghosttalk.domain.actions


import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ResolveSmartPredictionUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var useCase: ResolveSmartPredictionUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        actionExecutor = mockk(relaxed = true)
        useCase = ResolveSmartPredictionUseCase(pageRepository)
    }

    @Test
    fun `execute with matching button on current page executes button action`() = runTest {
        val button = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val currentPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(button))

        useCase.execute("btn1", currentPage, "b1", true, actionExecutor)

        coVerify { actionExecutor.executeButtonAction(button, bookId = "b1") }
    }

    @Test
    fun `execute with matching page id executes navigation action`() = runTest {
        val targetPage = Page(id = "p2", bookId = "b1", name = "Target", buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById("p2") } returns targetPage

        useCase.execute("p2", null, "b1", true, actionExecutor)

        coVerify { 
            actionExecutor.executeButtonAction(
                match { it.label == "Target" && it.buttonAction is NavigateToPageButtonAction },
                bookId = "b1"
            ) 
        }
    }

    @Test
    fun `execute does not provide bookId if user mode is inactive`() = runTest {
        val button = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val currentPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(button))

        useCase.execute("btn1", currentPage, "b1", false, actionExecutor)

        coVerify { actionExecutor.executeButtonAction(button, bookId = null) }
    }
}
