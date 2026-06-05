package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface PageDao {
    @Transaction
    @Query("SELECT * FROM pages")
    fun getAllPagesWithButtonsFlow(): Flow<List<PageWithButtons>>

    fun getAllPagesFlow(): Flow<List<Page>> = getAllPagesWithButtonsFlow().map { list ->
        list.map { it.toDomainModel() }
    }

    @Transaction
    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    fun getPagesForBookWithButtonsFlow(bookId: String): Flow<List<PageWithButtons>>

    fun getPagesForBookFlow(bookId: String): Flow<List<Page>> = getPagesForBookWithButtonsFlow(bookId).map { list ->
        list.map { it.toDomainModel() }
    }

    @Transaction
    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageWithButtonsById(id: String): PageWithButtons?

    suspend fun getPageById(id: String): Page? = getPageWithButtonsById(id)?.toDomainModel()

    @Transaction
    @Query("SELECT * FROM pages")
    suspend fun getAllPagesWithButtons(): List<PageWithButtons>

    suspend fun getAllPages(): List<Page> = getAllPagesWithButtons().map { it.toDomainModel() }

    @Transaction
    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    suspend fun getPagesForBookWithButtons(bookId: String): List<PageWithButtons>

    suspend fun getPagesForBook(bookId: String): List<Page> = getPagesForBookWithButtons(bookId).map { it.toDomainModel() }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageEntity(page: Page)

    @androidx.room.Update
    suspend fun updatePageEntity(page: Page)

    @Delete
    suspend fun deletePageEntity(page: Page)

    // IMPORTANT: Only use for import! Does NOT create tombstones.
    @Query("DELETE FROM pages WHERE bookId = :bookId")
    suspend fun deletePagesForBook(bookId: String)

    @Query("UPDATE pages SET name = :name, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageName(pageId: String, name: String, updatedAt: Long)

    @Query("UPDATE pages SET scanPattern = :scanPattern, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageScanPattern(pageId: String, scanPattern: String?, updatedAt: Long)

    @Query("UPDATE pages SET rowNames = :rowNames, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageRowNames(pageId: String, rowNames: List<String>, updatedAt: Long)

    @Query("UPDATE pages SET `rows` = :rows, `columns` = :columns, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageGridSize(pageId: String, rows: Int, columns: Int, updatedAt: Long)
}
