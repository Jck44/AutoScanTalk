package com.andreas_kratzer.ghosttalk.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.flow.Flow

import androidx.room.Transaction
import com.andreas_kratzer.ghosttalk.data.entities.PageWithButtons
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
}
