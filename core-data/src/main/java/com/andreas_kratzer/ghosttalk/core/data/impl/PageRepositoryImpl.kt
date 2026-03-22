package com.andreas_kratzer.ghosttalk.core.data.impl

import androidx.room.Transaction
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.toButtonEntities
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow

class PageRepositoryImpl(
    private val pageDao: PageDao,
    private val buttonDao: ButtonDao
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
        pageDao.insertPageEntity(page)
        buttonDao.insertButtons(page.toButtonEntities())
    }

    override suspend fun updatePage(page: Page) {
        pageDao.updatePageEntity(page)
        // Refresh buttons: delete old and insert new
        buttonDao.deleteButtonsForPage(page.id)
        buttonDao.insertButtons(page.toButtonEntities())
    }

    override suspend fun updatePageSettingsOnly(page: Page) {
        pageDao.updatePageEntity(page)
    }

    override suspend fun movePages(fromPage: Page, toPage: Page) {
        updatePage(fromPage)
        updatePage(toPage)
    }

    override suspend fun deletePage(page: Page) {
        // buttons will be deleted via CASCADE FK
        pageDao.deletePageEntity(page)
    }

    override suspend fun deletePagesForBook(bookId: String) {
        pageDao.deletePagesForBook(bookId)
    }

    @Transaction
    override suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String? {
        val original = pageDao.getPageWithButtonsById(pageId) ?: return null
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
        
        return newPageId
    }
}
