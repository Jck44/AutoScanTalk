package com.andreas_kratzer.ghosttalk.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages")
    fun getAllPagesFlow(): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    fun getPagesForBookFlow(bookId: String): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: String): Page?

    @Query("SELECT * FROM pages")
    suspend fun getAllPages(): List<Page>

    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    suspend fun getPagesForBook(bookId: String): List<Page>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page)

    @androidx.room.Update
    suspend fun updatePage(page: Page)

    @androidx.room.Transaction
    suspend fun moveButton(fromPage: Page, toPage: Page) {
        updatePage(fromPage)
        updatePage(toPage)
    }

    @Delete
    suspend fun deletePage(page: Page)
}
