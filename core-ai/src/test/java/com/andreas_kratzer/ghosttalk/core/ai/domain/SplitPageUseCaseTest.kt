package com.andreas_kratzer.ghosttalk.core.ai.domain

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class SplitPageUseCaseTest {

    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var splitPageUseCase: SplitPageUseCase

    @Before
    fun setup() {
        geminiUseCase = mockk(relaxed = true)
        splitPageUseCase = SplitPageUseCase(geminiUseCase)
    }

    @Test
    fun `parseResponse parses clean JSON correctly`() {
        val json = """
            {
              "categories": [
                {
                  "name": "Essen",
                  "buttonLabels": ["Apfel", "Banane"]
                },
                {
                  "name": "Gefühle",
                  "buttonLabels": ["froh", "traurig"]
                }
              ]
            }
        """.trimIndent()

        val proposal = splitPageUseCase.parseResponse(json)

        assertEquals(2, proposal.categories.size)
        assertEquals("Essen", proposal.categories[0].name)
        assertEquals(listOf("Apfel", "Banane"), proposal.categories[0].buttonLabels)
        assertEquals("Gefühle", proposal.categories[1].name)
        assertEquals(listOf("froh", "traurig"), proposal.categories[1].buttonLabels)
    }

    @Test
    fun `parseResponse parses JSON with markdown code blocks`() {
        val json = """
            Hier ist die Antwort:
            ```json
            {
              "categories": [
                {
                  "name": "Aktionen",
                  "buttonLabels": ["gehen", "schlafen"]
                }
              ]
            }
            ```
            Hoffe das hilft!
        """.trimIndent()

        val proposal = splitPageUseCase.parseResponse(json)

        assertEquals(1, proposal.categories.size)
        assertEquals("Aktionen", proposal.categories[0].name)
        assertEquals(listOf("gehen", "schlafen"), proposal.categories[0].buttonLabels)
    }

    @Test
    fun `parseResponse throws when no braces found`() {
        val invalidText = "Hier ist keine JSON-Struktur."

        assertThrows(IllegalArgumentException::class.java) {
            splitPageUseCase.parseResponse(invalidText)
        }
    }
}
