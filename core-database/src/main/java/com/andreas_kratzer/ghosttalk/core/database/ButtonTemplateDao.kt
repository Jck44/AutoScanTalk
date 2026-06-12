package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonTemplateDao {
    @Query("SELECT * FROM button_templates ORDER BY orderIndex ASC")
    fun getAllTemplatesFlow(): Flow<List<ButtonTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ButtonTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<ButtonTemplateEntity>)

    @Delete
    suspend fun deleteTemplate(template: ButtonTemplateEntity)

    @Query("DELETE FROM button_templates WHERE id IN (:ids)")
    suspend fun deleteTemplatesByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM button_templates")
    suspend fun getTemplateCount(): Int

    @Query("SELECT * FROM button_templates")
    suspend fun getAllTemplates(): List<ButtonTemplateEntity>
}

