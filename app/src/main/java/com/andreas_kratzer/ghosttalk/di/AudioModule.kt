package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioSettings(settingsRepository: SettingsRepository): AudioSettings
}
