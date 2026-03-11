package com.andreas_kratzer.ghosttalk.domain.genai

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ActivateGeminiUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var useCase: ActivateGeminiUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        geminiUseCase = mockk()
        
        useCase = ActivateGeminiUseCase(geminiUseCase, settingsRepository)
    }

    @Test
    fun `execute success updates settings and calls onSuccess`() = runTest {
        coEvery { geminiUseCase.generateResponse("Ping") } returns "Pong"
        var successCalled = false

        useCase.execute(
            onSuccess = { successCalled = true },
            onError = { }
        )

        assert(successCalled)
        verify { settingsRepository.isGeminiEnabled = true }
    }

    @Test
    fun `execute failure calls onError`() = runTest {
        val exception = RuntimeException("Network error")
        coEvery { geminiUseCase.generateResponse("Ping") } throws exception
        var errorException: Exception? = null

        useCase.execute(
            onSuccess = { },
            onError = { errorException = it }
        )

        assert(errorException == exception)
        verify(exactly = 0) { settingsRepository.isGeminiEnabled = true }
    }
}
