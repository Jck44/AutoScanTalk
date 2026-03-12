package com.andreas_kratzer.ghosttalk.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.database.TemplateDao
import com.andreas_kratzer.ghosttalk.core.database.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideDatabaseSettings(settingsRepository: SettingsRepository): com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings {
        return settingsRepository
    }

    @Provides
    @Singleton
    fun provideActionLogProvider(actionLogUseCase: ActionLogUseCase): com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider {
        return actionLogUseCase
    }

    @Provides
    @Singleton
    fun provideButtonUsageProvider(buttonUsageRepository: ButtonUsageRepository): com.andreas_kratzer.ghosttalk.core.data.ButtonUsageProvider {
        return buttonUsageRepository
    }

    @Provides
    @Singleton
    fun provideLocalIntentRouter(
        androidClockExecutor: com.andreas_kratzer.ghosttalk.domain.executors.AndroidClockExecutor,
        logger: com.andreas_kratzer.ghosttalk.core.util.Logger
    ): com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter {
        return com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter(androidClockExecutor, logger)
    }

    @Provides
    @Singleton
    fun provideGenAiSettings(settingsRepository: SettingsRepository): com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings {
        return settingsRepository
    }



    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher {
        return kotlinx.coroutines.Dispatchers.IO
    }
}
