package com.andreas_kratzer.ghosttalk.core.database.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonTemplateDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.TemplateDao
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionDao
import com.andreas_kratzer.ghosttalk.core.database.VocalProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

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
    fun provideButtonDao(database: AppDatabase): ButtonDao {
        return database.buttonDao()
    }

    @Provides
    fun provideButtonUsageDao(database: AppDatabase): ButtonUsageDao {
        return database.buttonUsageDao()
    }

    @Provides
    fun provideTemplateDao(appDatabase: AppDatabase): TemplateDao {
        return appDatabase.templateDao()
    }

    @Provides
    fun provideButtonTemplateDao(database: AppDatabase): ButtonTemplateDao {
        return database.buttonTemplateDao()
    }

    @Provides
    fun provideUserModeSessionDao(database: AppDatabase): UserModeSessionDao {
        return database.userModeSessionDao()
    }

    @Provides
    fun provideVocalProfileDao(database: AppDatabase): VocalProfileDao {
        return database.vocalProfileDao()
    }
}
