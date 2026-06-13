package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.ui.components.UsageLocationRow

@Composable
fun ActivatePageDialog(
    pageName: String,
    usages: List<UsageLocation>,
    onDismiss: () -> Unit,
    onConfirm: (List<UsageLocation>) -> Unit
) {
    var selectedUsages by remember { mutableStateOf(usages.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_activate_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.page_activate_message, pageName),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { selectedUsages = usages.toSet() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.page_activate_select_all))
                    }
                    TextButton(
                        onClick = { selectedUsages = emptySet() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.page_activate_deselect_all))
                    }
                }

                HorizontalDivider()

                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                ) {
                    Column {
                        usages.forEach { usage ->
                            UsageLocationRow(
                                usage = usage,
                                leadingContent = {
                                    Checkbox(
                                        checked = selectedUsages.contains(usage),
                                        onCheckedChange = { isChecked ->
                                            selectedUsages = if (isChecked) {
                                                selectedUsages + usage
                                            } else {
                                                selectedUsages - usage
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedUsages.toList()) },
                enabled = selectedUsages.isNotEmpty()
            ) {
                Text(stringResource(R.string.action_page_activate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
