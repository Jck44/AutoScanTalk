package com.andreas_kratzer.ghosttalk.di

import com.andreas_kratzer.ghosttalk.core.scanning.FeatureGuardProxy
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerFeedbackProvider
import com.andreas_kratzer.ghosttalk.core.scanning.TtsScannerFeedbackProvider
import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
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

    @Binds
    @Singleton
    abstract fun bindFeatureGuardProxy(
        featureGuard: FeatureGuard
    ): FeatureGuardProxy

    @Binds
    @Singleton
    abstract fun bindScannerActionProvider(
        actionExecutor: com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
    ): com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
}
