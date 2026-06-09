package com.andreas_kratzer.ghosttalk.ui.setup.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@Composable
fun BasicPermissionsStepContent(
    cameraGranted: Boolean,
    locationGranted: Boolean,
    microphoneGranted: Boolean,
    contactsGranted: Boolean,
    smsGranted: Boolean,
    calendarGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Text(
        text = stringResource(R.string.setup_basic_perms_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_basic_perms_desc),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_camera),
            isGranted = cameraGranted,
            description = stringResource(R.string.setup_permission_camera_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_mic),
            isGranted = microphoneGranted,
            description = stringResource(R.string.setup_permission_mic_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_contacts_phone),
            isGranted = contactsGranted,
            description = stringResource(R.string.setup_permission_contacts_phone_desc)
        )
        PermissionStatusRow(
            label = stringResource(R.string.setup_permission_sms),
            isGranted = smsGranted,
            description = stringResource(R.string.setup_permission_sms_desc)
        )
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_calendar),
            isGranted = calendarGranted,
            description = stringResource(R.string.setup_permission_calendar_desc)
        )
        PermissionStatusRow(
            label = stringResource(SettingsR.string.settings_permission_location),
            isGranted = locationGranted,
            description = stringResource(R.string.setup_permission_location_desc)
        )
    }

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(R.string.setup_btn_request_permissions))
    }
}
