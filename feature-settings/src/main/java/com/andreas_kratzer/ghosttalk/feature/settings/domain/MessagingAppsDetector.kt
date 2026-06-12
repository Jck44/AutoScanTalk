package com.andreas_kratzer.ghosttalk.feature.settings.domain

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagingAppsDetector(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    @SuppressLint("QueryPermissionsNeeded")
    fun initializeIfNeeded(scope: CoroutineScope) {
        try {
            val sharedPrefs = context.getSharedPreferences("ghosttalk_app_meta", Context.MODE_PRIVATE) ?: return
            val hasInitialized = sharedPrefs.getBoolean("has_initialized_monitored_apps", false)
            if (!hasInitialized) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager ?: return@launch
                        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                        val detectedApps = mutableSetOf<String>()
                        
                        for (resolveInfo in resolveInfos) {
                            val packageName = resolveInfo.activityInfo?.packageName ?: continue
                            if (packageName.isNotEmpty()) {
                                try {
                                    val appInfo = pm.getApplicationInfo(packageName, 0)
                                    if (isMessagingOrSocialApp(packageName, appInfo.category)) {
                                        detectedApps.add(packageName)
                                    }
                                } catch (_: Exception) {
                                    // ignore
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (detectedApps.isNotEmpty()) {
                                val current = settingsRepository.monitoredNotificationApps.toMutableSet()
                                current.addAll(detectedApps)
                                settingsRepository.monitoredNotificationApps = current
                            }
                            sharedPrefs.edit { putBoolean("has_initialized_monitored_apps", true) }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun resetMonitoredNotificationAppsToMessagingDefaults(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager ?: return@launch
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val detectedApps = mutableSetOf<String>()
                
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo?.packageName ?: continue
                    if (packageName.isNotEmpty()) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            if (isMessagingOrSocialApp(packageName, appInfo.category)) {
                                detectedApps.add(packageName)
                            }
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    settingsRepository.monitoredNotificationApps = detectedApps
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun isMessagingOrSocialApp(packageName: String, category: Int): Boolean {
        val pkg = packageName.lowercase()
        
        // Exclude system, utility, mail, browser, contacts, dialer, accessibility, and companion apps that might match CATEGORY_SOCIAL
        val excludeKeywords = listOf(
            "browser", "watch", "wear", "companion", "launcher", "keyboard", 
            "weather", "clock", "email", "mail", "gmail", "calendar", "chrome", 
            "firefox", "opera", "edge", "safari", "system", "service", "provider",
            "contacts", "dialer", "phone", "accessibility", "hearing", "speech", 
            "transcribe", "translate"
        )
        if (excludeKeywords.any { pkg.contains(it) }) {
            return false
        }

        // 1. Exact or prefix matches for well-known messaging app package names
        val knownPackages = listOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
            "org.thoughtcrime.securesms", // Signal
            "com.facebook.orca", "com.facebook.mlite", // Messenger
            "com.discord",
            "com.skype.raider", "com.skype.m2",
            "com.viber.voip",
            "ch.threema.app", "ch.threema.app.work",
            "jp.naver.line.android",
            "com.tencent.mm", // WeChat
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.dynamite", // Google Chat
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms" // Default System SMS
        )
        if (knownPackages.any { pkg == it || pkg.startsWith("$it.") }) {
            return true
        }

        // 2. Check if category is social
        if (category == ApplicationInfo.CATEGORY_SOCIAL) {
            return true
        }
        
        // 3. Fallback to general keywords (only if not excluded by excludeKeywords)
        val knownKeywords = listOf(
            "whatsapp", "telegram", "signal", "messenger", "discord", "skype", 
            "viber", "threema", "wechat", "imessage", "sms"
        )
        return knownKeywords.any { pkg.contains(it) }
    }
}
