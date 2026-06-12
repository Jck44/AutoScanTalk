package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.AiRestructureDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.NavigationDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BookRestructureViewModel @Inject constructor(
    private val aiRestructureDelegate: AiRestructureDelegate,
    private val pageManagementDelegate: PageManagementDelegate,
    private val navigationDelegate: NavigationDelegate,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            pageManagementDelegate.activeBookId.collect { bookId ->
                if (bookId != null) {
                    aiRestructureDelegate.setAiRestructureProposal(loadProposalFromCache(bookId))
                } else {
                    aiRestructureDelegate.setAiRestructureProposal(null)
                }
            }
        }

        viewModelScope.launch {
            val unfilteredFlow = pageManagementDelegate.unfilteredPages
            val activeTargetFlow = pageManagementDelegate.activeTargetPageIds
            combine(
                unfilteredFlow,
                activeTargetFlow
            ) { pages, activeIds ->
                Pair(pages, activeIds)
            }.collect { (pages, activeIds) ->
                if (pages.isNotEmpty() && aiRestructureDelegate.selectedPageIds.value.isEmpty()) {
                    aiRestructureDelegate.selectActivePagesOnly(pages, activeIds)
                }
            }
        }
    }

    val activeBookId = pageManagementDelegate.activeBookId
    val selectedPageIds: StateFlow<Set<String>> = aiRestructureDelegate.selectedPageIds
    val aiRestructureProposal: StateFlow<BookRestructureProposal?> = aiRestructureDelegate.aiRestructureProposal
    val aiHierarchyProposal: StateFlow<BookHierarchyProposal?> = aiRestructureDelegate.aiHierarchyProposal
    val aiPageLayoutProposals: StateFlow<Map<String, PageLayoutProposal>> = aiRestructureDelegate.aiPageLayoutProposals
    val isAiHierarchyLoading: StateFlow<Boolean> = aiRestructureDelegate.isAiHierarchyLoading
    val isLoadingPageLayout: StateFlow<Map<String, Boolean>> = aiRestructureDelegate.isLoadingPageLayout
    val aiRestructureScope: StateFlow<String> = aiRestructureDelegate.aiRestructureScope
    val aiRestructureError: StateFlow<String?> = aiRestructureDelegate.aiRestructureError
    val isAiRestructureLoading: StateFlow<Boolean> = aiRestructureDelegate.isAiRestructureLoading

    fun setAiRestructureScope(scope: String) {
        aiRestructureDelegate.setAiRestructureScope(scope)
    }

    fun clearAiRestructureError() {
        aiRestructureDelegate.clearAiRestructureError()
    }

    fun togglePageSelection(pageId: String) {
        aiRestructureDelegate.togglePageSelection(pageId)
    }

    fun selectAllPages() {
        aiRestructureDelegate.selectAllPages(pageManagementDelegate.unfilteredPages.value)
    }

    fun selectActivePagesOnly() {
        aiRestructureDelegate.selectActivePagesOnly(
            pages = pageManagementDelegate.unfilteredPages.value,
            activeTargetPageIds = pageManagementDelegate.activeTargetPageIds.value
        )
    }

    fun clearAiRestructureProposal() {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.deleteRestructureCache(viewModelScope, bookId)
    }

    fun generateAiHierarchyProposal(feedback: String? = null) {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.generateAiHierarchyProposal(viewModelScope, bookId, pageManagementDelegate, feedback)
    }

    fun updateHierarchyManualEdit(updatedProposal: BookHierarchyProposal) {
        aiRestructureDelegate.setAiHierarchyProposal(updatedProposal)
    }

    fun loadPageLayoutProposal(pageName: String) {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.loadPageLayoutProposal(viewModelScope, bookId, pageManagementDelegate, pageName)
    }

    fun loadAllPageLayoutProposals(onComplete: () -> Unit) {
        val hierarchy = aiHierarchyProposal.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val missingPages = hierarchy.pages.filter { !aiPageLayoutProposals.value.containsKey(it.name) }
            for (node in missingPages) {
                loadPageLayoutProposal(node.name)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun applyHierarchyProposal(onResult: (String) -> Unit) {
        val currentBookId = activeBookId.value ?: return
        aiRestructureDelegate.applyHierarchyProposal(
            scope = viewModelScope,
            currentBookId = currentBookId,
            pageManagementDelegate = pageManagementDelegate,
            setActiveBookId = { bookId ->
                navigationDelegate.pageBackStack.clear()
                settingsRepository.activeBookId = bookId
                pageManagementDelegate.setActiveBookId(bookId)
            },
            loadPage = { page ->
                navigationDelegate.loadPage(page)
            },
            onResult = onResult
        )
    }

    fun loadProposalFromCache(bookId: String): BookRestructureProposal? {
        return aiRestructureDelegate.loadProposalFromCache(bookId)
    }
}
