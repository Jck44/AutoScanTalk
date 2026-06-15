package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertButtonConfigUseCaseTest {

    private val useCase = InsertButtonConfigUseCase()

    @Test
    fun `execute returns InvalidIndex when index is out of bounds`() {
        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = List(49) { null }
        )
        val newConfig = ButtonConfig(id = "new", label = "New")

        val result = useCase.execute(page, 100, newConfig)
        assertTrue(result is InsertButtonConfigUseCase.Result.InvalidIndex)
    }

    @Test
    fun `execute replaces empty slot when target is empty and forceShift is false`() {
        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = List(49) { null }
        )
        val newConfig = ButtonConfig(id = "new", label = "New")

        val result = useCase.execute(page, 2, newConfig)
        assertTrue(result is InsertButtonConfigUseCase.Result.Success)
        val success = result as InsertButtonConfigUseCase.Result.Success
        assertEquals(newConfig, success.newConfigs[2])
    }

    @Test
    fun `execute shifts buttons when target is occupied`() {
        // 2 rows, 3 columns = 6 visible slots. Global indices of visible: 0, 1, 2, 7, 8, 9
        // Set configs at visible slots
        val configs = MutableList<ButtonConfig?>(49) { null }
        configs[0] = ButtonConfig(id = "b0", label = "0")
        configs[1] = ButtonConfig(id = "b1", label = "1")
        configs[2] = ButtonConfig(id = "b2", label = "2")
        configs[7] = ButtonConfig(id = "b7", label = "7")
        configs[8] = ButtonConfig(id = "b8", label = "8")
        configs[9] = ButtonConfig(id = "b9", label = "9")

        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = configs
        )
        val newConfig = ButtonConfig(id = "new", label = "New")

        // Insert at index 1.
        // Drop visible pos is index 1.
        // Last visible global is 9. lastItem is "b9".
        // Visible items from index 1 down:
        // index 9 becomes index 8 ("b8")
        // index 8 becomes index 7 ("b7")
        // index 7 becomes index 2 ("b2")
        // index 2 becomes index 1 ("b1")
        // "new" is placed at index 1.
        // "b9" is rescued to first available invisible slot (index 3, since 0,1,2,7,8,9 are visible and 3 is free).
        val result = useCase.execute(page, 1, newConfig, forceShift = true)
        assertTrue(result is InsertButtonConfigUseCase.Result.Success)
        val success = result as InsertButtonConfigUseCase.Result.Success

        assertEquals("0", success.newConfigs[0]?.label)
        assertEquals("New", success.newConfigs[1]?.label)
        assertEquals("1", success.newConfigs[2]?.label)
        assertEquals("b9", success.newConfigs[3]?.id) // rescued here
        assertEquals("2", success.newConfigs[7]?.label)
        assertEquals("7", success.newConfigs[8]?.label)
        assertEquals("8", success.newConfigs[9]?.label)
    }

    @Test
    fun `execute returns GridFull when all 49 slots are full`() {
        val configs = List(49) { ButtonConfig(id = "b$it", label = "$it") }
        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 7, columns = 7,
            buttonConfigs = configs
        )
        val newConfig = ButtonConfig(id = "new", label = "New")

        val result = useCase.execute(page, 0, newConfig, forceShift = true)
        assertTrue(result is InsertButtonConfigUseCase.Result.GridFull)
    }
}
