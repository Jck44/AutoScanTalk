package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsCategory(
    viewModel: SettingsViewModel,
    notificationListenerGranted: Boolean,
    monitoredApps: Set<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    PreferenceCategory(stringResource(R.string.settings_category_notifications), modifier = modifier) {
        // 1. Notification Listener Permission
        PermissionRow(
            title = stringResource(R.string.settings_notifications_enable),
            description = stringResource(R.string.settings_notifications_enable_desc),
            isGranted = notificationListenerGranted,
            onRequest = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )

        if (notificationListenerGranted) {
            val isReadingEnabled by viewModel.notifications.isNotificationReadingEnabled.collectAsState()
            SettingsToggleItem(
                label = "Benachrichtigungen vorlesen",
                checked = isReadingEnabled,
                description = "Aktiviert das Vorlesen von Benachrichtigungen.",
                onCheckedChange = { viewModel.notifications.setNotificationReadingEnabled(it) }
            )

            if (isReadingEnabled) {
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                PreferredAppsPicker(
                    monitoredApps = monitoredApps,
                    onToggleApp = { pkg, checked ->
                        viewModel.notifications.toggleMonitoredNotificationApp(pkg, checked)
                    },
                    onToggleAll = { apps ->
                        viewModel.notifications.setMonitoredNotificationApps(apps)
                    }
                )
                TextButton(
                    onClick = { viewModel.notifications.resetMonitoredNotificationAppsToMessagingDefaults() },
                    modifier = Modifier.padding(top = dimensions.paddingSmall)
                ) {
                    Text(stringResource(R.string.settings_notifications_apps_reset))
                }

                Spacer(modifier = Modifier.height(dimensions.paddingSmall))

                // Auto Read Mode Dropdown
                val autoReadModeVal by viewModel.notifications.autoReadMode.collectAsState()
                var modeExpanded by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
                    Text("Automatisches Vorlesen", style = MaterialTheme.typography.titleSmall)
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = !modeExpanded }
                    ) {
                         OutlinedTextField(
                            readOnly = true,
                            value = when(autoReadModeVal) {
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF -> "Aus"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.IMMEDIATE -> "Sofort"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_2_MIN -> "Alle 2 Minuten"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_5_MIN -> "Alle 5 Minuten"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_10_MIN -> "Alle 10 Minuten"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_15_MIN -> "Alle 15 Minuten"
                                com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_30_MIN -> "Alle 30 Minuten"
                            },
                            onValueChange = { },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(when(mode) {
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF -> "Aus"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.IMMEDIATE -> "Sofort"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_2_MIN -> "Alle 2 Minuten"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_5_MIN -> "Alle 5 Minuten"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_10_MIN -> "Alle 10 Minuten"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_15_MIN -> "Alle 15 Minuten"
                                            com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.EVERY_30_MIN -> "Alle 30 Minuten"
                                        })
                                    },
                                    onClick = {
                                        viewModel.notifications.setAutoReadMode(mode)
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (autoReadModeVal != com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode.OFF) {
                    val autoReadOnlyInUserModeVal by viewModel.notifications.autoReadOnlyInUserMode.collectAsState()
                    SettingsToggleItem(
                        label = "Nur im Benutzermodus aktiv",
                        checked = autoReadOnlyInUserModeVal,
                        description = "Wenn aktiv, werden Benachrichtigungen nur im Benutzermodus vorgelesen.",
                        onCheckedChange = { viewModel.notifications.setAutoReadOnlyInUserMode(it) }
                    )

                    val autoReadInStandbyVal by viewModel.notifications.autoReadInStandby.collectAsState()
                    SettingsToggleItem(
                        label = "Auch im Standby vorlesen",
                        checked = autoReadInStandbyVal,
                        description = "Liest Benachrichtigungen auch bei ausgeschaltetem Bildschirm vor.",
                        onCheckedChange = { viewModel.notifications.setAutoReadInStandby(it) }
                    )
                }
            }
        }
    }
}
