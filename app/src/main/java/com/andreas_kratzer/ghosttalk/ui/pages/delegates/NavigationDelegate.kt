package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import androidx.lifecycle.SavedStateHandle
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class NavigationDelegate @Inject constructor(
    private val pageManagementDelegate: PageManagementDelegate,
    private val actionExecutor: ActionExecutor,
    private val scanCoordinator: ScanCoordinator,
    private val settingsRepository: SettingsRepository
) {
    private lateinit var scope: CoroutineScope
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var activeBookState: StateFlow<Book?>
    private var onPageChanging: ((isSamePage: Boolean, redoPrediction: Boolean) -> Unit)? = null

    val pageBackStack = mutableListOf<String>()

    fun init(
        scope: CoroutineScope,
        savedStateHandle: SavedStateHandle,
        activeBookState: StateFlow<Book?>,
        onPageChanging: (isSamePage: Boolean, redoPrediction: Boolean) -> Unit
    ) {
        this.scope = scope
        this.savedStateHandle = savedStateHandle
        this.activeBookState = activeBookState
        this.onPageChanging = onPageChanging
    }

    fun restoreState() {
        savedStateHandle.get<String>("currentPageId")?.let { id ->
            scope.launch {
                pageManagementDelegate.getPageById(id)?.let { page ->
                    pageManagementDelegate.setCurrentPage(page)
                }
            }
        }
    }

    fun loadStartPage() {
        pageBackStack.clear()
        val allPages = pageManagementDelegate.allPagesFlow.value
        val startId = settingsRepository.defaultStartPageId ?: allPages.firstOrNull()?.id
        val startPage = allPages.find { it.id == startId }
        if (startPage != null) {
            loadPage(startPage)
        }
    }

    fun loadPage(page: Page) {
        loadPageInternal(page, isBackNavigation = false)
    }

    private fun loadPageInternal(page: Page, isBackNavigation: Boolean = false) {
        scope.launch {
            val currentPage = pageManagementDelegate.currentPage.value
            val isSamePage = currentPage?.id == page.id
            val redoPrediction = settingsRepository.geminiRedoPrediction
            
            // Stoppe laufende Aktionen und Audio der alten Seite
            val skipLog = activeBookState.value?.logStopActions == false
            actionExecutor.stopActions(skipLog = skipLog)
            
            scanCoordinator.onPageChanged(isSamePage)
            onPageChanging?.invoke(isSamePage, redoPrediction)

            // Track backstack: push current page ID before switching
            if (!isSamePage && !isBackNavigation) {
                currentPage?.id?.let { prevId ->
                    if (pageBackStack.lastOrNull() != prevId) {
                        pageBackStack.add(prevId)
                    }
                }
            }

            pageManagementDelegate.setCurrentPage(page)
            savedStateHandle["currentPageId"] = page.id
            savedStateHandle["focusedButtonIndex"] = scanCoordinator.focusedButtonIndex.value
            savedStateHandle["focusedRowIndex"] = scanCoordinator.focusedRowIndex.value
        }
    }

    fun navigateBack() {
        if (pageBackStack.isNotEmpty()) {
            val prevPageId = pageBackStack.removeAt(pageBackStack.lastIndex)
            scope.launch {
                val prevPage = pageManagementDelegate.getPageById(prevPageId)
                if (prevPage != null) {
                    loadPageInternal(prevPage, isBackNavigation = true)
                }
            }
        } else {
            loadStartPage()
        }
    }
}
