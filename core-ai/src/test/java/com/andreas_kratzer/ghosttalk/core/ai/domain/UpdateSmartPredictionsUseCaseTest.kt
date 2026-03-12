package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateSmartPredictionsUseCaseTest {

    private val settingsRepository = mockk<GenAiSettings>(relaxed = true)
    private val predictNextActionUseCase = mockk<PredictNextActionUseCase>()
    private val checkForPredictorUseCase = mockk<CheckForPredictorUseCase>()
    private val useCase = UpdateSmartPredictionsUseCase(
        settingsRepository,
        predictNextActionUseCase,
        checkForPredictorUseCase
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `isLoading state cycles correctly during prediction`() = runTest {
        val page = mockk<Page> { every { id } returns "page1" }
        val pages = listOf(page)
        val bookId = "book1"
        
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow(true)
        every { checkForPredictorUseCase(page) } returns true
        coEvery { predictNextActionUseCase.predict(any<Page>(), any<List<Page>>(), any<String>()) } coAnswers {
            // Check that it's loading while we are predicting
            assertTrue(useCase.isLoading.value)
            listOf("id1")
        }

        val loadingStates = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            useCase.isLoading.collect { loadingStates.add(it) }
        }

        useCase.execute(flowOf(page), flowOf(pages), flowOf(bookId), flowOf<List<String>>(emptyList()), flowOf(true)).take(1).toList()

        // Should be false -> true -> false
        assertTrue(loadingStates.contains(true))
        assertFalse(useCase.isLoading.value)
        
        job.cancel()
    }
}
