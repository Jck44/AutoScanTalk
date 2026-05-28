package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.*
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

    @Query("SELECT COUNT(*) FROM button_templates")
    suspend fun getTemplateCount(): Int
}
