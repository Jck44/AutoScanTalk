package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {
    
    // We don't need a provide method if it's annotated with @Singleton and @Inject 
    // AND we are NOT providing an interface.
    // BUT if we want to BE ABLE to replace it in tests via @TestInstallIn, 
    // it's best to have a module providing it.
    
    // However, for @TestInstallIn to work, Hilt must find a module to replace.
    // If TextToSpeechHelper is just @Inject, there is no module to replace.
    
    // So let's provide it here.
}
