package com.andreas_kratzer.ghosttalk.core.ai.domain

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageLayoutProposalUseCaseTest {

    private lateinit var mockGeminiUseCase: GeminiUseCase
    private lateinit var useCase: PageLayoutProposalUseCase

    @Before
    fun setup() {
        mockGeminiUseCase = mockk(relaxed = true)
        useCase = PageLayoutProposalUseCase(mockGeminiUseCase)
    }

    @Test
    fun `generatePrompt contains correct instructions and inputs`() {
        val targetName = "Ernährung"
        val desc = "Essen und Trinken"
        val subpages = listOf("Getränke")
        val buttonsJson = "[{\"label\":\"Apfel\"}]"

        val prompt = useCase.generatePrompt(targetName, desc, subpages, buttonsJson)

        assertTrue(prompt.contains("Experte für Unterstützte Kommunikation (AAC)"))
        assertTrue(prompt.contains("Ernährung"))
        assertTrue(prompt.contains("Essen und Trinken"))
        assertTrue(prompt.contains("Getränke"))
        assertTrue(prompt.contains("Apfel"))
    }

    @Test
    fun `parseResponse parses layout JSON correctly`() {
        val json = """
            {
              "pageName": "Ernährung",
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "buttonId": "btn_apfel",
                  "buttonLabel": "Apfel",
                  "sourcePageName": "Hauptseite",
                  "rationale": "Apfel ist Essen"
                },
                {
                  "type": "CREATE_NAV_BUTTON",
                  "buttonLabel": "Getränke",
                  "targetPageName": "Getränke",
                  "rationale": "Verknüpfung"
                }
              ]
            }
        """.trimIndent()

        val proposal = useCase.parseResponse(json)
        assertEquals("Ernährung", proposal.pageName)
        assertEquals(2, proposal.actions.size)

        val action1 = proposal.actions[0]
        assertEquals("MOVE_BUTTON", action1.type)
        assertEquals("btn_apfel", action1.buttonId)
        assertEquals("Apfel", action1.buttonLabel)
        assertEquals("Hauptseite", action1.sourcePageName)
        assertEquals("Apfel ist Essen", action1.rationale)

        val action2 = proposal.actions[1]
        assertEquals("CREATE_NAV_BUTTON", action2.type)
        assertEquals(null, action2.buttonId)
        assertEquals("Getränke", action2.buttonLabel)
        assertEquals("Getränke", action2.targetPageName)
        assertEquals("Verknüpfung", action2.rationale)
    }

    @Test
    fun `execute flows to gemini and parses response`() = runTest {
        val geminiResponse = """
            {
              "pageName": "Hauptseite",
              "actions": []
            }
        """.trimIndent()

        coEvery { mockGeminiUseCase.generateResponse(any()) } returns geminiResponse

        val proposal = useCase.execute("Hauptseite", "Start", emptyList(), "[]")
        assertNotNull(proposal)
        assertEquals("Hauptseite", proposal.pageName)
        assertTrue(proposal.actions.isEmpty())
    }
}
