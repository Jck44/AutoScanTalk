package com.andreas_kratzer.ghosttalk.feature.settings.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingAppsDetectorTest {

    private val context = mockk<Context>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val detector = MessagingAppsDetector(context, settingsRepository)

    @Test
    fun testIsMessagingOrSocialApp_knownApps() {
        assertTrue(detector.isMessagingOrSocialApp("com.whatsapp", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(detector.isMessagingOrSocialApp("org.telegram.messenger", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(detector.isMessagingOrSocialApp("org.thoughtcrime.securesms", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(detector.isMessagingOrSocialApp("com.facebook.orca", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(detector.isMessagingOrSocialApp("com.google.android.apps.messaging", ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test
    fun testIsMessagingOrSocialApp_excludedKeywords() {
        // Even if category is social, browsers or clocks or watch apps should be excluded
        assertFalse(detector.isMessagingOrSocialApp("com.android.chrome", ApplicationInfo.CATEGORY_SOCIAL))
        assertFalse(detector.isMessagingOrSocialApp("com.google.android.apps.gmail", ApplicationInfo.CATEGORY_SOCIAL)) // gmail / mail
        assertFalse(detector.isMessagingOrSocialApp("com.samsung.android.wear.companion", ApplicationInfo.CATEGORY_SOCIAL))
        assertFalse(detector.isMessagingOrSocialApp("com.sec.android.app.clockpackage", ApplicationInfo.CATEGORY_SOCIAL))
    }

    @Test
    fun testIsMessagingOrSocialApp_socialCategoryFallback() {
        // Fallback for random app that has social category and is not excluded
        assertTrue(detector.isMessagingOrSocialApp("com.some.social.network", ApplicationInfo.CATEGORY_SOCIAL))
    }

    @Test
    fun testIsMessagingOrSocialApp_keywordFallback() {
        // Fallback for packages containing keywords
        assertTrue(detector.isMessagingOrSocialApp("org.test.whatsappapp", ApplicationInfo.CATEGORY_UNDEFINED))
        assertTrue(detector.isMessagingOrSocialApp("com.sub.messenger.app", ApplicationInfo.CATEGORY_UNDEFINED))
        assertFalse(detector.isMessagingOrSocialApp("com.mail.messenger.helper", ApplicationInfo.CATEGORY_UNDEFINED)) // excluded by mail keyword
    }
}
