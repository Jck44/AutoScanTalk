package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

@Composable
fun EditablePageTitle(
    pageName: String,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "page_editor_name_field"
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempName by remember(pageName) { mutableStateOf(pageName) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable { showDialog = true }
            .padding(vertical = 4.dp)
            .testTag("editable_page_title_row")
    ) {
        Text(
            text = pageName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = GhostTalkIcons.Edit,
            contentDescription = "Umbenennen",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        GhostTalkDialog(
            title = stringResource(R.string.page_dialog_rename_title),
            onDismiss = { showDialog = false },
            onConfirm = {
                if (tempName.isNotBlank()) {
                    onRename(tempName)
                    showDialog = false
                }
            },
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.action_cancel)
        ) {
            ValidatedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                isRequired = true,
                errorMessage = stringResource(R.string.error_page_name_required),
                placeholder = { Text(stringResource(R.string.page_name_label)) },
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag(testTag)
            )
        }
    }
}
