package com.andreas_kratzer.ghosttalk.core.cloud.di

import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthManager(googleAuthManager: GoogleAuthManager): AuthManager
}
