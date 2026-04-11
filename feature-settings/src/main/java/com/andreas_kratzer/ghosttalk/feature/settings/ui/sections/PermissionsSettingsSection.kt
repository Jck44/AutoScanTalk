package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val packageName = context.packageName

    // Notification Reader State
    val isNotificationReadingEnabled by viewModel.isNotificationReadingEnabled.collectAsState(false)
    val monitoredApps by viewModel.monitoredNotificationApps.collectAsState(emptySet())
    
    // Permission States
    var cameraGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationListenerGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName))
    }
    var calendarGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }

    // Refresh states when returning to screen (approximation)
    LaunchedEffect(Unit) {
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        notificationListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        locationGranted = permissions.values.any { it }
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarGranted = granted
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        PreferenceCategory(stringResource(R.string.settings_category_notifications), modifier = Modifier.weight(1f)) {
            
            // 1. Notification Listener Permission
            PermissionRow(
                title = stringResource(R.string.settings_notifications_enable),
                description = stringResource(R.string.settings_notifications_enable_desc),
                isGranted = notificationListenerGranted,
                onRequest = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )

            if (notificationListenerGranted) {
                SettingsToggleItem(
                    label = "Audio-Hinweis aktivieren",
                    checked = isNotificationReadingEnabled,
                    onCheckedChange = { viewModel.setNotificationReadingEnabled(it) }
                )

                if (isNotificationReadingEnabled) {
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

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // 2. Camera Permission
            PermissionRow(
                title = stringResource(R.string.settings_permission_camera),
                description = stringResource(R.string.settings_permission_camera_desc),
                isGranted = cameraGranted,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // 3. Location Permission
            PermissionRow(
                title = stringResource(R.string.settings_permission_location),
                description = stringResource(R.string.settings_permission_location_desc),
                isGranted = locationGranted,
                onRequest = { 
                    locationLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // 4. Calendar Permission
            PermissionRow(
                title = stringResource(R.string.settings_permission_calendar),
                description = stringResource(R.string.settings_permission_calendar_desc),
                isGranted = calendarGranted,
                onRequest = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val dimensions = LocalDimensions.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isGranted) stringResource(R.string.settings_permission_active) else stringResource(R.string.settings_permission_inactive),
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (!isGranted) {
            TextButton(
                onClick = onRequest,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.settings_permission_request))
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val dimensions = LocalDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
