package com.andreas_kratzer.ghosttalk.core.di

import com.andreas_kratzer.ghosttalk.core.actions.ActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.GeminiActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.NavigationActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.SmartHomeActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.SmartPredictionActionHandler
import com.andreas_kratzer.ghosttalk.core.actions.WeatherActionHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface AppActionHandlerModule {

    @Binds
    @IntoSet
    fun bindNavigationActionHandler(handler: NavigationActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindWeatherActionHandler(handler: WeatherActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindGeminiActionHandler(handler: GeminiActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindSmartHomeActionHandler(handler: SmartHomeActionHandler): ActionHandler

    @Binds
    @IntoSet
    fun bindSmartPredictionActionHandler(handler: SmartPredictionActionHandler): ActionHandler
}
