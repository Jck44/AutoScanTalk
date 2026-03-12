package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KeyEventModule {

    @Binds
    @Singleton
    abstract fun bindKeyEventSettings(settingsRepository: SettingsRepository): KeyEventSettings
}
