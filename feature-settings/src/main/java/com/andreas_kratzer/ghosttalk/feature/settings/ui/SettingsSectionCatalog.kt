package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.feature.settings.R

enum class SettingsSection(private val titleRes: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_category_general, Icons.Default.Settings),
    PROFILE(R.string.settings_category_profile, Icons.Default.Person),
    MANAGE_BOOK(R.string.settings_category_manage_book, GhostTalkIcons.Book),
    VOICE(R.string.settings_category_voice, Icons.Default.PlayArrow),
    AUDIO_HARDWARE(R.string.settings_category_audio_hardware, GhostTalkIcons.VolumeUp),
    SCANNING(R.string.settings_category_scanning, GhostTalkIcons.SwitchAccessShortcut),
    VOCAL_SWITCH(R.string.settings_category_vocal_switch, GhostTalkIcons.RecordVoiceOver),
    PERMISSIONS(R.string.settings_category_notifications, GhostTalkIcons.Notifications),
    SECURITY(R.string.settings_category_security, GhostTalkIcons.Security),
    AI(R.string.settings_category_gemini, GhostTalkIcons.AutoAwesome),
    CALLS(R.string.settings_category_call, Icons.Default.Phone),
    SMART_INTEGRATION(R.string.settings_category_smart_home, Icons.Default.Home),
    CLOUD_SYNC(R.string.settings_category_cloud, GhostTalkIcons.Cloud),
    ACCOUNTS(R.string.settings_category_accounts, GhostTalkIcons.ManageAccounts),
    MAINTENANCE(R.string.settings_category_maintenance, GhostTalkIcons.Science);

    fun getTitleRes(): Int {
        return titleRes
    }
}

data class SettingsSearchItem(
    val title: String,
    val description: String,
    val section: SettingsSection
)

val ProfileEditSections = listOf(
    SettingsSection.GENERAL,
    SettingsSection.VOICE,
    SettingsSection.AUDIO_HARDWARE,
    SettingsSection.SCANNING,
    SettingsSection.VOCAL_SWITCH,
    SettingsSection.PERMISSIONS,
    SettingsSection.SECURITY,
    SettingsSection.AI,
    SettingsSection.CALLS,
    SettingsSection.SMART_INTEGRATION,
    SettingsSection.CLOUD_SYNC
)
