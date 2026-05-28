package com.andreas_kratzer.ghosttalk.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [WorkManagerModule::class]
)
object WorkManagerTestModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        // Initialize WorkManager with a default configuration for tests if needed
        // or use WorkManagerTestInitHelper
        try {
            Configuration.Builder().build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
        } catch (e: Exception) {
            // Already initialized or other error
        }
        return WorkManager.getInstance(context)
    }
    
    // We need to provide other things from AppModule too since we are REPLACING it.
    // This is getting complicated. Maybe just replace the WorkManager provider?
    // In Hilt, we can't replace just one method of a module. We replace the whole module.
}
