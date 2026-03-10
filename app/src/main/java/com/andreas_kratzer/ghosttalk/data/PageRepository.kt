package com.andreas_kratzer.ghosttalk.data

import androidx.room.Transaction
import com.andreas_kratzer.ghosttalk.data.entities.toButtonEntities
import com.andreas_kratzer.ghosttalk.model.Page
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
}
