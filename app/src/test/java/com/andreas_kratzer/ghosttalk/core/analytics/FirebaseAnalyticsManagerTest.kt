package com.andreas_kratzer.ghosttalk.core.analytics

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.*
import org.junit.Before
import org.junit.Test

class FirebaseAnalyticsManagerTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var context: Context
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var firebaseCrashlytics: FirebaseCrashlytics
    private lateinit var manager: FirebaseAnalyticsManager

    @Before
    fun setUp() {
        settingsRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        firebaseAnalytics = mockk(relaxed = true)
        firebaseCrashlytics = mockk(relaxed = true)

        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseAnalytics.getInstance(any()) } returns firebaseAnalytics

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns firebaseCrashlytics

        manager = FirebaseAnalyticsManager(settingsRepository, context)
    }

    @Test
    fun testUpdateConsentEnabled() {
        manager.updateConsent(true)
        verify { firebaseAnalytics.setAnalyticsCollectionEnabled(true) }
        verify { firebaseCrashlytics.setCrashlyticsCollectionEnabled(true) }
    }

    @Test
    fun testUpdateConsentDisabled() {
        manager.updateConsent(false)
        verify { firebaseAnalytics.setAnalyticsCollectionEnabled(false) }
        verify { firebaseCrashlytics.setCrashlyticsCollectionEnabled(false) }
    }
}
