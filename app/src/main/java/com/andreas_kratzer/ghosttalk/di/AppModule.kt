package com.andreas_kratzer.ghosttalk.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.data.AppDatabase
import com.andreas_kratzer.ghosttalk.data.BookDao
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageDao
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateDao
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.AppLogger
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePageDao(database: AppDatabase): PageDao {
        return database.pageDao()
    }

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    @Singleton
    fun providePageRepository(pageDao: PageDao): PageRepository {
        return PageRepository(pageDao)
    }

    @Provides
    @Singleton
    fun provideBookRepository(bookDao: BookDao): BookRepository {
        return BookRepository(bookDao)
    }

    @Provides
    fun provideButtonUsageDao(database: AppDatabase): ButtonUsageDao {
        return database.buttonUsageDao()
    }

    @Provides
    @Singleton
    fun provideButtonUsageRepository(buttonUsageDao: ButtonUsageDao): ButtonUsageRepository {
        return ButtonUsageRepository(buttonUsageDao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }



    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher {
        return kotlinx.coroutines.Dispatchers.IO
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }

    @Provides
    fun provideTemplateDao(appDatabase: AppDatabase): TemplateDao {
        return appDatabase.templateDao()
    }

    @Provides
    @Singleton
    fun provideTemplateRepository(
        templateDao: TemplateDao,
        settingsRepository: SettingsRepository
    ): TemplateRepository {
        return TemplateRepository(templateDao, settingsRepository)
    }
}
