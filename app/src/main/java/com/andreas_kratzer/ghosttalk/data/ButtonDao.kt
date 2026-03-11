package com.andreas_kratzer.ghosttalk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andreas_kratzer.ghosttalk.data.entities.ButtonEntity

@Dao
interface ButtonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertButtons(buttons: List<ButtonEntity>)

    @Query("DELETE FROM buttons WHERE pageId = :pageId")
    suspend fun deleteButtonsForPage(pageId: String)
    
    @Query("SELECT * FROM buttons WHERE pageId = :pageId")
    suspend fun getButtonsForPage(pageId: String): List<ButtonEntity>
}
