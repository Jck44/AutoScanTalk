package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveButtonWithInsertUseCaseTest {

    private val useCase = MoveButtonWithInsertUseCase()

    @Test
    fun `execute returns Error when indices are invalid or identical`() {
        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = List(49) { null }
        )

        // same index
        var result = useCase.execute(page, 2, 2)
        assertTrue(result is MoveButtonWithInsertUseCase.Result.Error)

        // target is fromIndex + 1
        result = useCase.execute(page, 2, 3)
        assertTrue(result is MoveButtonWithInsertUseCase.Result.Error)
    }

    @Test
    fun `execute shifts left when fromIndex is less than toIndex`() {
        // 2 rows, 3 columns = visible global indices: 0, 1, 2, 7, 8, 9
        val configs = MutableList<ButtonConfig?>(49) { null }
        configs[0] = ButtonConfig(id = "b0", label = "0")
        configs[1] = ButtonConfig(id = "b1", label = "1")
        configs[2] = ButtonConfig(id = "b2", label = "2")

        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = configs
        )

        // Move b0 (index 0) to drop position 2 (after b1).
        // Since fromIndex (0) < toIndex (2):
        // Shift visible indices between 0 and 1 left:
        // index 0 gets config from index 1 ("b1").
        // index 1 (toIndex - 1) gets "b0".
        // index 2 remains "b2".
        val result = useCase.execute(page, 0, 2)
        assertTrue(result is MoveButtonWithInsertUseCase.Result.Success)
        val success = result as MoveButtonWithInsertUseCase.Result.Success

        assertEquals("0", success.movedButtonLabel)
        assertEquals("1", success.newConfigs[0]?.label)
        assertEquals("0", success.newConfigs[1]?.label)
        assertEquals("2", success.newConfigs[2]?.label)
    }

    @Test
    fun `execute shifts right when fromIndex is greater than toIndex`() {
        // 2 rows, 3 columns = visible global indices: 0, 1, 2, 7, 8, 9
        val configs = MutableList<ButtonConfig?>(49) { null }
        configs[0] = ButtonConfig(id = "b0", label = "0")
        configs[1] = ButtonConfig(id = "b1", label = "1")
        configs[2] = ButtonConfig(id = "b2", label = "2")

        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 3,
            buttonConfigs = configs
        )

        // Move b2 (index 2) to drop position 0 (before b0).
        // Since fromIndex (2) > toIndex (0):
        // Shift visible indices between 0 and 2 right:
        // index 2 (global 2) gets config from index 1 ("b1").
        // index 1 (global 1) gets config from index 0 ("b0").
        // index 0 (toIndex) gets "b2".
        val result = useCase.execute(page, 2, 0)
        assertTrue(result is MoveButtonWithInsertUseCase.Result.Success)
        val success = result as MoveButtonWithInsertUseCase.Result.Success

        assertEquals("2", success.movedButtonLabel)
        assertEquals("2", success.newConfigs[0]?.label)
        assertEquals("0", success.newConfigs[1]?.label)
        assertEquals("1", success.newConfigs[2]?.label)
    }

    @Test
    fun `execute handles correctly when columns is less than 7 and moving from second row`() {
        // 2 rows, 4 columns = visible global indices: 0, 1, 2, 3, 7, 8, 9, 10
        val configs = MutableList<ButtonConfig?>(49) { null }
        configs[0] = ButtonConfig(id = "b0", label = "0")
        configs[1] = ButtonConfig(id = "b1", label = "1")
        configs[2] = ButtonConfig(id = "b2", label = "2")
        configs[3] = ButtonConfig(id = "b3", label = "3")
        configs[7] = ButtonConfig(id = "b7", label = "7")
        configs[8] = ButtonConfig(id = "b8", label = "8")
        configs[9] = ButtonConfig(id = "b9", label = "9")
        configs[10] = ButtonConfig(id = "b10", label = "10")

        val page = Page(
            id = "p1", bookId = "b1", name = "P1",
            rows = 2, columns = 4,
            buttonConfigs = configs
        )

        // Move b7 (global index 7, visible pos 4) to drop position 1 (before b1, visible pos 1).
        // Since fromPos (4) > toPos (1):
        // Shift visible indices between 1 and 4 right:
        // index 7 (pos 4) gets config from index 3 (pos 3) -> "b3".
        // index 3 (pos 3) gets config from index 2 (pos 2) -> "b2".
        // index 2 (pos 2) gets config from index 1 (pos 1) -> "b1".
        // index 1 (toPos 1) gets "b7".
        val result = useCase.execute(page, 7, 1)
        assertTrue(result is MoveButtonWithInsertUseCase.Result.Success)
        val success = result as MoveButtonWithInsertUseCase.Result.Success

        assertEquals("7", success.movedButtonLabel)
        assertEquals("0", success.newConfigs[0]?.label)
        assertEquals("7", success.newConfigs[1]?.label)
        assertEquals("1", success.newConfigs[2]?.label)
        assertEquals("2", success.newConfigs[3]?.label)
        assertEquals("3", success.newConfigs[7]?.label)
        assertEquals("8", success.newConfigs[8]?.label)
        assertEquals("9", success.newConfigs[9]?.label)
        assertEquals("10", success.newConfigs[10]?.label)
    }
}
