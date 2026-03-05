package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R

@Composable
fun ControlButtons(
    onStartScanning: () -> Unit,
    onActivateFocused: () -> Unit,
    onStopScanning: () -> Unit,
    isFocused: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onStartScanning) {
            Text(stringResource(R.string.page_action_start_scan))
        }
        Button(
            onClick = onActivateFocused,
            enabled = isFocused
        ) {
            Text(stringResource(R.string.page_action_activate_focused))
        }
        Button(onClick = onStopScanning) {
            Text(stringResource(R.string.page_action_stop_scan))
        }
    }
}
