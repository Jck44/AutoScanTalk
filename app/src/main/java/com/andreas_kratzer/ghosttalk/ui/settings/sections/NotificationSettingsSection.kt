package com.andreas_kratzer.ghosttalk.ui.settings.sections

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@Composable
fun NotificationSettingsSection(viewModel: SettingsViewModel) {
    val isEnabled by viewModel.isNotificationReadingEnabled.collectAsState(false)
    val monitoredApps by viewModel.monitoredNotificationApps.collectAsState(emptySet())
    val context = LocalContext.current
    val packageName = context.packageName
    val dimensions = LocalDimensions.current

    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    val hasPermission = enabledListeners.contains(packageName)

    PreferenceCategory(stringResource(R.string.settings_category_notifications)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { 
                if (hasPermission) {
                    viewModel.setNotificationReadingEnabled(!isEnabled)
                } else {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }.padding(vertical = dimensions.paddingSmall)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_notifications_enable),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_notifications_enable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasPermission) {
                Switch(checked = isEnabled, onCheckedChange = { viewModel.setNotificationReadingEnabled(it) })
            } else {
                TextButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.settings_notifications_permission_button))
                }
            }
        }

        if (isEnabled && hasPermission) {
            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))
            val apps = listOf(
                "com.whatsapp" to "WhatsApp",
                "org.thoughtcrime.securesms" to "Signal",
                "org.telegram.messenger" to "Telegram",
                "com.google.android.apps.messaging" to "Messages"
            )

            apps.forEach { (pkg, name) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.toggleMonitoredNotificationApp(pkg, !monitoredApps.contains(pkg))
                    }.padding(vertical = dimensions.paddingSmall)
                ) {
                    Text(
                        text = name, 
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(checked = monitoredApps.contains(pkg), onCheckedChange = { viewModel.toggleMonitoredNotificationApp(pkg, it) })
                }
            }
        }
    }
}
