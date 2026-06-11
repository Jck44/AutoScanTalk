package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedEntity(deletedEntity: DeletedEntity)

    @Query("SELECT * FROM deleted_entities WHERE bookId = :bookId")
    suspend fun getDeletedEntitiesForBook(bookId: String): List<DeletedEntity>

    @Query("SELECT * FROM deleted_entities WHERE bookId = :bookId AND deletedAt >= :since")
    suspend fun getDeletedEntitiesForBookSince(bookId: String, since: Long): List<DeletedEntity>

    @Query("DELETE FROM deleted_entities WHERE bookId = :bookId")
    suspend fun deleteTombstonesForBook(bookId: String)

    @Query("DELETE FROM deleted_entities WHERE deletedAt < :cutoff")
    suspend fun pruneTombstones(cutoff: Long)
}
