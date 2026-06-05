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
class BookHierarchyProposalUseCaseTest {

    private lateinit var mockGeminiUseCase: GeminiUseCase
    private lateinit var useCase: BookHierarchyProposalUseCase

    @Before
    fun setup() {
        mockGeminiUseCase = mockk(relaxed = true)
        useCase = BookHierarchyProposalUseCase(mockGeminiUseCase)
    }

    @Test
    fun `generatePrompt contains correct instructions and inputs`() {
        val inputPages = "[{\"pageName\":\"Hauptseite\"}]"
        val feedback = "Essen unterteilen"
        val manualEdits = "[{\"name\":\"Hauptseite\"}]"
        
        val prompt = useCase.generatePrompt(inputPages, feedback, manualEdits)
        
        assertTrue(prompt.contains("Unterstützte Kommunikation (AAC)"))
        assertTrue(prompt.contains(inputPages))
        assertTrue(prompt.contains("Essen unterteilen"))
        assertTrue(prompt.contains("MANUELLE BASIS:"))
        assertTrue(prompt.contains(manualEdits))
    }

    @Test
    fun `parseResponse parses hierarchy JSON correctly`() {
        val json = """
            {
              "pages": [
                {
                  "name": "Hauptseite",
                  "description": "Startseite",
                  "subpages": ["Ernährung", "Ich möchte"],
                  "sourcePageName": "Originalseite",
                  "buttonIds": ["btn_1", "btn_2"]
                },
                {
                  "name": "Ernährung",
                  "description": "Essen & Trinken",
                  "subpages": [],
                  "buttonIds": []
                }
              ],
              "unmappedButtonIds": ["btn_3"]
            }
        """.trimIndent()

        val proposal = useCase.parseResponse(json)
        assertEquals(2, proposal.pages.size)

        val page1 = proposal.pages[0]
        assertEquals("Hauptseite", page1.name)
        assertEquals("Startseite", page1.description)
        assertEquals(listOf("Ernährung", "Ich möchte"), page1.subpages)
        assertEquals("Originalseite", page1.sourcePageName)
        assertEquals(listOf("btn_1", "btn_2"), page1.buttonIds)

        val page2 = proposal.pages[1]
        assertEquals("Ernährung", page2.name)
        assertEquals("Essen & Trinken", page2.description)
        assertTrue(page2.subpages.isEmpty())
        assertEquals(null, page2.sourcePageName)
        assertTrue(page2.buttonIds.isEmpty())

        assertEquals(listOf("btn_3"), proposal.unmappedButtonIds)
    }

    @Test
    fun `execute flows to gemini and parses response`() = runTest {
        val geminiResponse = """
            {
              "pages": [
                {
                  "name": "Hauptseite",
                  "description": "Startseite",
                  "subpages": [],
                  "buttonIds": ["btn_1"]
                }
              ],
              "unmappedButtonIds": []
            }
        """.trimIndent()

        coEvery { mockGeminiUseCase.generateResponse(any()) } returns geminiResponse

        val proposal = useCase.execute("[]")
        assertNotNull(proposal)
        assertEquals(1, proposal.pages.size)
        assertEquals("Hauptseite", proposal.pages[0].name)
        assertEquals(listOf("btn_1"), proposal.pages[0].buttonIds)
    }
}
