package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideActionLogProvider(actionLogUseCase: ActionLogUseCase): com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider {
        return actionLogUseCase
    }

    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher {
        return kotlinx.coroutines.Dispatchers.IO
    }
}
