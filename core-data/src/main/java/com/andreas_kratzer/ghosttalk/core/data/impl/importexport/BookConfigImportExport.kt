package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.importexport.BookConfigWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookConfigImportExport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val settingsMapper: SettingsMapper
) {
    fun exportBookConfigToJson(bookId: String): String {
        val configSettings = settingsMapper.exportConfigSettings(bookId)
        val wrapper = BookConfigWrapper(
            version = 1,
            bookId = bookId,
            lastModified = getBookConfigLastModified(bookId),
            settings = configSettings
        )
        return ImportExportJson.encodeToString(BookConfigWrapper.serializer(), wrapper)
    }

    fun importBookConfigFromJson(jsonString: String, bookId: String): Result<Unit> {
        return try {
            val wrapper = ImportExportJson.decodeFromString<BookConfigWrapper>(jsonString)
            settingsMapper.importSettings(wrapper.settings)
            settingsRepository.updateConfigLastModified(bookId)
            val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
            prefs.edit {
                putLong("config_last_synced_remote_time_$bookId", wrapper.lastModified)
                putLong("config_last_synced_local_time_$bookId", settingsRepository.getConfigLastModified(bookId))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBookConfigLastModified(bookId: String): Long {
        return settingsRepository.getConfigLastModified(bookId)
    }
}
