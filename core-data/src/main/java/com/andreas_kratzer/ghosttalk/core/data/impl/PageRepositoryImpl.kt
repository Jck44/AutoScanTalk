package com.andreas_kratzer.ghosttalk.core.data.impl

import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.toButtonEntities
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PageRepositoryImpl(
    private val pageDao: PageDao,
    private val buttonDao: ButtonDao,
    private val appDatabase: AppDatabase
) : PageRepository {

    override fun getAllPagesFlow(): Flow<List<Page>> {
        return pageDao.getAllPagesFlow()
    }

    override fun getPagesForBookFlow(bookId: String): Flow<List<Page>> {
        return pageDao.getPagesForBookFlow(bookId)
    }

    override suspend fun getPageById(id: String): Page? {
        return pageDao.getPageById(id)
    }

    override suspend fun getAllPages(): List<Page> {
        return pageDao.getAllPages()
    }

    override suspend fun getPagesForBook(bookId: String): List<Page> {
        return pageDao.getPagesForBook(bookId)
    }

    override suspend fun insertPage(page: Page) {
        appDatabase.withTransaction {
            pageDao.insertPageEntity(page)
            buttonDao.insertButtons(page.toButtonEntities())
        }
    }

    override suspend fun updatePage(page: Page) {
        appDatabase.withTransaction {
            pageDao.updatePageEntity(page)
            // Refresh buttons: delete old and insert new
            buttonDao.deleteButtonsForPage(page.id)
            buttonDao.insertButtons(page.toButtonEntities())
        }
    }

    override suspend fun updatePageSettingsOnly(page: Page) {
        pageDao.updatePageEntity(page)
    }

    override suspend fun movePages(fromPage: Page, toPage: Page) {
        appDatabase.withTransaction {
            updatePage(fromPage)
            updatePage(toPage)
        }
    }

    override suspend fun deletePage(page: Page) {
        // buttons will be deleted via CASCADE FK
        pageDao.deletePageEntity(page)
    }

    override suspend fun deletePagesForBook(bookId: String) {
        pageDao.deletePagesForBook(bookId)
    }

    override suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String? {
        return appDatabase.withTransaction {
            val original = pageDao.getPageWithButtonsById(pageId) ?: return@withTransaction null
            val newPageId = java.util.UUID.randomUUID().toString()
            
            val newPage = original.page.copy(
                id = newPageId,
                name = "${original.page.name}${duplicateSuffix}",
                createdAt = System.currentTimeMillis()
            )
            
            val newButtons = original.buttons.map { entity ->
                entity.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    pageId = newPageId
                )
            }
            
            pageDao.insertPageEntity(newPage)
            buttonDao.insertButtons(newButtons)
            
            newPageId
        }
    }

    override fun getUsedTemplateIdsFlow(): Flow<Set<String>> {
        return pageDao.getAllPagesFlow().map { pages ->
            pages.mapNotNull { it.templateId }.toSet()
        }
    }

    override suspend fun deleteEmptyButtons(): Int {
        return buttonDao.deleteEmptyButtons()
    }

    override suspend fun updatePageName(pageId: String, name: String) {
        pageDao.updatePageName(pageId, name)
    }

    override suspend fun updatePageScanPattern(pageId: String, scanPattern: String?) {
        pageDao.updatePageScanPattern(pageId, scanPattern)
    }

    override suspend fun updatePageRowNames(pageId: String, rowNames: List<String>) {
        pageDao.updatePageRowNames(pageId, rowNames)
    }

    override suspend fun updatePageGridSize(pageId: String, rows: Int, columns: Int) {
        pageDao.updatePageGridSize(pageId, rows, columns)
    }
}
