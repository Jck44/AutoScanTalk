package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.ActionCoordinator
import com.andreas_kratzer.ghosttalk.core.actions.ActionEventEmitter
import com.andreas_kratzer.ghosttalk.core.actions.ActionLogger
import com.andreas_kratzer.ghosttalk.core.actions.ActionTtsProxy
import com.andreas_kratzer.ghosttalk.core.actions.CameraProvider
import com.andreas_kratzer.ghosttalk.core.actions.CameraProviderImpl
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceTtsProxy
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

    @Binds
    @Singleton
    fun bindCallActionProxy(impl: com.andreas_kratzer.ghosttalk.core.call.SystemCallManager): com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
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
