package com.andreas_kratzer.ghosttalk.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {

    // Note: We can't provide CoroutineScope directly as viewModelScope is not available here.
    // ViewModels will continue to create scoped components like ActionExecutor manually.

}
