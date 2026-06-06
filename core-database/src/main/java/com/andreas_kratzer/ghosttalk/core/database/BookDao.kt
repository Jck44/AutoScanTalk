package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andreas_kratzer.ghosttalk.core.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    suspend fun getAllBooksList(): List<Book>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: String): Flow<Book?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("UPDATE books SET updatedAt = :timestamp WHERE id = :bookId")
    suspend fun updateLastModified(bookId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE books SET versionSequence = versionSequence + 1, updatedAt = :timestamp WHERE id = :bookId")
    suspend fun incrementVersionSequence(bookId: String, timestamp: Long = System.currentTimeMillis())
}
