package com.andreas_kratzer.ghosttalk.ui.main

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleProfileSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStartupInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val sampleDataInitializer: SampleDataInitializer,
    private val backgroundScheduler: BackgroundScheduler,
    private val rescheduleProfileSyncUseCase: RescheduleProfileSyncUseCase,
    private val pageRepository: PageRepository
) {
    suspend fun run(): String? = withContext(Dispatchers.IO) {
        val defaultBookId = "book-default"

        val migrationPrefs = context.getSharedPreferences("setup_migration_prefs", Context.MODE_PRIVATE)
        val migrationDone = migrationPrefs.getBoolean("setup_completed_migration_done", false)
        Log.d("AppStartupInitializer", "DEBUG_SETUP: migrationDone = $migrationDone, isSetupCompleted = ${settingsRepository.isSetupCompleted}")
        if (!migrationDone) {
            val hasExistingData = bookRepository.getAllBooksList().any { it.id != "book-default" }
            Log.d("AppStartupInitializer", "DEBUG_SETUP: hasExistingData = $hasExistingData")
            if (hasExistingData && !settingsRepository.isSetupCompleted) {
                settingsRepository.isSetupCompleted = true
            }
            migrationPrefs.edit { putBoolean("setup_completed_migration_done", true) }
        }

        if (settingsRepository.isSetupCompleted) {
            // 1. Ensure at least one book exists.
            val initializedBookId = sampleDataInitializer.initializeIfNeeded(defaultBookId)
            
            // 2. Load the user's last active book preference
            val persistedActiveBookId = settingsRepository.activeBookId
            
            // 3. Verify it still exists in the DB
            val finalActiveBookId = if (bookRepository.getBookById(persistedActiveBookId) != null) {
                persistedActiveBookId
            } else {
                initializedBookId
            }

            // 4. Set the final active book
            settingsRepository.activeBookId = finalActiveBookId

            // Background scheduling, sync and DB maintenance are NOT needed to
            // paint the first frame. They run via runDeferredStartupWork() after
            // the UI is shown so startup isn't blocked on them.
            finalActiveBookId
        } else {
            Log.d("AppStartupInitializer", "Setup is not completed yet, skipping database initialization on startup.")
            null
        }
    }

    /**
     * Non-critical startup work (WorkManager scheduling, cloud sync, button purge).
     * Call this after the UI has been shown so it doesn't delay first paint.
     */
    suspend fun runDeferredStartupWork() = withContext(Dispatchers.IO) {
        if (!settingsRepository.isSetupCompleted) return@withContext
        backgroundScheduler.scheduleLocationUpdate()
        backgroundScheduler.scheduleWeatherUpdate()
        rescheduleProfileSyncUseCase.reschedule()
        rescheduleProfileSyncUseCase.runOnceImmediately()
        pageRepository.purgeInstallUpdateButtons()
    }
}
