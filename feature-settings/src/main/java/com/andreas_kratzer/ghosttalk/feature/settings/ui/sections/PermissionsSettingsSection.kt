package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.graphics.createBitmap
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextLight
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
    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val showOverlayExplanationDialog = remember { mutableStateOf(false) }


    
    // Check when screen is focused or resumed
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                  ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                notificationListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
                calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        notificationListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        overlayGranted = Settings.canDrawOverlays(context)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // Overlay Permission (Draw over other apps)
            PermissionRow(
                title = stringResource(R.string.settings_permission_overlay),
                description = stringResource(R.string.settings_permission_overlay_desc),
                isGranted = overlayGranted,
                onRequest = {
                    showOverlayExplanationDialog.value = true
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // 5. Default Dialer Role
            var isDefaultDialer by remember { mutableStateOf(false) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                        isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dialer_register_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_dialer_register_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Chip indicating status
                    val isDark = isSystemInDarkTheme()
                    val (chipBg, chipContentColor) = if (isDefaultDialer) {
                        if (isDark) StatusActiveBgDark to StatusActiveTextDark
                        else StatusActiveBgLight to StatusActiveTextLight
                    } else {
                        if (isDark) StatusInactiveBgDark to StatusInactiveTextDark
                        else StatusInactiveBgLight to StatusInactiveTextLight
                    }
                    val chipIcon = if (isDefaultDialer) Icons.Default.Check else Icons.Default.Warning
                    val chipText = if (isDefaultDialer) {
                        stringResource(R.string.settings_permission_active)
                    } else {
                        stringResource(R.string.settings_permission_inactive)
                    }
                    
                    Surface(
                        color = chipBg,
                        contentColor = chipContentColor,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                
                val activity = context.findActivity()
                if (activity != null) {
                    Button(
                        onClick = { viewModel.requestDefaultDialer(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !isDefaultDialer
                    ) {
                        if (isDefaultDialer) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(dimensions.paddingSmall))
                            Text(stringResource(R.string.settings_dialer_registered))
                        } else {
                            Text(stringResource(R.string.settings_dialer_register_title))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

            // Rerun setup wizard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                Text(
                    text = stringResource(R.string.settings_permissions_relaunch_setup_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_permissions_relaunch_setup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                Button(
                    onClick = { viewModel.triggerStartSetupWizard() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.settings_permissions_relaunch_setup_button))
                }
            }
        }
    }

    if (showOverlayExplanationDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOverlayExplanationDialog.value = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_permission_overlay_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_permission_overlay_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayExplanationDialog.value = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:$packageName".toUri()
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text(text = stringResource(R.string.settings_permission_overlay_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOverlayExplanationDialog.value = false }
                ) {
                    Text(text = stringResource(R.string.settings_permission_overlay_dialog_dismiss))
                }
            }
        )
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
    val isDark = isSystemInDarkTheme()
    
    // Status-colors matching design guidelines
    val (chipBg, chipContentColor) = if (isGranted) {
        if (isDark) StatusActiveBgDark to StatusActiveTextDark
        else StatusActiveBgLight to StatusActiveTextLight
    } else {
        if (isDark) StatusInactiveBgDark to StatusInactiveTextDark
        else StatusInactiveBgLight to StatusInactiveTextLight
    }
    
    val chipIcon = if (isGranted) Icons.Default.Check else Icons.Default.Warning
    val chipText = if (isGranted) {
        stringResource(R.string.settings_permission_active)
    } else {
        stringResource(R.string.settings_permission_inactive)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
            .run {
                if (!isGranted) {
                    clickable(onClick = onRequest)
                } else this
            }
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
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
        ) {
            Surface(
                color = chipBg,
                contentColor = chipContentColor,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = chipIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
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
        val bitmap = createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1)
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    } catch (_: Exception) {
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("QueryPermissionsNeeded", "LocalContextGetResourceValueCall")
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
                } catch (_: Exception) {
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

    val currentLabel = remember(monitoredApps, installedApps, context) {
        if (monitoredApps.isEmpty()) {
            context.getString(R.string.settings_notifications_no_preferred_apps)
        } else if (installedApps.isEmpty()) {
            context.applicationContext.resources.getQuantityString(
                R.plurals.settings_notifications_apps_selected,
                monitoredApps.size,
                monitoredApps.size
            )
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
                context.applicationContext.resources.getQuantityString(
                    R.plurals.settings_notifications_apps_more_format,
                    remaining,
                    firstThree,
                    remaining
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.settings_notifications_manage_preferred_apps), style = MaterialTheme.typography.titleSmall)
        
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
                        text = { Text(stringResource(R.string.settings_notifications_loading_apps)) },
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
                            Text(stringResource(R.string.settings_notifications_select_all))
                        }
                        TextButton(
                            onClick = { onToggleAll(emptySet()) }
                        ) {
                            Text(stringResource(R.string.settings_notifications_deselect_all))
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

