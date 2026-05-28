package com.andreas_kratzer.ghosttalk.core.ai.domain

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiToolsModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindDriveSearchTool(tool: DriveSearchTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindWikipediaTool(tool: WikipediaTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSpotifyTool(tool: SpotifyTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCalendarListTool(tool: CalendarListTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTasksListTool(tool: TasksListTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindGmailReadTool(tool: GmailReadTool): AiTool

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCalendarCreateTool(tool: CalendarCreateTool): AiTool
}
