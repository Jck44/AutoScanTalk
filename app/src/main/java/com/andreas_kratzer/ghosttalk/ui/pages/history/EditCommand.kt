package com.andreas_kratzer.ghosttalk.ui.pages.history

import androidx.annotation.StringRes

interface EditCommand {
    val pageId: String?
    val label: EditLabel
    val icon: EditIcon
    suspend fun apply()
    suspend fun revert()
    fun mergeWith(next: EditCommand): EditCommand? = null
}

data class EditLabel(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList()
)

enum class EditIcon {
    DELETE, MOVE, EDIT, REORDER, PAGE, BOOK
}
