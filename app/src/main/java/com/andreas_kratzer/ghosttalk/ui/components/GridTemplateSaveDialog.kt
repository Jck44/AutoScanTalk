package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun GridTemplateSaveDialog(
    templateName: String,
    onTemplateNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalDimensions.current

    com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
        title = stringResource(R.string.template_save_as_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_save),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.action_cancel)
    ) {
        Column {
            Text(stringResource(R.string.template_enter_name_prompt))
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            OutlinedTextField(
                value = templateName,
                onValueChange = onTemplateNameChange,
                label = { Text(stringResource(R.string.template_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
