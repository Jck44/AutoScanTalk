package com.andreas_kratzer.ghosttalk.ui.pages.history

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import kotlinx.coroutines.flow.first

class UpdateButtonConfigCommand(
    private val delegate: PageManagementDelegate,
    override val pageId: String,
    private val index: Int,
    private val oldConfig: ButtonConfig?,
    private val newConfig: ButtonConfig?,
    override val label: EditLabel,
    override val icon: EditIcon
) : EditCommand {
    override suspend fun apply() {
        val updatedPage = delegate.updateButtonConfigUseCase.execute(pageId, index, newConfig)
        if (updatedPage != null && delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }

    override suspend fun revert() {
        val updatedPage = delegate.updateButtonConfigUseCase.execute(pageId, index, oldConfig)
        if (updatedPage != null && delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }
}

class MoveButtonCommand(
    private val delegate: PageManagementDelegate,
    override val pageId: String,
    private val fromIndex: Int,
    private val toIndex: Int,
    override val label: EditLabel,
    override val icon: EditIcon = EditIcon.MOVE
) : EditCommand {
    override suspend fun apply() {
        val updatedPage = delegate.moveButtonUseCase.execute(pageId, fromIndex, toIndex)
        if (updatedPage != null && delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }

    override suspend fun revert() {
        val updatedPage = delegate.moveButtonUseCase.execute(pageId, toIndex, fromIndex)
        if (updatedPage != null && delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }
}

class PageSnapshotCommand(
    private val delegate: PageManagementDelegate,
    override val pageId: String,
    private val oldConfigs: List<ButtonConfig?>,
    private val newConfigs: List<ButtonConfig?>,
    override val label: EditLabel,
    override val icon: EditIcon
) : EditCommand {
    override suspend fun apply() {
        val page = delegate.pageRepository.getPageById(pageId) ?: return
        val updatedPage = page.copy(buttonConfigs = newConfigs)
        delegate.pageRepository.updatePage(updatedPage)
        delegate.bookRepository.updateLastModified(page.bookId)
        if (delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }

    override suspend fun revert() {
        val page = delegate.pageRepository.getPageById(pageId) ?: return
        val updatedPage = page.copy(buttonConfigs = oldConfigs)
        delegate.pageRepository.updatePage(updatedPage)
        delegate.bookRepository.updateLastModified(page.bookId)
        if (delegate.currentPage.value?.id == pageId) {
            delegate.setCurrentPage(updatedPage)
        }
    }
}

class PageFullSnapshotCommand(
    private val delegate: PageManagementDelegate,
    private val oldPage: Page,
    private val newPage: Page,
    override val label: EditLabel,
    override val icon: EditIcon
) : EditCommand {
    override val pageId: String? get() = oldPage.id
    override suspend fun apply() {
        delegate.pageRepository.updatePage(newPage)
        delegate.bookRepository.updateLastModified(newPage.bookId)
        if (delegate.currentPage.value?.id == newPage.id) {
            delegate.setCurrentPage(newPage)
        }
    }

    override suspend fun revert() {
        delegate.pageRepository.updatePage(oldPage)
        delegate.bookRepository.updateLastModified(oldPage.bookId)
        if (delegate.currentPage.value?.id == oldPage.id) {
            delegate.setCurrentPage(oldPage)
        }
    }
}

class CreatePageCommand(
    private val delegate: PageManagementDelegate,
    private val name: String,
    private val rows: Int,
    private val columns: Int,
    private val bookId: String,
    private val templateId: String?,
    private val onCreated: (String) -> Unit
) : EditCommand {
    private var createdPage: Page? = null
    override val pageId: String? get() = createdPage?.id
    override val label = EditLabel(R.string.history_create_page, listOf(name))
    override val icon = EditIcon.PAGE

    override suspend fun apply() {
        val page = createdPage
        if (page != null) {
            delegate.pageRepository.insertPage(page)
            delegate.bookRepository.updateLastModified(bookId)
        } else {
            val currentPages = delegate.allPagesFlow.value
            val generatedId = delegate.createPageUseCase.execute(name, rows, columns, bookId, currentPages, templateId)
            createdPage = delegate.pageRepository.getPageById(generatedId)
            delegate.bookRepository.updateLastModified(bookId)
            onCreated(generatedId)
        }
    }

    override suspend fun revert() {
        createdPage?.let { page ->
            delegate.deletePageUseCase.execute(page, deleteUsages = false)
        }
    }
}

class DeletePageCommand(
    private val delegate: PageManagementDelegate,
    private val page: Page,
    private val deleteUsages: Boolean
) : EditCommand {
    override val pageId: String get() = page.id
    override val label = EditLabel(R.string.history_delete_page, listOf(page.name))
    override val icon = EditIcon.PAGE

    private var deletedPageUsages: List<Page>? = null
    private var deletedTemplateUsages: List<PageTemplate>? = null

    override suspend fun apply() {
        if (deleteUsages && deletedPageUsages == null && deletedTemplateUsages == null) {
            val pageId = page.id
            val allPages = delegate.pageRepository.getAllPages()
            deletedPageUsages = allPages.filter { otherPage ->
                otherPage.buttonConfigs.any { config ->
                    val action = config?.buttonAction
                    action is NavigateToPageButtonAction && action.pageId == pageId
                }
            }

            val allTemplates = delegate.templateRepository.getAllTemplates().first()
            deletedTemplateUsages = allTemplates.filter { template ->
                template.buttonConfigs.any { config ->
                    val action = config?.buttonAction
                    action is NavigateToPageButtonAction && action.pageId == pageId
                }
            }
        }

        delegate.deletePageUseCase.execute(page, deleteUsages)
    }

    override suspend fun revert() {
        delegate.pageRepository.insertPage(page)
        delegate.bookRepository.updateLastModified(page.bookId)

        deletedPageUsages?.forEach { originalPage ->
            delegate.pageRepository.updatePage(originalPage)
        }

        deletedTemplateUsages?.forEach { originalTemplate ->
            delegate.templateRepository.insert(originalTemplate)
        }
    }
}

class MoveButtonToPageCommand(
    private val delegate: PageManagementDelegate,
    private val fromPageId: String,
    private val fromIndices: List<Int>,
    private val toPageId: String,
    private val forceMove: Boolean,
    override val label: EditLabel,
    private val onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
) : EditCommand {
    private var oldFromPage: Page? = null
    private var oldToPage: Page? = null
    private var newFromPage: Page? = null
    private var newToPage: Page? = null
    private var hasCallbackFired = false

    override val pageId: String get() = fromPageId
    override val icon = EditIcon.MOVE

    override suspend fun apply() {
        if (oldFromPage == null) {
            oldFromPage = delegate.pageRepository.getPageById(fromPageId)
            oldToPage = delegate.pageRepository.getPageById(toPageId)
        }
        val result = delegate.moveButtonToPageUseCase.execute(fromPageId, fromIndices, toPageId, forceMove)
        if (result is MoveButtonToPageUseCase.MoveResult.Success) {
            newFromPage = result.fromPage
            newToPage = result.toPage
            if (delegate.currentPage.value?.id == fromPageId) {
                delegate.setCurrentPage(result.fromPage)
            } else if (delegate.currentPage.value?.id == toPageId) {
                delegate.setCurrentPage(result.toPage)
            }
        }
        if (!hasCallbackFired) {
            hasCallbackFired = true
            onResult(result)
        }
    }

    override suspend fun revert() {
        val f = oldFromPage
        val t = oldToPage
        if (f != null && t != null) {
            delegate.pageRepository.updatePage(f)
            delegate.pageRepository.updatePage(t)
            delegate.bookRepository.updateLastModified(f.bookId)
            if (delegate.currentPage.value?.id == fromPageId) {
                delegate.setCurrentPage(f)
            } else if (delegate.currentPage.value?.id == toPageId) {
                delegate.setCurrentPage(t)
            }
        }
    }
}

class DuplicateButtonToPageCommand(
    private val delegate: PageManagementDelegate,
    private val fromPageId: String,
    private val fromIndices: List<Int>,
    private val toPageId: String,
    private val forceMove: Boolean,
    override val label: EditLabel,
    private val onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
) : EditCommand {
    private var oldToPage: Page? = null
    private var newToPage: Page? = null
    private var hasCallbackFired = false

    override val pageId: String get() = toPageId
    override val icon = EditIcon.EDIT

    override suspend fun apply() {
        if (oldToPage == null) {
            oldToPage = delegate.pageRepository.getPageById(toPageId)
        }
        val result = delegate.duplicateButtonToPageUseCase.execute(fromPageId, fromIndices, toPageId, forceMove)
        if (result is MoveButtonToPageUseCase.MoveResult.Success) {
            newToPage = result.toPage
            if (delegate.currentPage.value?.id == toPageId) {
                delegate.setCurrentPage(result.toPage)
            }
        }
        if (!hasCallbackFired) {
            hasCallbackFired = true
            onResult(result)
        }
    }

    override suspend fun revert() {
        val t = oldToPage
        if (t != null) {
            delegate.pageRepository.updatePage(t)
            delegate.bookRepository.updateLastModified(t.bookId)
            if (delegate.currentPage.value?.id == toPageId) {
                delegate.setCurrentPage(t)
            }
        }
    }
}

class PageSplitCommand(
    private val delegate: PageManagementDelegate,
    private val bookId: String,
    private val sourcePageId: String,
    private val preSplitPages: List<Page>,
    override val label: EditLabel
) : EditCommand {
    override val pageId: String get() = sourcePageId
    override val icon = EditIcon.REORDER

    private var postSplitPages: List<Page>? = null

    override suspend fun apply() {
        val post = postSplitPages
        if (post != null) {
            val currentPages = delegate.pageRepository.getPagesForBook(bookId)
            val postIds = post.map { it.id }.toSet()
            currentPages.forEach { page ->
                if (page.id !in postIds) {
                    delegate.deletePageUseCase.execute(page, deleteUsages = false)
                }
            }
            post.forEach { page ->
                delegate.pageRepository.insertPage(page)
            }
            delegate.bookRepository.updateLastModified(bookId)
            preSplitPages.find { it.id == sourcePageId }?.let { delegate.setCurrentPage(it) }
        } else {
            postSplitPages = delegate.pageRepository.getPagesForBook(bookId)
        }
    }

    override suspend fun revert() {
        val currentPages = delegate.pageRepository.getPagesForBook(bookId)
        val preIds = preSplitPages.map { it.id }.toSet()
        currentPages.forEach { page ->
            if (page.id !in preIds) {
                delegate.deletePageUseCase.execute(page, deleteUsages = false)
            }
        }
        preSplitPages.forEach { page ->
            delegate.pageRepository.insertPage(page)
        }
        delegate.bookRepository.updateLastModified(bookId)
        preSplitPages.find { it.id == sourcePageId }?.let { delegate.setCurrentPage(it) }
    }
}
