package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun GhostTalkDialog(
    title: String = "",
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    titleContent: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    dismissText: String? = null,          // null = kein Abbrechen-Button
    isDestructive: Boolean = false,       // färbt Confirm-Button error
    confirmEnabled: Boolean = true,
    neutralButton: @Composable (() -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = properties,
        icon = icon,
        title = titleContent ?: { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column { content() } },
        confirmButton = {
            if (confirmText.isNotEmpty()) {
                if (isDestructive) {
                    TextButton(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(confirmText) }
                } else {
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
                }
            }
        },
        dismissButton = if (dismissText != null || neutralButton != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    neutralButton?.invoke()
                    if (neutralButton != null && dismissText != null) {
                        Spacer(modifier = Modifier.width(LocalDimensions.current.paddingMedium))
                    }
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss) { Text(dismissText) }
                    }
                }
            }
        } else null,
        shape = RoundedCornerShape(LocalDimensions.current.dialogCornerRadius),
        modifier = modifier
    )
}
