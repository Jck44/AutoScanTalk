package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
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
class AppStateRepositoryImpl @Inject constructor() : AppStateRepository {

    private val _isUserModeActive = MutableStateFlow(false)
    override val isUserModeActive: StateFlow<Boolean> = _isUserModeActive.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    override val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _currentPageId = MutableStateFlow<String?>(null)
    override val currentPageId: StateFlow<String?> = _currentPageId.asStateFlow()

    override fun setUserModeActive(isActive: Boolean) {
        _isUserModeActive.value = isActive
    }

    override fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    override fun setCurrentPageId(pageId: String?) {
        _currentPageId.value = pageId
    }
}
