package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecuritySettings(settingsRepository: SettingsRepository): SecuritySettings
}
