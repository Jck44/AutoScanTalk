package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.ActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.SpeechActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.PlayMediaActionHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface CoreActionHandlerModule {

    @Binds
    @IntoSet
    fun bindSpeechActionHandler(handler: SpeechActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindControlDeviceActionHandler(handler: ControlDeviceActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindPlayMediaActionHandler(handler: PlayMediaActionHandler): ActionHandler
}
