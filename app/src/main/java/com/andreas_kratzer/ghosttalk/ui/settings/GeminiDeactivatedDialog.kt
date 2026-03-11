package com.andreas_kratzer.ghosttalk.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R

@Composable
fun GeminiDeactivatedDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_gemini_nano_deactivated_title))
        },
        text = {
            Text(text = stringResource(R.string.settings_gemini_nano_deactivated_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK")
            }
        }
    )
}
