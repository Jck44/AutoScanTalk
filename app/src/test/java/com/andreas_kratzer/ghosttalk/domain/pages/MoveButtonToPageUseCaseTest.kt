package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoveButtonToPageUseCaseTest {

    private lateinit var useCase: MoveButtonToPageUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = MoveButtonToPageUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute moves button to visible free slot`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
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
        assertNull(success.fromPage.buttonConfigs[0])
        assertEquals("MoveMe", success.toPage.buttonConfigs[0]?.label)
        
        coVerify {
            pageRepository.movePages(
                match { it.id == "p1" && it.buttonConfigs[0] == null },
                match { it.id == "p2" && it.buttonConfigs[0]?.label == "MoveMe" }
            )
        }
    }

    @Test
    fun `execute returns NeedsConfirmation if slot is hidden`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        // Target is 1x1, but has a null at index 1 (row 0, col 1 - hidden if columns=1)
        // Wait, index 1 is Row 0, Col 1.
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
    fun `execute returns TargetFull if no nulls available`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
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
    fun `execute moves button and expands grid when forceMove is true`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
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

        // Move to index 1 (requires expanding columns to 2)
        val result = useCase.execute("p1", 0, "p2", true)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.Success)
        val success = result as MoveButtonToPageUseCase.MoveResult.Success
        
        // Final state must have both: the new grid size AND the moved button
        assertEquals(1, success.toPage.rows)
        assertEquals(2, success.toPage.columns)
        assertEquals("MoveMe", success.toPage.buttonConfigs[1]?.label)
        assertEquals("Existing", success.toPage.buttonConfigs[0]?.label)
    }

    @Test
    fun `execute returns NeedsConfirmation if only the 49th slot is free`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val fromPage = Page(
            id = "p1", bookId = "book1", name = "From",
            rows = 1, columns = 1,
            buttonConfigs = listOf(button) + List(48) { null }
        )
        // Target is 1x1, everything except index 48 is full
        val toPage = Page(
            id = "p2", bookId = "book1", name = "To",
            rows = 1, columns = 1,
            buttonConfigs = List(48) { ButtonConfig(label = "Full", auditoryCue = null, buttonAction = SpeakTextButtonAction()) } + listOf(null)
        )

        coEvery { pageRepository.getPageById("p1") } returns fromPage
        coEvery { pageRepository.getPageById("p2") } returns toPage

        val result = useCase.execute("p1", 0, "p2", false)

        assertTrue(result is MoveButtonToPageUseCase.MoveResult.NeedsConfirmation)
        val confirmation = result as MoveButtonToPageUseCase.MoveResult.NeedsConfirmation
        assertEquals(48, confirmation.freeSlotIndex)
        assertEquals(7, confirmation.requiredRows)
        assertEquals(7, confirmation.requiredCols)
    }

    @Test
    fun `execute does not delete button from source if target is full`() = runTest {
        val button = ButtonConfig(id = "b1", label = "MoveMe", auditoryCue = null, buttonAction = SpeakTextButtonAction())
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
        
        // Verify no updates happened
        coVerify(exactly = 0) { pageRepository.updatePage(any()) }
    }

    private fun assertNull(actual: Any?) {
        org.junit.Assert.assertNull(actual)
    }
}
