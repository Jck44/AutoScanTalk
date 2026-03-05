package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {

    // Note: We can't provide CoroutineScope directly as viewModelScope is not available here.
    // ViewModels will continue to create scoped components like ActionExecutor manually.

}
