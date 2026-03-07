package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateSmartPredictionsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val predictNextActionUseCase = mockk<PredictNextActionUseCase>()
    private val checkForPredictorUseCase = mockk<CheckForPredictorUseCase>()
    private val useCase = UpdateSmartPredictionsUseCase(
        settingsRepository,
        predictNextActionUseCase,
        checkForPredictorUseCase
    )

    @Test
    fun `isLoading state cycles correctly during prediction`() = runTest {
        val page = mockk<Page> { every { id } returns "page1" }
        val pages = listOf(page)
        val bookId = "book1"
        
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow(true)
        every { checkForPredictorUseCase(page) } returns true
        coEvery { predictNextActionUseCase.predict(any(), any(), any()) } coAnswers {
            // Check that it's loading while we are predicting
            assertTrue(useCase.isLoading.value)
            listOf("id1")
        }

        val loadingStates = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            useCase.isLoading.collect { loadingStates.add(it) }
        }

        useCase.execute(flowOf(page), flowOf(pages), flowOf(bookId), flowOf(emptyList()), flowOf(true)).take(1).toList()

        // Should be false -> true -> false
        assertTrue(loadingStates.contains(true))
        assertFalse(useCase.isLoading.value)
        
        job.cancel()
    }
}
