package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.util.AppLogger
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    @Binds
    @Singleton
    abstract fun bindLogger(impl: AppLogger): Logger
}
