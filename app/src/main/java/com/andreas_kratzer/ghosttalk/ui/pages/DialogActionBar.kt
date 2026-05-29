package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun DialogActionBar(
    buttonConfig: ButtonConfig,
    label: String,
    spokenText: String,
    spokenTextMode: SpokenTextMode,
    audioFileName: String?,
    buildCurrentAction: () -> ButtonAction,
    onDismiss: () -> Unit,
    onTest: (ButtonConfig) -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSaveAsTemplate: ((ButtonConfig) -> Unit)? = null,
    context: Context = androidx.compose.ui.platform.LocalContext.current
) {
    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val showTest = availableWidth > 420.dp
        val showMove = availableWidth > 550.dp
        val showDuplicate = availableWidth > 680.dp
        val showDelete = availableWidth > 810.dp

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            // Always show Close
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = 96.dp)
            ) {
                Text(stringResource(CoreR.string.dialog_close))
            }

            // Test Button
            if (showTest) {
                OutlinedButton(
                    onClick = {
                        val currentAction = buildCurrentAction()
                        onTest(buttonConfig.copy(
                            label = label,
                            spokenText = if (spokenText.isNotBlank()) spokenText else null,
                            spokenTextMode = spokenTextMode,
                            audioFileName = audioFileName,
                            buttonAction = currentAction
                        ))
                        Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.button_action_test))
                }
            }

            // Move Button
            if (showMove) {
                OutlinedButton(
                    onClick = onMove,
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Text(stringResource(R.string.button_action_move))
                }
            }

            // Duplicate Button
            if (showDuplicate) {
                OutlinedButton(
                    onClick = onDuplicate,
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.action_duplicate))
                }
            }

            // Delete Button
            if (showDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(CoreR.string.action_delete))
                }
            }

            // Overflow Menu
            val hasHiddenItems = !showTest || !showMove || !showDuplicate || !showDelete
            if (hasHiddenItems) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onSaveAsTemplate != null) {
                            DropdownMenuItem(
                                text = { Text("Als Vorlage speichern") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val currentAction = buildCurrentAction()
                                    onSaveAsTemplate(buttonConfig.copy(
                                        label = label,
                                        spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                        spokenTextMode = spokenTextMode,
                                        audioFileName = audioFileName,
                                        buttonAction = currentAction
                                    ))
                                }
                            )
                        }
                        if (!showTest) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.button_action_test)) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val currentAction = buildCurrentAction()
                                    onTest(buttonConfig.copy(
                                        label = label,
                                        spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                        spokenTextMode = spokenTextMode,
                                        audioFileName = audioFileName,
                                        buttonAction = currentAction
                                    ))
                                    Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (!showMove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.button_action_move)) },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                }
                            )
                        }
                        if (!showDuplicate) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDuplicate()
                                }
                            )
                        }
                        if (!showDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(CoreR.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
