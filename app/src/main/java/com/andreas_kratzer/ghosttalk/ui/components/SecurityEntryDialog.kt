package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager

@Composable
fun SecurityEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    securityManager: SecurityManager,
    title: String = stringResource(R.string.security_pin_title),
    isBiometricEnabled: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (isBiometricEnabled && context is FragmentActivity) {
            securityManager.authenticateBiometric(context) { success ->
                if (success) {
                    onConfirm(true)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        if (it.length <= 8) {
                            pin = it
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.settings_security_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (isBiometricEnabled) {
                    TextButton(
                        onClick = {
                            if (context is FragmentActivity) {
                                securityManager.authenticateBiometric(context) { success ->
                                    if (success) onConfirm(true)
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.security_use_biometric))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (securityManager.unlock(pin)) {
                        onConfirm(true)
                    } else {
                        errorMessage = "Falscher PIN"
                    }
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
