package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class GeminiUseCaseTest {

    private val oauthTokenProvider: suspend () -> String? = mockk()
    private val driveProvider: suspend () -> com.google.api.services.drive.Drive? = mockk()
    private val logger: com.andreas_kratzer.ghosttalk.core.util.Logger = mockk(relaxed = true)
    private val useCase = GeminiUseCase(oauthTokenProvider, driveProvider, logger)

    @Before
    fun setup() {
        GeminiUseCase.resetHealthStateForTesting()
    }

    @Test
    fun `getToolStatus returns PENDING when no call has been made`() {
        val status = useCase.getToolStatus(isUserSignedIn = true)
        assertEquals(GeminiUseCase.ToolStatus.PENDING, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.PENDING, status["search_drive"])
    }

    @Test
    fun `getToolStatus returns AVAILABLE after success`() {
        GeminiUseCase.lastSuccess = true
        
        val status = useCase.getToolStatus(isUserSignedIn = true)
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["search_drive"])
    }

    @Test
    fun `getToolStatus returns FAILED after error`() {
        GeminiUseCase.lastSuccess = false
        
        val status = useCase.getToolStatus(isUserSignedIn = true)
        assertEquals(GeminiUseCase.ToolStatus.FAILED, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.FAILED, status["search_drive"])
    }

    @Test
    fun `getToolStatus returns REQUIRES_AUTH when not signed in`() {
        GeminiUseCase.lastSuccess = true
        
        val status = useCase.getToolStatus(isUserSignedIn = false)
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["wikipedia_search"]) // Static tool
        assertEquals(GeminiUseCase.ToolStatus.REQUIRES_AUTH, status["search_drive"]) // Auth dependent
    }

    @Test
    fun `google_search is never in the status map`() {
        val status = useCase.getToolStatus(isUserSignedIn = true)
        assertFalse(status.containsKey("google_search"))
    }

    @Test
    fun `parseWaitTime uses Retry-After header if present`() {
        val wait = useCase.parseWaitTime("42", "some error")
        assertEquals(42L, wait)
    }

    @Test
    fun `parseWaitTime parses from error body if header is missing`() {
        val wait = useCase.parseWaitTime(null, "Quota exceeded. Please retry in 15.5s")
        assertEquals(15L, wait)
    }

    @Test
    fun `parseWaitTime falls back to 60s if everything fails`() {
        val wait = useCase.parseWaitTime(null, "generic error")
        assertEquals(60L, wait)
    }

    @Test(expected = Exception::class)
    fun `generateResponse throws exception when lockout is active`() = kotlinx.coroutines.test.runTest {
        GeminiUseCase.lockoutUntilTime = System.currentTimeMillis() + 10000
        useCase.generateResponse("hello")
    }
}
