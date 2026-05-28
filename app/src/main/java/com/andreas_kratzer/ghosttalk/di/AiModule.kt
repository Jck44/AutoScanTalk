package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.ai.domain.LocalIntentRouterImpl
import com.andreas_kratzer.ghosttalk.core.domain.executors.AndroidClockExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindClockExecutor(
        androidClockExecutor: AndroidClockExecutor
    ): ClockExecutor

    @Binds
    @Singleton
    abstract fun bindLocalIntentRouter(
        localIntentRouterImpl: LocalIntentRouterImpl
    ): LocalIntentRouter
}
