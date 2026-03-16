package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.PinEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecuritySettingsSection(
    securityPin: String?,
    onSecurityPinChange: (String) -> Unit,
    onClearSecurityPin: () -> Unit,
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
    onLockClicked: () -> Unit,
    isPinRequired: Boolean,
    onPinRequiredChange: (Boolean) -> Unit,
    securityManager: SecurityManager,
    isBiometricSupported: Boolean
) {
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val hasPin = !securityPin.isNullOrEmpty()
    val dimensions = LocalDimensions.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        PreferenceCategory(stringResource(R.string.settings_category_security), modifier = Modifier.weight(1f)) {
            SettingsToggleItem(
                label = stringResource(R.string.settings_security_require_pin),
                checked = isPinRequired,
                onCheckedChange = { 
                    if (!it) {
                        if (hasPin) {
                            showConfirmClearDialog = true
                        } else {
                            onClearSecurityPin()
                        }
                    } else {
                        showSetPinDialog = true
                    }
                }
            )

            if (isPinRequired) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsClickableItem(
                            label = stringResource(R.string.settings_security_pin_label),
                            value = if (securityPin.isNullOrEmpty())
                                stringResource(R.string.settings_security_pin_inactive)
                            else
                                "**** (${stringResource(R.string.settings_security_pin_active)})",
                            onClick = { showSetPinDialog = true }
                        )
                    }
                    if (hasPin) {
                        TextButton(
                            onClick = { showConfirmClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp).width(18.dp))
                            Text(stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_delete))
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
                ) {
                    Text(
                        text = stringResource(R.string.settings_security_pin_timeout),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    var localTimeout by remember(securityPinTimeoutMinutes) { mutableStateOf(securityPinTimeoutMinutes.toString()) }

                    LaunchedEffect(localTimeout) {
                        if (localTimeout != securityPinTimeoutMinutes.toString()) {
                            delay(500)
                            localTimeout.toLongOrNull()?.let { minutes -> onSecurityPinTimeoutChange(minutes) }
                        }
                    }

                    OutlinedTextField(
                        value = localTimeout,
                        onValueChange = { localTimeout = it },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )
                }

                SettingsToggleItem(
                    label = if (isBiometricSupported) 
                        stringResource(R.string.settings_security_biometric_enabled)
                    else 
                        stringResource(R.string.settings_security_biometric_enabled) + " (" + stringResource(R.string.settings_security_biometric_unsupported) + ")",
                    checked = isBiometricEnabled,
                    onCheckedChange = onBiometricEnabledChange,
                    enabled = hasPin && isBiometricSupported
                )

                SettingsToggleItem(
                    label = stringResource(R.string.settings_security_pin_required_for_deletion),
                    checked = isPinRequiredForDeletion,
                    onCheckedChange = onPinRequiredForDeletionChange,
                    enabled = hasPin
                )

                SettingsToggleItem(
                    label = stringResource(R.string.settings_security_require_for_edit),
                    checked = isSecurityRequiredForEdit,
                    onCheckedChange = onSecurityRequiredForEditChange,
                    enabled = hasPin
                )

                SettingsToggleItem(
                    label = stringResource(R.string.settings_security_require_for_settings),
                    checked = isSecurityRequiredForSettings,
                    onCheckedChange = onSecurityRequiredForSettingsChange,
                    enabled = hasPin
                )

                if (hasPin) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingMedium)) {
                        Button(
                            onClick = onLockClicked,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_security_lock_now))
                        }
                    }
                }
            }
        }
    }

    if (showSetPinDialog) {
        PinEntryDialog(
            title = stringResource(R.string.settings_security_set_pin_title),
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin: String ->
                onSecurityPinChange(pin)
                showSetPinDialog = false
            }
        )
    }

    if (showConfirmClearDialog) {
        SecurityEntryDialog(
            onDismiss = { showConfirmClearDialog = false },
            onConfirm = { success: Boolean ->
                if (success) {
                    onClearSecurityPin()
                    showConfirmClearDialog = false
                }
            },
            securityManager = securityManager,
            isBiometricEnabled = isBiometricEnabled
        )
    }
}
