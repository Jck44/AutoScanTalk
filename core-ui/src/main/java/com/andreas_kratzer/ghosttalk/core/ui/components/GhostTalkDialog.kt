package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun GhostTalkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    dismissText: String? = null,          // null = kein Abbrechen-Button
    isDestructive: Boolean = false,       // färbt Confirm-Button error
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column { content() } },
        confirmButton = {
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
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        },
        shape = RoundedCornerShape(LocalDimensions.current.dialogCornerRadius),
        modifier = modifier
    )
}
