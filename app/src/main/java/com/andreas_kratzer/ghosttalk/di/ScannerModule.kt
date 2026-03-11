package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.scanning.ScannerFeedbackProvider
import com.andreas_kratzer.ghosttalk.core.scanning.TtsScannerFeedbackProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindScannerFeedbackProvider(
        ttsScannerFeedbackProvider: TtsScannerFeedbackProvider
    ): ScannerFeedbackProvider
}
