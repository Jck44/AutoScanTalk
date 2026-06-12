package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
import com.andreas_kratzer.ghosttalk.feature.settings.domain.MessagingAppsDetector
import com.andreas_kratzer.ghosttalk.feature.settings.ui.ProfileDraftCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class NotificationSettingsDelegate(
    private val settingsRepository: SettingsRepository,
    private val draftCoordinator: ProfileDraftCoordinator,
    private val messagingAppsDetector: MessagingAppsDetector,
    private val scope: CoroutineScope
) {
    val isNotificationReadingEnabled: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isNotificationReadingEnabledFlow) { it.isNotificationReadingEnabled }
    val monitoredNotificationApps: StateFlow<Set<String>> = draftCoordinator.scopedFlow(settingsRepository.monitoredNotificationAppsFlow) { it.monitoredNotificationApps }
    val autoReadMode: StateFlow<AutoReadMode> = draftCoordinator.scopedFlow(settingsRepository.autoReadModeFlow) {
        try {
            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.valueOf(it.autoReadMode)
        } catch (_: Exception) {
            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF
        }
    }
    val autoReadOnlyInUserMode: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.autoReadOnlyInUserModeFlow) { it.autoReadOnlyInUserMode }
    val autoReadInStandby: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.autoReadInStandbyFlow) { it.autoReadInStandby }

    fun setNotificationReadingEnabled(enabled: Boolean) = draftCoordinator.update({
        settingsRepository.isNotificationReadingEnabled = enabled
    }) { it.copy(isNotificationReadingEnabled = enabled) }

    fun setAutoReadMode(mode: AutoReadMode) = draftCoordinator.update({
        settingsRepository.autoReadMode = mode
    }) { it.copy(autoReadMode = mode.name) }

    fun setAutoReadOnlyInUserMode(enabled: Boolean) = draftCoordinator.update({
        settingsRepository.autoReadOnlyInUserMode = enabled
    }) { it.copy(autoReadOnlyInUserMode = enabled) }

    fun setAutoReadInStandby(enabled: Boolean) = draftCoordinator.update({
        settingsRepository.autoReadInStandby = enabled
    }) { it.copy(autoReadInStandby = enabled) }

    fun toggleMonitoredNotificationApp(pkg: String, enabled: Boolean) = draftCoordinator.update({
        val current = settingsRepository.monitoredNotificationApps.toMutableSet()
        if (enabled) current.add(pkg) else current.remove(pkg)
        settingsRepository.monitoredNotificationApps = current
    }) {
        val current = it.monitoredNotificationApps.toMutableSet()
        if (enabled) current.add(pkg) else current.remove(pkg)
        it.copy(monitoredNotificationApps = current)
    }

    fun setMonitoredNotificationApps(apps: Set<String>) = draftCoordinator.update({
        settingsRepository.monitoredNotificationApps = apps
    }) { it.copy(monitoredNotificationApps = apps) }

    fun resetMonitoredNotificationAppsToMessagingDefaults() {
        messagingAppsDetector.resetMonitoredNotificationAppsToMessagingDefaults(scope)
    }
}
