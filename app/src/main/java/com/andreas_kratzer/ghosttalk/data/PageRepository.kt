package com.andreas_kratzer.ghosttalk.data

import androidx.room.Transaction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.data.entities.toButtonEntities
import kotlinx.coroutines.flow.Flow

class PageRepository(
    private val pageDao: PageDao,
    private val buttonDao: ButtonDao
) {

    fun getAllPagesFlow(): Flow<List<Page>> {
        return pageDao.getAllPagesFlow()
    }

    fun getPagesForBookFlow(bookId: String): Flow<List<Page>> {
        return pageDao.getPagesForBookFlow(bookId)
    }

    suspend fun getPageById(id: String): Page? {
        return pageDao.getPageById(id)
    }

    suspend fun getAllPages(): List<Page> {
        return pageDao.getAllPages()
    }

    suspend fun getPagesForBook(bookId: String): List<Page> {
        return pageDao.getPagesForBook(bookId)
    }

    suspend fun insertPage(page: Page) {
        pageDao.insertPageEntity(page)
        buttonDao.insertButtons(page.toButtonEntities())
    }

    suspend fun updatePage(page: Page) {
        pageDao.updatePageEntity(page)
        // Refresh buttons: delete old and insert new
        buttonDao.deleteButtonsForPage(page.id)
        buttonDao.insertButtons(page.toButtonEntities())
    }

    suspend fun updatePageSettingsOnly(page: Page) {
        pageDao.updatePageEntity(page)
    }

    suspend fun movePages(fromPage: Page, toPage: Page) {
        updatePage(fromPage)
        updatePage(toPage)
    }

    suspend fun deletePage(page: Page) {
        // buttons will be deleted via CASCADE FK
        pageDao.deletePageEntity(page)
    }

    suspend fun deletePagesForBook(bookId: String) {
        pageDao.deletePagesForBook(bookId)
    }

    @Transaction
    suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String? {
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
