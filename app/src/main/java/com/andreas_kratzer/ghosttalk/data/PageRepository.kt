package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.flow.Flow

class PageRepository(private val pageDao: PageDao) {

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
        pageDao.insertPage(page)
    }

    suspend fun updatePage(page: Page) {
        pageDao.updatePage(page)
    }

    suspend fun movePages(fromPage: Page, toPage: Page) {
        pageDao.moveButton(fromPage, toPage)
    }

    suspend fun deletePage(page: Page) {
        pageDao.deletePage(page)
    }
}
