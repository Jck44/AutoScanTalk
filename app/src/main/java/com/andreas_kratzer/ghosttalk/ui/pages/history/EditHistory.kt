package com.andreas_kratzer.ghosttalk.ui.pages.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class HistoryEntry(
    val label: EditLabel,
    val icon: EditIcon
)

data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val entries: List<HistoryEntry> = emptyList(),
    val nextRedoLabel: EditLabel? = null
)

class EditHistory(
    private val limit: Int = 50,
    private val onPageNavigate: suspend (String) -> Unit
) {
    val mutex = kotlinx.coroutines.sync.Mutex()
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    /** Command ausführen UND registrieren. Löscht den Redo-Stack. */
    suspend fun execute(command: EditCommand) {
        command.apply()
        val merged = undoStack.lastOrNull()?.mergeWith(command)
        if (merged != null) {
            undoStack.removeLast()
            undoStack.addLast(merged)
        } else {
            undoStack.addLast(command)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
        publish()
    }

    suspend fun undo(): Boolean = mutex.withLock {
        val cmd = undoStack.removeLastOrNull() ?: return false
        cmd.revert()
        cmd.pageId?.let { onPageNavigate(it) }
        redoStack.addLast(cmd)
        publish()
        return true
    }

    suspend fun redo(): Boolean = mutex.withLock {
        val cmd = redoStack.removeLastOrNull() ?: return false
        cmd.apply()
        cmd.pageId?.let { onPageNavigate(it) }
        undoStack.addLast(cmd)
        publish()
        return true
    }

    /** Mehrschritt-Rücksprung: bis Verlaufseintrag [index] (0 = neuester) zurück. */
    suspend fun undoTo(index: Int): Boolean = mutex.withLock {
        var lastPageId: String? = null
        repeat(index + 1) {
            val cmd = undoStack.removeLastOrNull() ?: return@repeat
            cmd.revert()
            lastPageId = cmd.pageId
            redoStack.addLast(cmd)
        }
        lastPageId?.let { onPageNavigate(it) }
        publish()
        return true
    }

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        publish()
    }

    private fun publish() {
        _state.value = HistoryState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            entries = undoStack.reversed().map { HistoryEntry(it.label, it.icon) },
            nextRedoLabel = redoStack.lastOrNull()?.label
        )
    }
}
