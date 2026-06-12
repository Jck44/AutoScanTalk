package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

@Composable
internal fun SystemPermissionsCategory(
    viewModel: SettingsViewModel,
    cameraGranted: Boolean,
    locationGranted: Boolean,
    calendarGranted: Boolean,
    overlayGranted: Boolean,
    packageName: String,
    onCameraRequest: () -> Unit,
    onLocationRequest: () -> Unit,
    onCalendarRequest: () -> Unit,
    onOverlayExplanationRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    PreferenceCategory(stringResource(R.string.settings_category_permissions), modifier = modifier) {
        // 2. Camera Permission
        PermissionRow(
            title = stringResource(R.string.settings_permission_camera),
            description = stringResource(R.string.settings_permission_camera_desc),
            isGranted = cameraGranted,
            onRequest = onCameraRequest
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

        // 3. Location Permission
        PermissionRow(
            title = stringResource(R.string.settings_permission_location),
            description = stringResource(R.string.settings_permission_location_desc),
            isGranted = locationGranted,
            onRequest = onLocationRequest
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

        // 4. Calendar Permission
        PermissionRow(
            title = stringResource(R.string.settings_permission_calendar),
            description = stringResource(R.string.settings_permission_calendar_desc),
            isGranted = calendarGranted,
            onRequest = onCalendarRequest
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))

        // Overlay Permission (Draw over other apps)
        PermissionRow(
            title = stringResource(R.string.settings_permission_overlay),
            description = stringResource(R.string.settings_permission_overlay_desc),
            isGranted = overlayGranted,
            onRequest = onOverlayExplanationRequest
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
                    onClick = { viewModel.call.requestDefaultDialer(activity) },
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
