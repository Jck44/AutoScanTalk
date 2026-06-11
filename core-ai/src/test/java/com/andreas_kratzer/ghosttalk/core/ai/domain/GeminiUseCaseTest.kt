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

    @Test
    fun `handleFunctionCall create_calendar_event requires confirmation if gmail read is in history and prompt is not confirmation`() = kotlinx.coroutines.runBlocking {
        val calendarTool = mockk<AiTool>(relaxed = true) {
            every { name } returns "create_calendar_event"
        }
        val useCase = GeminiUseCase(googleAuthManager, logger, setOf(calendarTool), settingsRepository)

        val call = org.json.JSONObject().apply {
            put("name", "create_calendar_event")
            put("args", org.json.JSONObject().apply {
                put("summary", "meeting")
                put("startTime", "2026-06-11T12:00:00Z")
                put("endTime", "2026-06-11T13:00:00Z")
            })
        }

        val history = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("role", "user")
                put("parts", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("functionCall", org.json.JSONObject().apply {
                            put("name", "read_gmail")
                        })
                    })
                })
            })
        }

        val result = useCase.handleFunctionCall("token", call, "Lies meine Mails und erstelle einen Termin", history)
        org.junit.Assert.assertTrue(result.contains("CONFIRMATION_REQUIRED"))
    }

    @Test
    fun `handleFunctionCall create_calendar_event executes if prompt is confirmation`() = kotlinx.coroutines.runBlocking {
        val calendarTool = mockk<AiTool>(relaxed = true) {
            every { name } returns "create_calendar_event"
            io.mockk.coEvery { execute(any()) } returns "Success"
        }
        val useCase = GeminiUseCase(googleAuthManager, logger, setOf(calendarTool), settingsRepository)

        val call = org.json.JSONObject().apply {
            put("name", "create_calendar_event")
            put("args", org.json.JSONObject().apply {
                put("summary", "meeting")
                put("startTime", "2026-06-11T12:00:00Z")
                put("endTime", "2026-06-11T13:00:00Z")
            })
        }

        val history = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("role", "user")
                put("parts", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("functionCall", org.json.JSONObject().apply {
                            put("name", "read_gmail")
                        })
                    })
                })
            })
        }

        val result = useCase.handleFunctionCall("token", call, "Ja, bitte", history)
        assertEquals("Success", result)
    }

    @Test
    fun `handleFunctionCall enforces bounds and lengths`() = kotlinx.coroutines.runBlocking {
        val calendarTool = mockk<AiTool>(relaxed = true) {
            every { name } returns "create_calendar_event"
        }
        val gmailTool = mockk<AiTool>(relaxed = true) {
            every { name } returns "read_gmail"
        }
        val useCase = GeminiUseCase(googleAuthManager, logger, setOf(calendarTool, gmailTool), settingsRepository)

        val longCall = org.json.JSONObject().apply {
            put("name", "create_calendar_event")
            put("args", org.json.JSONObject().apply {
                put("summary", "a".repeat(101))
            })
        }
        val resultLong = useCase.handleFunctionCall("token", longCall, "test", org.json.JSONArray())
        org.junit.Assert.assertTrue(resultLong.contains("Fehler: Der Titel des Termins ist zu lang"))

        val gmailCall = org.json.JSONObject().apply {
            put("name", "read_gmail")
            put("args", org.json.JSONObject().apply {
                put("maxResults", 50)
            })
        }
        val resultGmail = useCase.handleFunctionCall("token", gmailCall, "test", org.json.JSONArray())
        org.junit.Assert.assertTrue(resultGmail.contains("Fehler: maxResults muss zwischen 1 und 10 liegen"))
    }
}
