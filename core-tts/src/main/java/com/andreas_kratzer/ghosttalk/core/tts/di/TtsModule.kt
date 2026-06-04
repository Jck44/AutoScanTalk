package com.andreas_kratzer.ghosttalk.core.tts.di

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.tts.AndroidTtsProvider
import com.andreas_kratzer.ghosttalk.core.tts.ElevenLabsTtsProvider
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.tts.TtsVoiceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider
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
    fun provideAndroidTtsProvider(
        @ApplicationContext context: Context,
        settingsRepository: TtsSettings,
        routedAudioPlayer: RoutedAudioPlayer,
        voiceManager: TtsVoiceManager,
        audioDeviceManager: AudioDeviceManager
    ): AndroidTtsProvider {
        return AndroidTtsProvider(context, settingsRepository, routedAudioPlayer, voiceManager, audioDeviceManager)
    }

    @Provides
    @Singleton
    fun provideElevenLabsTtsProvider(
        @ApplicationContext context: Context,
        cloudSettings: CloudSettings,
        ttsSettings: TtsSettings,
        routedAudioPlayer: RoutedAudioPlayer,
        @ApplicationScope scope: CoroutineScope
    ): ElevenLabsTtsProvider {
        return ElevenLabsTtsProvider(context, cloudSettings, ttsSettings, routedAudioPlayer, scope)
    }

    @Provides
    @Singleton
    fun provideTextToSpeechHelper(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        settingsRepository: TtsSettings,
        androidTtsProvider: Provider<AndroidTtsProvider>,
        elevenLabsTtsProvider: Provider<ElevenLabsTtsProvider>
    ): TextToSpeechHelper {
        return TextToSpeechHelper(
            context, 
            scope, 
            settingsRepository, 
            androidTtsProvider, 
            elevenLabsTtsProvider
        )
    }
}
