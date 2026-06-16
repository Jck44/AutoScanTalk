package com.andreas_kratzer.ghosttalk.ui.pages.history

interface EditCommand {
    val pageId: String?
    val label: EditLabel
    val icon: EditIcon
    suspend fun apply()
    suspend fun revert()
    fun mergeWith(next: EditCommand): EditCommand? = null
}

data class EditLabel(
    val resId: Int,
    val args: List<Any> = emptyList(),
    val isPlural: Boolean = false,
    val quantity: Int = 1
)

enum class EditIcon {
    DELETE, MOVE, EDIT, REORDER, PAGE, BOOK
}
