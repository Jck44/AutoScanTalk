package com.example.gostalk.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gostalk.model.Page
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

    @Delete
    suspend fun deletePage(page: Page)
}
