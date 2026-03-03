package com.andreas_kratzer.ghosttalk.domain

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class GeminiUseCaseTest {

    private val oauthTokenProvider: suspend () -> String? = mockk()
    private val driveProvider: suspend () -> com.google.api.services.drive.Drive? = mockk()
    private val useCase = GeminiUseCase(oauthTokenProvider, driveProvider)

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
}
