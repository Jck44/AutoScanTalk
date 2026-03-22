package com.andreas_kratzer.ghosttalk.utils

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * A helper class that resets the database and settings.
 * This ensures test isolation and a consistent starting state.
 */
class TestDataResetHelper @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val sampleDataInitializer: SampleDataInitializer
) {

    fun resetData() = runBlocking {
        // 1. Clear SharedPreferences
        settingsRepository.resetToDefaults()

        // 2. Clear Database
        database.clearAllTables()

        // 3. Re-initialize Sample Data
        // Use the same default ID as in MainActivity/AppDatabase migrations
        sampleDataInitializer.initializeIfNeeded("book-default")
    }
}
