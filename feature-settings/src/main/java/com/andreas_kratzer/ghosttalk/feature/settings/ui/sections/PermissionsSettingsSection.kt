package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val packageName = context.packageName

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
                PreferredAppsPicker(
                    monitoredApps = monitoredApps,
                    onToggleApp = { pkg, checked ->
                        viewModel.toggleMonitoredNotificationApp(pkg, checked)
                    },
                    onToggleAll = { apps ->
                        viewModel.setMonitoredNotificationApps(apps)
                    }
                )
                TextButton(
                    onClick = { viewModel.resetMonitoredNotificationAppsToMessagingDefaults() },
                    modifier = Modifier.padding(top = dimensions.paddingSmall)
                ) {
                    Text(stringResource(R.string.settings_notifications_apps_reset))
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
    description: String? = null,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.Bitmap?
)

private fun android.graphics.drawable.Drawable.toBitmapOrNull(): android.graphics.Bitmap? {
    try {
        val bitmap = android.graphics.Bitmap.createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    } catch (e: Exception) {
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredAppsPicker(
    monitoredApps: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleAll: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)?.toBitmapOrNull()
                } catch (e: Exception) {
                    null
                }
                if (packageName.isNotEmpty()) InstalledAppInfo(packageName, label, icon) else null
            }
            val sortedApps = appsList.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = sortedApps
            }
        }
    }

    val currentLabel = remember(monitoredApps, installedApps) {
        if (monitoredApps.isEmpty()) {
            "Keine bevorzugten Apps ausgewählt"
        } else if (installedApps.isEmpty()) {
            if (monitoredApps.size == 1) "1 App ausgewählt" else "${monitoredApps.size} Apps ausgewählt"
        } else {
            val selectedLabels = monitoredApps.mapNotNull { pkg ->
                installedApps.find { it.packageName == pkg }?.label
            }.filter { it.isNotEmpty() && !it.contains(".") }
            
            val displayLabels = if (selectedLabels.size == monitoredApps.size) {
                selectedLabels
            } else {
                monitoredApps.map { pkg ->
                    installedApps.find { it.packageName == pkg }?.label ?: pkg
                }
            }

            if (displayLabels.size <= 3) {
                displayLabels.joinToString(", ")
            } else {
                val firstThree = displayLabels.take(3).joinToString(", ")
                val remaining = displayLabels.size - 3
                "$firstThree und $remaining weitere"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text("Bevorzugte Apps verwalten:", style = MaterialTheme.typography.titleSmall)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (installedApps.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Lade Apps...") },
                        onClick = {}
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { onToggleAll(installedApps.map { it.packageName }.toSet()) }
                        ) {
                            Text("Alle auswählen")
                        }
                        TextButton(
                            onClick = { onToggleAll(emptySet()) }
                        ) {
                            Text("Alle abwählen")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.paddingSmall))

                    installedApps.forEach { app ->
                        val isChecked = monitoredApps.contains(app.packageName)
                        DropdownMenuItem(
                            text = { Text(app.label) },
                            leadingIcon = {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onToggleApp(app.packageName, checked)
                                    }
                                )
                            },
                            onClick = {
                                onToggleApp(app.packageName, !isChecked)
                            }
                        )
                    }
                }
            }
        }
    }
}

