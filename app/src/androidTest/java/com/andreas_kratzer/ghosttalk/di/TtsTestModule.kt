package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.tts.TtsRecordingHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [TtsModule::class]
)
abstract class TtsTestModule {

    @Binds
    @Singleton
    abstract fun bindTextToSpeechHelper(
        ttsRecordingHelper: TtsRecordingHelper
    ): TextToSpeechHelper
}
