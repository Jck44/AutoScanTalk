package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.*
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ActionInfrastructureModule {

    @Binds
    @Singleton
    fun bindActionLogger(impl: ActionCoordinator): ActionLogger

    @Binds
    @Singleton
    fun bindActionEventEmitter(impl: ActionCoordinator): ActionEventEmitter

    @Binds
    @Singleton
    fun bindCameraProvider(impl: CameraProviderImpl): CameraProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ActionProxyModule {

    @Provides
    @Singleton
    fun provideActionTtsProxy(ttsHelper: dagger.Lazy<TextToSpeechHelper>): ActionTtsProxy {
        return object : ActionTtsProxy {
            override val isReady: Boolean get() = ttsHelper.get().isReady
            override var isReadingNotification: Boolean 
                get() = ttsHelper.get().isReadingNotification
                set(value) { ttsHelper.get().isReadingNotification = value }
            
            override fun speakRouted(text: String, deviceAddress: String?, queueMode: Int, isForCues: Boolean, onDone: (() -> Unit)?) {
                ttsHelper.get().speakRouted(text, deviceAddress, queueMode, isForCues, onDone)
            }
        }
    }

    @Provides
    @Singleton
    fun provideControlDeviceTtsProxy(ttsHelper: dagger.Lazy<TextToSpeechHelper>): ControlDeviceTtsProxy {
        return object : ControlDeviceTtsProxy {
            override val isReady: Boolean get() = ttsHelper.get().isReady
            override var isReadingNotification: Boolean 
                get() = ttsHelper.get().isReadingNotification
                set(value) { ttsHelper.get().isReadingNotification = value }
            
            override fun speakRouted(text: String, deviceAddress: String?, onDone: (() -> Unit)?) {
                ttsHelper.get().speakRouted(text, deviceAddress, onDone = onDone)
            }
        }
    }
}
