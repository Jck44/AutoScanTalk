package com.andreas_kratzer.ghosttalk.core.database.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.database.*
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
}
