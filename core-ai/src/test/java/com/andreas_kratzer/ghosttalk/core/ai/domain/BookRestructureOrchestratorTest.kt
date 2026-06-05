package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import com.andreas_kratzer.ghosttalk.core.model.PageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookRestructureOrchestratorTest {

    private lateinit var mockHierarchyUseCase: BookHierarchyProposalUseCase
    private lateinit var mockLayoutUseCase: PageLayoutProposalUseCase
    private lateinit var orchestrator: BookRestructureOrchestrator

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0

        mockHierarchyUseCase = mockk()
        mockLayoutUseCase = mockk()
        orchestrator = BookRestructureOrchestrator(mockHierarchyUseCase, mockLayoutUseCase)
    }

    @Test
    fun `executeRestructure with complete mapping returns unmodified results without reconciliation`() = runTest {
        val pagesJson = """
            {
              "pages": [
                {
                  "name": "Hauptseite",
                  "buttons": [
                    { "id": "btn1", "label": "Hallo" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val mockHierarchy = BookHierarchyProposal(
            pages = listOf(
                HierarchyPageNode(
                    name = "Hauptseite",
                    description = "Startseite",
                    buttonIds = listOf("btn1")
                )
            )
        )

        val mockLayout = PageLayoutProposal(
            pageName = "Hauptseite",
            actions = listOf(
                PageButtonAction(
                    type = "MOVE_BUTTON",
                    buttonLabel = "Hallo",
                    buttonId = "btn1",
                    rationale = "Move btn1"
                )
            )
        )

        coEvery { mockHierarchyUseCase.execute(pagesJson, any(), any()) } returns mockHierarchy
        coEvery { mockLayoutUseCase.execute("Hauptseite", "Startseite", emptyList(), pagesJson) } returns mockLayout

        val result = orchestrator.executeRestructure(pagesJson)

        assertFalse(result.wasReconciliationTriggered)
        assertEquals(1, result.hierarchy.pages.size)
        assertEquals("Hauptseite", result.hierarchy.pages[0].name)
        assertEquals(1, result.pageLayouts.size)
        assertEquals("Hauptseite", result.pageLayouts[0].pageName)
    }

    @Test
    fun `executeRestructure with missing buttons triggers reconciliation and creates backup page`() = runTest {
        val pagesJson = """
            {
              "pages": [
                {
                  "name": "Hauptseite",
                  "buttons": [
                    { "id": "btn1", "label": "Hallo" },
                    { "id": "btn2", "label": "Tschüss" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val mockHierarchy = BookHierarchyProposal(
            pages = listOf(
                HierarchyPageNode(
                    name = "Hauptseite",
                    description = "Startseite",
                    buttonIds = listOf("btn1")
                )
            )
        )

        // Mock layout ONLY maps btn1
        val mockLayout = PageLayoutProposal(
            pageName = "Hauptseite",
            actions = listOf(
                PageButtonAction(
                    type = "MOVE_BUTTON",
                    buttonLabel = "Hallo",
                    buttonId = "btn1",
                    rationale = "Move btn1"
                )
            )
        )

        coEvery { mockHierarchyUseCase.execute(pagesJson, any(), any()) } returns mockHierarchy
        coEvery { mockLayoutUseCase.execute("Hauptseite", "Startseite", emptyList(), pagesJson) } returns mockLayout

        val result = orchestrator.executeRestructure(pagesJson)

        assertTrue(result.wasReconciliationTriggered)
        // Check that backup page was added to hierarchy pages list
        assertEquals(2, result.hierarchy.pages.size)
        val backupPage = result.hierarchy.pages.firstOrNull { it.name == "Umsortierte Reste (Automatisch)" }
        assertTrue(backupPage != null)
        assertEquals(listOf("btn2"), backupPage?.buttonIds)

        // Check that backup layout proposal was appended
        assertEquals(2, result.pageLayouts.size)
        val backupLayout = result.pageLayouts.firstOrNull { it.pageName == "Umsortierte Reste (Automatisch)" }
        assertTrue(backupLayout != null)
        assertEquals(1, backupLayout?.actions?.size)
        assertEquals("btn2", backupLayout?.actions?.get(0)?.buttonId)
    }
}
