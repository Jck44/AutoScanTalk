package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.R

@Composable
fun VolumeActionFields(
    volumeValue: String?,
    onVolumeValueChange: (String) -> Unit,
    onAutoSave: () -> Unit
) {
    OutlinedTextField(
        value = volumeValue ?: "50",
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onVolumeValueChange(newValue)
            }
        },
        label = { Text(stringResource(R.string.volume_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().onFocusChanged { 
            if (!it.isFocused) onAutoSave()
        }
    )
}
