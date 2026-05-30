package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.core.domain.actions.SyncLogUseCase
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
    @Singleton
    fun provideSyncLogProvider(syncLogUseCase: SyncLogUseCase): com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider {
        return syncLogUseCase
    }

    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher {
        return kotlinx.coroutines.Dispatchers.IO
    }

    @Provides
    @Singleton
    fun provideBackgroundScheduler(
        impl: com.andreas_kratzer.ghosttalk.domain.workers.BackgroundSchedulerImpl
    ): com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler {
        return impl
    }
}
