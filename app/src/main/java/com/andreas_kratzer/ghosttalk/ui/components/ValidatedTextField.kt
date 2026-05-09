package com.andreas_kratzer.ghosttalk.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape

@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    errorMessage: String? = null,
    onFocusLost: ((String) -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val isError = isRequired && value.isBlank()

    // Block physical back navigation if this field is required and currently empty
    BackHandler(enabled = isError) {
        // Do nothing, just block the back navigation
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        placeholder = placeholder,
        singleLine = singleLine,
        shape = shape,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    onFocusLost?.invoke(value)
                }
            }
    )
}
