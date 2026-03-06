package com.andreas_kratzer.ghosttalk.domain.genai

import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TestGeminiNanoUseCaseTest {

    private lateinit var localIntentRouter: LocalIntentRouter
    private lateinit var useCase: TestGeminiNanoUseCase

    @Before
    fun setup() {
        localIntentRouter = mockk()
        useCase = TestGeminiNanoUseCase(localIntentRouter)
    }

    @Test
    fun `execute success calls onResponse`() = runTest {
        val expectedResponse = "Pong"
        coEvery { localIntentRouter.routeIntent("Ping", any()) } answers {
            val onSpeak = secondArg<(String) -> Unit>()
            onSpeak(expectedResponse)
        }

        var actualResponse: String? = null
        useCase.execute(
            onResponse = { actualResponse = it },
            onError = { }
        )

        assertEquals(expectedResponse, actualResponse)
    }

    @Test
    fun `execute failure calls onError`() = runTest {
        val exception = RuntimeException("Model error")
        coEvery { localIntentRouter.routeIntent("Ping", any()) } throws exception

        var actualError: Exception? = null
        useCase.execute(
            onResponse = { },
            onError = { actualError = it }
        )

        assertEquals(exception, actualError)
    }
}
