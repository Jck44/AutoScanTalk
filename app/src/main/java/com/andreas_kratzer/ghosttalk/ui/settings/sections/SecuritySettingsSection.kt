package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.PinEntryDialog

@Composable
fun SecuritySettingsSection(
    securityPin: String?,
    onSecurityPinChange: (String) -> Unit,
    securityPinTimeoutMinutes: Long,
    onSecurityPinTimeoutChange: (Long) -> Unit,
    isPinRequiredForDeletion: Boolean,
    onPinRequiredForDeletionChange: (Boolean) -> Unit,
    isBiometricEnabled: Boolean,
    onBiometricEnabledChange: (Boolean) -> Unit,
    isSecurityRequiredForEdit: Boolean,
    onSecurityRequiredForEditChange: (Boolean) -> Unit,
    isSecurityRequiredForSettings: Boolean,
    onSecurityRequiredForSettingsChange: (Boolean) -> Unit,
    onLockClicked: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_category_security),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_security_pin_label))
                Text(
                    text = if (securityPin.isNullOrEmpty()) 
                        stringResource(R.string.settings_security_pin_inactive) 
                    else 
                        "**** (${stringResource(R.string.settings_security_pin_active)})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = { showPinDialog = true }) {
                Text(
                    if (securityPin.isNullOrEmpty()) 
                        stringResource(R.string.settings_security_pin_set) 
                    else 
                        stringResource(R.string.settings_security_pin_change)
                )
            }
        }

        if (showPinDialog) {
            PinEntryDialog(
                onDismiss = { showPinDialog = false },
                onConfirm = {
                    onSecurityPinChange(it)
                    showPinDialog = false
                },
                title = if (securityPin.isNullOrEmpty()) 
                    stringResource(R.string.security_pin_new) 
                else 
                    stringResource(R.string.settings_security_pin_change)
            )
        }

        val hasPin = !securityPin.isNullOrEmpty()

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_security_biometric_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = isBiometricEnabled,
                onCheckedChange = onBiometricEnabledChange,
                enabled = hasPin
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_security_pin_timeout), modifier = Modifier.weight(1f))
            var textValue by remember(securityPinTimeoutMinutes) { mutableStateOf(securityPinTimeoutMinutes.toString()) }
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    it.toLongOrNull()?.let { minutes -> onSecurityPinTimeoutChange(minutes) }
                },
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_security_pin_required_for_deletion), modifier = Modifier.weight(1f))
            Switch(
                checked = isPinRequiredForDeletion,
                onCheckedChange = onPinRequiredForDeletionChange,
                enabled = hasPin
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_security_require_for_edit), modifier = Modifier.weight(1f))
            Switch(
                checked = isSecurityRequiredForEdit,
                onCheckedChange = onSecurityRequiredForEditChange,
                enabled = hasPin
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_security_require_for_settings), modifier = Modifier.weight(1f))
            Switch(
                checked = isSecurityRequiredForSettings,
                onCheckedChange = onSecurityRequiredForSettingsChange,
                enabled = hasPin
            )
        }

        if (hasPin) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLockClicked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_security_lock_now))
            }
        }
    }
}
