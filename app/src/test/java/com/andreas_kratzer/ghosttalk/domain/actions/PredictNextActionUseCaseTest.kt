package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PredictNextActionUseCaseTest {

    private lateinit var actionLogUseCase: ActionLogUseCase
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var localIntentRouter: LocalIntentRouter
    private lateinit var useCase: PredictNextActionUseCase

    @Before
    fun setup() {
        actionLogUseCase = mockk()
        buttonUsageRepository = mockk()
        settingsRepository = mockk()
        localIntentRouter = mockk()
        useCase = PredictNextActionUseCase(
            actionLogUseCase,
            buttonUsageRepository,
            settingsRepository,
            localIntentRouter
        )
        
        // Default mock responses for settings frequently used
        every { settingsRepository.showPageIdInLog } returns false
        every { settingsRepository.geminiTimeout } returns 5000L
    }

    @Test
    fun `predict returns empty list when generative AI is disabled`() = runTest {
        every { settingsRepository.useLocalGenerativeAi } returns false

        val result = useCase.predict(mockk(), emptyList(), "book1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `predict returns parsed IDs from model response`() = runTest {
        every { settingsRepository.useLocalGenerativeAi } returns true
        every { actionLogUseCase.loadSavedLogs() } returns emptyList()
        coEvery { buttonUsageRepository.getTopActions(any(), any()) } returns emptyList()
        coEvery { localIntentRouter.generateRawResponse(any()) } returns "id1, id2, id3"

        val currentPage = Page(
            id = "curr",
            bookId = "book1",
            name = "Home",
            buttonConfigs = listOf(
                ButtonConfig(id = "id1", label = "A", auditoryCue = null, buttonAction = SpeakTextButtonAction()),
                ButtonConfig(id = "id2", label = "B", auditoryCue = null, buttonAction = SpeakTextButtonAction())
            )
        )

        val result = useCase.predict(currentPage, emptyList(), "book1")

        assertEquals(3, result.size)
        assertEquals("id1", result[0])
        assertEquals("id2", result[1])
        assertEquals("id3", result[2])
    }

    @Test
    fun `predict handles dirty model response with quotes`() = runTest {
        every { settingsRepository.useLocalGenerativeAi } returns true
        every { actionLogUseCase.loadSavedLogs() } returns emptyList()
        coEvery { buttonUsageRepository.getTopActions(any(), any()) } returns emptyList()
        coEvery { localIntentRouter.generateRawResponse(any()) } returns "'id1', \"id2\" , id3"

        val currentPage = Page(
            id = "curr",
            bookId = "book1",
            name = "Home",
            buttonConfigs = emptyList()
        )

        val result = useCase.predict(currentPage, emptyList(), "book1")

        assertEquals(listOf("id1", "id2", "id3"), result)
    }

    @Test
    fun `predict returns empty list on exception`() = runTest {
        every { settingsRepository.useLocalGenerativeAi } returns true
        coEvery { localIntentRouter.generateRawResponse(any()) } throws RuntimeException("Model error")
        every { actionLogUseCase.loadSavedLogs() } returns emptyList()
        coEvery { buttonUsageRepository.getTopActions(any(), any()) } returns emptyList()

        val currentPage = Page(
            id = "curr",
            bookId = "book1",
            name = "Home",
            buttonConfigs = emptyList()
        )

        val result = useCase.predict(currentPage, emptyList(), "book1")

        assertTrue(result.isEmpty())
    }
}
