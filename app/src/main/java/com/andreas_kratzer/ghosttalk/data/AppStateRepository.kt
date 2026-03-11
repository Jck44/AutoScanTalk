package com.andreas_kratzer.ghosttalk.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A central repository to hold global application state that is needed across
 * different layers (UI, UseCases, Services) to reduce prop-drilling and boilerplate.
 */
@Singleton
class AppStateRepository @Inject constructor() {

    private val _isUserModeActive = MutableStateFlow(false)
    val isUserModeActive: StateFlow<Boolean> = _isUserModeActive.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _currentPageId = MutableStateFlow<String?>(null)
    val currentPageId: StateFlow<String?> = _currentPageId.asStateFlow()

    fun setUserModeActive(isActive: Boolean) {
        _isUserModeActive.value = isActive
    }

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    fun setCurrentPageId(pageId: String?) {
        _currentPageId.value = pageId
    }
}
