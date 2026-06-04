package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.RestructureAction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookRestructureProposalUseCaseTest {

    private lateinit var mockGeminiUseCase: GeminiUseCase
    private lateinit var useCase: BookRestructureProposalUseCase

    @Before
    fun setup() {
        mockGeminiUseCase = mockk(relaxed = true)
        useCase = BookRestructureProposalUseCase(mockGeminiUseCase)
    }

    @Test
    fun `generatePrompt contains correct instructions and pages input`() {
        val inputPages = "[{\"pageName\":\"Hauptseite\",\"buttons\":[{\"label\":\"Apple\"}]}]"
        val prompt = useCase.generatePrompt(inputPages)
        
        assertTrue(prompt.contains("AAC-Kommunikationsbuchs"))
        assertTrue(prompt.contains(inputPages))
        assertTrue(prompt.contains("MOVE_BUTTON"))
        assertTrue(prompt.contains("SPLIT_PAGE"))
        assertTrue(prompt.contains("DEACTIVATE_BUTTON"))
    }

    @Test
    fun `parseResponse parses clean JSON correctly`() {
        val json = """
            {
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "rationale": "Apple wird sehr oft geklickt",
                  "buttonLabel": "Apple",
                  "sourcePageName": "Kategorie 1",
                  "targetPageName": "Hauptseite"
                },
                {
                  "type": "DEACTIVATE_BUTTON",
                  "rationale": "Rarely used button",
                  "buttonLabel": "Rarely Used",
                  "sourcePageName": "Hauptseite"
                },
                {
                  "type": "SPLIT_PAGE",
                  "rationale": "Seite ist zu voll",
                  "sourcePageName": "Hauptseite",
                  "newCategories": [
                    {
                      "name": "Food",
                      "buttonLabels": ["Apple", "Banana"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val proposal = useCase.parseResponse(json)
        assertEquals(3, proposal.actions.size)

        val action1 = proposal.actions[0]
        assertEquals("MOVE_BUTTON", action1.type)
        assertEquals("Apple wird sehr oft geklickt", action1.rationale)
        assertEquals("Apple", action1.buttonLabel)
        assertEquals("Kategorie 1", action1.sourcePageName)
        assertEquals("Hauptseite", action1.targetPageName)

        val action2 = proposal.actions[1]
        assertEquals("DEACTIVATE_BUTTON", action2.type)
        assertEquals("Rarely used button", action2.rationale)
        assertEquals("Rarely Used", action2.buttonLabel)
        assertEquals("Hauptseite", action2.sourcePageName)

        val action3 = proposal.actions[2]
        assertEquals("SPLIT_PAGE", action3.type)
        assertEquals("Food", action3.newCategories?.get(0)?.name)
        assertEquals(listOf("Apple", "Banana"), action3.newCategories?.get(0)?.buttonLabels)
    }

    @Test
    fun `parseResponse extracts JSON wrapped in markdown or conversational text`() {
        val json = """
            Gerne, hier ist mein Vorschlag für die Restrukturierung:
            ```json
            {
              "actions": [
                {
                  "type": "DEACTIVATE_BUTTON",
                  "rationale": "Test deactivation",
                  "buttonLabel": "Test",
                  "sourcePageName": "Home"
                }
              ]
            }
            ```
            Ich hoffe, das hilft dem Caregiver weiter.
        """.trimIndent()

        val proposal = useCase.parseResponse(json)
        assertEquals(1, proposal.actions.size)
        assertEquals("DEACTIVATE_BUTTON", proposal.actions[0].type)
        assertEquals("Test", proposal.actions[0].buttonLabel)
    }

    @Test
    fun `parseResponse throws exception for invalid or missing JSON braces`() {
        val invalidText = "No JSON here"
        assertThrows(IllegalArgumentException::class.java) {
            useCase.parseResponse(invalidText)
        }
    }

    @Test
    fun `execute flows prompt to gemini and parses back response`() = runTest {
        val inputPages = "[]"
        val geminiResponse = """
            {
              "actions": [
                {
                  "type": "DEACTIVATE_BUTTON",
                  "rationale": "Rationale description",
                  "buttonLabel": "Button",
                  "sourcePageName": "Home"
                }
              ]
            }
        """.trimIndent()

        coEvery { mockGeminiUseCase.generateResponse(any()) } returns geminiResponse

        val proposal = useCase.execute(inputPages)
        assertNotNull(proposal)
        assertEquals(1, proposal.actions.size)
        assertEquals("DEACTIVATE_BUTTON", proposal.actions[0].type)
        assertEquals("Button", proposal.actions[0].buttonLabel)
    }
}
