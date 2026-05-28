package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.ActionHandler
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
interface ActionHandlerModule {
    @Multibinds
    fun actionHandlers(): Set<ActionHandler>
}
