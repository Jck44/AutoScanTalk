package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.ui.R

@Composable
fun SecurityEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    securityManager: SecurityManager,
    title: String = stringResource(R.string.security_pin_title),
    isBiometricEnabled: Boolean = false
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val incorrectPinMessage = stringResource(R.string.security_pin_incorrect)

    val triggerBiometric = {
        if (context is FragmentActivity) {
            securityManager.authenticateBiometric(context) { success ->
                if (success) onConfirm(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            triggerBiometric()
        }
    }

    PinEntryDialog(
        onDismiss = onDismiss,
        onConfirm = { pinEntry ->
            if (securityManager.unlock(pinEntry)) {
                onConfirm(true)
            } else {
                errorMessage = incorrectPinMessage
            }
        },
        title = title,
        errorMessage = errorMessage,
        isBiometricEnabled = isBiometricEnabled,
        onBiometricClick = { triggerBiometric() }
    )
}
