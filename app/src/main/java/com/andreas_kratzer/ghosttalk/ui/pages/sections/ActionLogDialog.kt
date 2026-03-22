package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase

@Composable
fun ActionLogDialog(
    lastActions: List<ActionLogEntry>,
    onDismiss: () -> Unit,
    actionLogUseCase: ActionLogUseCase
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.page_last_actions_title))
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                if (lastActions.isEmpty()) {
                    Text(text = stringResource(R.string.page_no_actions_yet))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(lastActions) { entry ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = actionLogUseCase.formatEntryForDisplay(entry),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    )
}
