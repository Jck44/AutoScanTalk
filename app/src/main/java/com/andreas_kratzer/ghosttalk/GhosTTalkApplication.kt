package com.andreas_kratzer.ghosttalk

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.analytics.FirebaseAnalyticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class GhostTalkApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var firebaseAnalyticsManager: FirebaseAnalyticsManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        val appScope = CoroutineScope(Dispatchers.Main)
        appScope.launch {
            settingsRepository.firebaseAnalyticsEnabledFlow.collect { enabled ->
                firebaseAnalyticsManager.updateConsent(enabled)
                if (enabled) {
                    firebaseAnalyticsManager.logSafeSetup()
                }
            }
        }
    }
}
