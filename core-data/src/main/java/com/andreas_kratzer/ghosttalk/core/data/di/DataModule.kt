package com.andreas_kratzer.ghosttalk.core.data.di

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.SpeechSettings
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageProvider
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.WeatherRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.AppStateRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.BookRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.ButtonTemplateRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.ButtonUsageRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.data.impl.PageRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.data.impl.TemplateRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.UserModeSessionRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.WeatherRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.AdvancedSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.CallSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.CloudSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.GenAiSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.GeneralSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.NotificationSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.ScanningSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SecuritySettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SmartHomeSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.UserSettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.VoiceSettingsRepository
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonTemplateDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.TemplateDao
import com.andreas_kratzer.ghosttalk.core.settings.AdvancedSettings
import com.andreas_kratzer.ghosttalk.core.settings.CallSettings
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import com.andreas_kratzer.ghosttalk.core.settings.GeneralSettings
import com.andreas_kratzer.ghosttalk.core.settings.ImportExportSettings
import com.andreas_kratzer.ghosttalk.core.settings.NotificationSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.settings.UserSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
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
    abstract fun bindButtonTemplateRepository(impl: ButtonTemplateRepositoryImpl): ButtonTemplateRepository

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
    abstract fun bindUserModeSessionRepository(impl: UserModeSessionRepositoryImpl): UserModeSessionRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVocalProfileRepository(impl: com.andreas_kratzer.ghosttalk.core.data.impl.VocalProfileRepositoryImpl): com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository

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
        fun providePageRepositoryImpl(
            pageDao: PageDao,
            buttonDao: ButtonDao,
            appDatabase: AppDatabase
        ): PageRepositoryImpl {
            return PageRepositoryImpl(pageDao, buttonDao, appDatabase)
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
        fun provideButtonTemplateRepositoryImpl(
            buttonTemplateDao: ButtonTemplateDao
        ): ButtonTemplateRepositoryImpl {
            return ButtonTemplateRepositoryImpl(buttonTemplateDao)
        }





        @Provides
        @Singleton
        fun provideDatabaseSettings(impl: SettingsRepositoryImpl): DatabaseSettings = impl

        @Provides
        @Singleton
        fun provideScanningSettings(impl: ScanningSettingsRepository): ScanningSettings = impl

        @Provides
        @Singleton
        fun provideFeatureSettings(impl: SettingsRepositoryImpl): FeatureSettings = impl

        @Provides
        @Singleton
        fun provideTtsSettings(impl: VoiceSettingsRepository): TtsSettings = impl

        @Provides
        @Singleton
        fun provideGenAiSettings(impl: GenAiSettingsRepository): GenAiSettings = impl

        @Provides
        @Singleton
        fun provideCloudSettings(impl: CloudSettingsRepository): CloudSettings = impl

        @Provides
        @Singleton
        fun provideSmartHomeSettings(impl: SmartHomeSettingsRepository): SmartHomeSettings = impl

        @Provides
        @Singleton
        fun provideImportExportSettings(impl: SettingsRepositoryImpl): ImportExportSettings = impl

        @Provides
        @Singleton
        fun provideSecuritySettings(impl: SecuritySettingsRepository): SecuritySettings = impl

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
        fun provideGeneralSettings(impl: GeneralSettingsRepository): GeneralSettings = impl

        @Provides
        @Singleton
        fun provideUserSettings(impl: UserSettingsRepository): UserSettings = impl

        @Provides
        @Singleton
        fun provideAdvancedSettings(impl: AdvancedSettingsRepository): AdvancedSettings = impl

        @Provides
        @Singleton
        fun provideNotificationSettings(impl: NotificationSettingsRepository): NotificationSettings = impl

        @Provides
        @Singleton
        fun provideCallSettings(impl: CallSettingsRepository): CallSettings = impl

        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        }

        @Provides
        @Singleton
        fun provideSampleDataInitializer(
            bookRepository: BookRepository,
            pageRepository: PageRepository,
            buttonUsageRepository: ButtonUsageRepository
        ): SampleDataInitializer {
            return SampleDataInitializer(bookRepository, pageRepository, buttonUsageRepository)
        }
    }
}
