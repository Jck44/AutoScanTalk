package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY isBuiltIn DESC, name ASC")
    fun getAllTemplatesFlow(): Flow<List<PageTemplate>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplateById(id: String): PageTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: PageTemplate)

    @Delete
    suspend fun deleteTemplate(template: PageTemplate)
}
