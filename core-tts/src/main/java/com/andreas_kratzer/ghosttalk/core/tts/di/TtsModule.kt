package com.andreas_kratzer.ghosttalk.core.tts.di

import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.tts.TtsVoiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTtsVoiceManager(): TtsVoiceManager {
        return TtsVoiceManager()
    }

    @Provides
    @Singleton
    fun provideTextToSpeechHelper(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        @com.andreas_kratzer.ghosttalk.core.di.ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
        settingsRepository: com.andreas_kratzer.ghosttalk.core.settings.TtsSettings,
        routedAudioPlayer: com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer,
        voiceManager: TtsVoiceManager
    ): TextToSpeechHelper {
        return TextToSpeechHelper(context, scope, settingsRepository, routedAudioPlayer, voiceManager)
    }
}
