package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val packageName = context.packageName

    val monitoredApps by viewModel.notifications.monitoredNotificationApps.collectAsState()
    
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
        NotificationsCategory(
            viewModel = viewModel,
            notificationListenerGranted = notificationListenerGranted,
            monitoredApps = monitoredApps,
            modifier = Modifier.weight(1f)
        )

        SystemPermissionsCategory(
            viewModel = viewModel,
            cameraGranted = cameraGranted,
            locationGranted = locationGranted,
            calendarGranted = calendarGranted,
            overlayGranted = overlayGranted,
            packageName = packageName,
            onCameraRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onLocationRequest = { 
                locationLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            },
            onCalendarRequest = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
            onOverlayExplanationRequest = { showOverlayExplanationDialog.value = true },
            modifier = Modifier.weight(1f)
        )
    }

    if (showOverlayExplanationDialog.value) {
        com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
            title = stringResource(R.string.settings_permission_overlay_dialog_title),
            onDismiss = { showOverlayExplanationDialog.value = false },
            confirmText = stringResource(R.string.settings_permission_overlay_dialog_confirm),
            onConfirm = {
                showOverlayExplanationDialog.value = false
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                context.startActivity(intent)
            },
            dismissText = stringResource(R.string.settings_permission_overlay_dialog_dismiss)
        ) {
            Text(
                text = stringResource(R.string.settings_permission_overlay_dialog_desc),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}



@Composable
internal fun PermissionRow(
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
