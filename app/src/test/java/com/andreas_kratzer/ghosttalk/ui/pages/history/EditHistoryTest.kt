package com.andreas_kratzer.ghosttalk.ui.pages.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditHistoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    class TestCommand(
        val name: String,
        override val pageId: String? = null,
        override val label: EditLabel = EditLabel(0),
        override val icon: EditIcon = EditIcon.EDIT,
        private val onApply: () -> Unit = {},
        private val onRevert: () -> Unit = {}
    ) : EditCommand {
        override suspend fun apply() {
            onApply()
        }

        override suspend fun revert() {
            onRevert()
        }
    }

    @Test
    fun testExecuteUndoRedo() = runTest(testDispatcher) {
        val history = EditHistory(limit = 5, onPageNavigate = {})
        var count = 0
        val cmd = TestCommand(
            name = "cmd1",
            onApply = { count++ },
            onRevert = { count-- }
        )

        history.execute(cmd)
        assertEquals(1, count)
        assertEquals(true, history.state.value.canUndo)
        assertEquals(false, history.state.value.canRedo)

        history.undo()
        assertEquals(0, count)
        assertEquals(false, history.state.value.canUndo)
        assertEquals(true, history.state.value.canRedo)

        history.redo()
        assertEquals(1, count)
        assertEquals(true, history.state.value.canUndo)
        assertEquals(false, history.state.value.canRedo)
    }

    @Test
    fun testLimit() = runTest(testDispatcher) {
        val history = EditHistory(limit = 3, onPageNavigate = {})
        val commands = (1..5).map { i ->
            TestCommand(name = "cmd$i")
        }

        commands.forEach { history.execute(it) }

        // Limit is 3, so oldest 2 commands (cmd1, cmd2) should have dropped out.
        // Stack should contain cmd3, cmd4, cmd5
        assertEquals(3, history.state.value.entries.size)
        
        // We can only undo 3 times
        history.undo()
        history.undo()
        history.undo()
        assertEquals(false, history.state.value.canUndo)
    }

    @Test
    fun testUndoTo() = runTest(testDispatcher) {
        var navigatedPageId: String? = null
        val history = EditHistory(limit = 5, onPageNavigate = { navigatedPageId = it })
        var revertedCount = 0
        val commands = (1..4).map { i ->
            TestCommand(
                name = "cmd$i",
                onRevert = { revertedCount++ }
            )
        }

        commands.forEach { history.execute(it) }

        // entries are reversed: [cmd4, cmd3, cmd2, cmd1]
        // index 0 -> cmd4
        // index 1 -> cmd3
        // index 2 -> cmd2
        // Undo to index 1 (means undoing cmd4 and cmd3)
        history.undoTo(1)

        assertEquals(2, revertedCount)
        assertEquals(2, history.state.value.entries.size)
    }
}
