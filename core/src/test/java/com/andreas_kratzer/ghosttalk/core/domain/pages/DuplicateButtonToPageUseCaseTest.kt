package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuplicateButtonToPageUseCaseTest {

    private lateinit var useCase: DuplicateButtonToPageUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = DuplicateButtonToPageUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute duplicates button to visible free slot`() = runTest {
        val button = ButtonConfig(id = "b1", label = "DuplicateMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 1, columns = 1,
            buttonConfigs = List(49) { null }
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        val result = useCase.execute("p1", 0, "p2", false)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.Success)
        val success = result as MoveButtonToPageUseCase.MoveResult.Success
        
        // Source button remains
        assertEquals("DuplicateMe", success.fromPage.buttonConfigs[0]?.label)
        // Target button added
        assertEquals("DuplicateMe", success.toPage.buttonConfigs[0]?.label)
        // Target button should have a NEW ID
        assertTrue(success.toPage.buttonConfigs[0]?.id != "b1")
        
        coVerify {
            pageRepository.updatePage(
                match { it.id == "p2" && it.buttonConfigs[0]?.label == "DuplicateMe" }
            )
        }
        // Verify source page was NOT updated
        coVerify(exactly = 0) { pageRepository.updatePage(match { it.id == "p1" }) }
    }

    @Test
    fun `execute returns NeedsConfirmation if slot is hidden`() = runTest {
        val button = ButtonConfig(id = "b1", label = "DuplicateMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 1, columns = 1,
            buttonConfigs = listOf(ButtonConfig(label = "Existing", auditoryCue = null, buttonAction = SpeakTextButtonAction())) + List(48) { null }
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        val result = useCase.execute("p1", 0, "p2", false)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.NeedsConfirmation)
        val confirmation = result as MoveButtonToPageUseCase.MoveResult.NeedsConfirmation
        assertEquals(1, confirmation.freeSlotIndex)
        assertEquals(1, confirmation.requiredRows)
        assertEquals(2, confirmation.requiredCols)
    }

    @Test
    fun `execute prioritizes visible free slots over invisible free slots`() = runTest {
        val button = ButtonConfig(id = "b1", label = "DuplicateMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        // target page is 2x2.
        // Index 0 and 1 are occupied.
        // Index 2 is null (invisible because col=2 >= columns=2).
        // Index 7 is null (visible: row=1 < 2, col=0 < 2).
        val targetConfigs = MutableList<ButtonConfig?>(49) { null }
        targetConfigs[0] = ButtonConfig(label = "O0", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        targetConfigs[1] = ButtonConfig(label = "O1", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 2, columns = 2,
            buttonConfigs = targetConfigs
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        val result = useCase.execute("p1", 0, "p2", false)

        // It should succeed and put the duplicated button at index 7 (visible) rather than index 2 (invisible, which would need confirmation)
        assertTrue(result is MoveButtonToPageUseCase.MoveResult.Success)
        val success = result as MoveButtonToPageUseCase.MoveResult.Success
        assertEquals("DuplicateMe", success.toPage.buttonConfigs[7]?.label)
        assertNull(success.toPage.buttonConfigs[2])
    }

    @Test
    fun `execute returns TargetFull if no nulls available`() = runTest {
        val button = ButtonConfig(id = "b1", label = "DuplicateMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 7, columns = 7,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 7, columns = 7,
            buttonConfigs = List(49) { ButtonConfig(label = "Full", auditoryCue = null, buttonAction = SpeakTextButtonAction()) }
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        val result = useCase.execute("p1", 0, "p2", false)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.TargetFull)
    }

    @Test
    fun `execute duplicates button and expands grid when forceMove is true`() = runTest {
        val button = ButtonConfig(id = "b1", label = "DuplicateMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 1, columns = 1,
            buttonConfigs = listOf(ButtonConfig(label = "Existing", auditoryCue = null, buttonAction = SpeakTextButtonAction())) + List(48) { null }
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        // Duplicate to index 1 (requires expanding columns to 2)
        val result = useCase.execute("p1", 0, "p2", true)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.Success)
        val success = result as MoveButtonToPageUseCase.MoveResult.Success
        
        assertEquals(1, success.toPage.rows)
        assertEquals(2, success.toPage.columns)
        assertEquals("DuplicateMe", success.toPage.buttonConfigs[1]?.label)
        assertEquals("Existing", success.toPage.buttonConfigs[0]?.label)
        // Source remains untouched
        assertEquals("DuplicateMe", success.fromPage.buttonConfigs[0]?.label)
    }

    private fun assertNull(actual: Any?) {
        org.junit.Assert.assertNull(actual)
    }
}
