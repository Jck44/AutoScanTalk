package com.andreas_kratzer.ghosttalk.core.analytics

import android.content.Context
import android.os.Bundle
import com.andreas_kratzer.ghosttalk.BuildConfig
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    private val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(context)
    }

    /**
     * Updates consent dynamically for both Analytics and Crashlytics.
     */
    fun updateConsent(enabled: Boolean) {
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Logs anonymous app configuration states as User Properties for optimizing defaults.
     * Absolutely no sensitive information (API keys, PINs, emails) is sent.
     */
    fun logSafeSetup() {
        if (!settingsRepository.firebaseAnalyticsEnabled) return

        try {
            // Log version info
            firebaseAnalytics.setUserProperty("app_version_name", BuildConfig.VERSION_NAME)
            firebaseAnalytics.setUserProperty("app_version_code", BuildConfig.VERSION_CODE.toString())

            // Log safe scanner settings
            firebaseAnalytics.setUserProperty("scan_delay", settingsRepository.scanDelayMillis.toString())
            firebaseAnalytics.setUserProperty("holding_time", settingsRepository.holdingTimeMillis.toString())
            firebaseAnalytics.setUserProperty("auto_start_scanning", settingsRepository.autoStartScanning.toString())
            firebaseAnalytics.setUserProperty("static_row_enabled", settingsRepository.staticRowEnabled.toString())
            firebaseAnalytics.setUserProperty("vocal_switch_active", settingsRepository.isVocalSwitchEnabledFlow.value.toString())
            firebaseAnalytics.setUserProperty("theme_mode", settingsRepository.themeMode)
            firebaseAnalytics.setUserProperty("only_record_hardware_stats", settingsRepository.onlyRecordHardwareStats.toString())

            // Log a general setup status event
            val bundle = Bundle().apply {
                putString("status", "initialized")
            }
            firebaseAnalytics.logEvent("app_config_sync", bundle)
        } catch (e: Exception) {
            // Prevent initialization issues from crashing the app
        }
    }

    /**
     * Logs high-level feature usage in aggregate (no user content).
     */
    fun logFeatureUsed(featureType: String) {
        if (!settingsRepository.firebaseAnalyticsEnabled) return

        try {
            val bundle = Bundle().apply {
                putString("feature_type", featureType)
            }
            firebaseAnalytics.logEvent("feature_triggered", bundle)
        } catch (e: Exception) {
            // Fail-silent
        }
    }
}
