package com.andreas_kratzer.ghosttalk.core.data

import kotlinx.coroutines.flow.StateFlow

interface AppStateRepository {
    val isUserModeActive: StateFlow<Boolean>
    val activeBookId: StateFlow<String?>
    val currentPageId: StateFlow<String?>

    fun setUserModeActive(isActive: Boolean)
    fun setActiveBookId(bookId: String?)
    fun setCurrentPageId(pageId: String?)
}
