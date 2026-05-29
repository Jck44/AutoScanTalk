package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeminiUseCaseTest {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var logger: Logger
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var wikiTool: AiTool
    private lateinit var driveTool: AiTool

    @Before
    fun setup() {
        googleAuthManager = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        wikiTool = mockk(relaxed = true) {
            every { name } returns "wikipedia_search"
            every { requiresAuth } returns false
        }
        driveTool = mockk(relaxed = true) {
            every { name } returns "search_drive"
            every { requiresAuth } returns true
        }

        geminiUseCase = GeminiUseCase(googleAuthManager, logger, setOf(wikiTool, driveTool), settingsRepository)
        GeminiUseCase.resetHealthStateForTesting()
    }

    @Test
    fun `parseWaitTime parses Retry-After header`() {
        val result = geminiUseCase.parseWaitTime("30", null)
        assertEquals(30L, result)
    }

    @Test
    fun `parseWaitTime parses error body with seconds`() {
        val errorBody = "Quota exceeded. Please retry in 45.5s"
        val result = geminiUseCase.parseWaitTime(null, errorBody)
        assertEquals(45L, result)
    }

    @Test
    fun `parseWaitTime does not misinterpret 429 as seconds`() {
        val errorBody = "HTTP 429: Quota exceeded"
        val result = geminiUseCase.parseWaitTime(null, errorBody)
        assertEquals(60L, result)
    }

    @Test
    fun `parseWaitTime returns default when parsing fails`() {
        val result = geminiUseCase.parseWaitTime(null, "Something went wrong")
        assertEquals(60L, result)
    }

    @Test
    fun `getToolStatus correctly reflects AVAILABLE state`() {
        // Mock a success state
        GeminiUseCase.lastSuccess = true
        
        val status = geminiUseCase.getToolStatus(isUserSignedIn = true)
        
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["search_drive"])
    }

    @Test
    fun `getToolStatus correctly reflects REQUIRES_AUTH state`() {
        GeminiUseCase.lastSuccess = true
        
        val status = geminiUseCase.getToolStatus(isUserSignedIn = false)
        
        assertEquals(GeminiUseCase.ToolStatus.AVAILABLE, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.REQUIRES_AUTH, status["search_drive"])
    }

    @Test
    fun `getToolStatus correctly reflects FAILED state`() {
        GeminiUseCase.lastSuccess = false
        
        val status = geminiUseCase.getToolStatus(isUserSignedIn = true)
        
        assertEquals(GeminiUseCase.ToolStatus.FAILED, status["wikipedia_search"])
        assertEquals(GeminiUseCase.ToolStatus.FAILED, status["search_drive"])
    }
}
