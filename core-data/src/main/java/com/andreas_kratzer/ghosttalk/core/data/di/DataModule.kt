package com.andreas_kratzer.ghosttalk.core.data.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.*
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.*
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.TemplateDao
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.ImportExportSettings
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.core.actions.SpeechSettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindPageRepository(impl: PageRepositoryImpl): PageRepository

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindButtonUsageRepository(impl: ButtonUsageRepositoryImpl): ButtonUsageRepository

    @Binds
    @Singleton
    abstract fun bindButtonUsageProvider(impl: ButtonUsageRepositoryImpl): ButtonUsageProvider

    @Binds
    @Singleton
    abstract fun bindAppStateRepository(impl: AppStateRepositoryImpl): AppStateRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPageImportExportProvider(manager: PageImportExportManager): PageImportExportProvider

    companion object {
        @Provides
        @Singleton
        fun provideBookRepositoryImpl(bookDao: BookDao): BookRepositoryImpl {
            return BookRepositoryImpl(bookDao)
        }

        @Provides
        @Singleton
        fun providePageRepositoryImpl(pageDao: PageDao, buttonDao: ButtonDao): PageRepositoryImpl {
            return PageRepositoryImpl(pageDao, buttonDao)
        }

        @Provides
        @Singleton
        fun provideTemplateRepositoryImpl(
            templateDao: TemplateDao,
            settings: DatabaseSettings
        ): TemplateRepositoryImpl {
            return TemplateRepositoryImpl(templateDao, settings)
        }

        @Provides
        @Singleton
        fun provideButtonUsageRepositoryImpl(buttonUsageDao: ButtonUsageDao): ButtonUsageRepositoryImpl {
            return ButtonUsageRepositoryImpl(buttonUsageDao)
        }

        @Provides
        @Singleton
        fun provideSettingsRepositoryImpl(
            @ApplicationContext context: Context,
            bookRepository: BookRepository,
            @ApplicationScope scope: CoroutineScope
        ): SettingsRepositoryImpl {
            return SettingsRepositoryImpl(context, bookRepository, scope)
        }

        @Provides
        @Singleton
        fun provideDatabaseSettings(impl: SettingsRepositoryImpl): DatabaseSettings = impl

        @Provides
        @Singleton
        fun provideScanningSettings(impl: SettingsRepositoryImpl): ScanningSettings = impl

        @Provides
        @Singleton
        fun provideFeatureSettings(impl: SettingsRepositoryImpl): FeatureSettings = impl

        @Provides
        @Singleton
        fun provideTtsSettings(impl: SettingsRepositoryImpl): TtsSettings = impl

        @Provides
        @Singleton
        fun provideGenAiSettings(impl: SettingsRepositoryImpl): GenAiSettings = impl

        @Provides
        @Singleton
        fun provideCloudSettings(impl: SettingsRepositoryImpl): CloudSettings = impl

        @Provides
        @Singleton
        fun provideImportExportSettings(impl: SettingsRepositoryImpl): ImportExportSettings = impl

        @Provides
        @Singleton
        fun provideSecuritySettings(impl: SettingsRepositoryImpl): SecuritySettings = impl

        @Provides
        @Singleton
        fun provideKeyEventSettings(impl: SettingsRepositoryImpl): KeyEventSettings = impl

        @Provides
        @Singleton
        fun provideAudioSettings(impl: SettingsRepositoryImpl): AudioSettings = impl

        @Provides
        @Singleton
        fun provideSpeechSettings(impl: SettingsRepositoryImpl): SpeechSettings = impl

        @Provides
        @Singleton
        fun provideControlDeviceSettings(impl: SettingsRepositoryImpl): ControlDeviceSettings = impl

        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        }

        @Provides
        @Singleton
        fun provideSampleDataInitializer(
            bookRepository: BookRepository,
            pageRepository: PageRepository
        ): SampleDataInitializer {
            return SampleDataInitializer(bookRepository, pageRepository)
        }
    }
}
