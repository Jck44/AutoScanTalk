package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor
import com.andreas_kratzer.ghosttalk.core.domain.executors.AndroidClockExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindClockExecutor(
        androidClockExecutor: AndroidClockExecutor
    ): ClockExecutor
}
