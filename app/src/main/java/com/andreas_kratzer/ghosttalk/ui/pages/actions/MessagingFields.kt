package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun MessagingFields(
    contactName: String,
    contactPhone: String = "",
    onContactSelected: (String, String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onAutoSave: () -> Unit = {},
    showTextField: Boolean = true
) {
    val dimensions = LocalDimensions.current
    val context = LocalContext.current

    val contactLauncher = rememberContactPickerLauncher(onContactSelected = onContactSelected)

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Proactive check
    fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    LaunchedEffect(Unit) {
        checkSmsPermission()
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        // Contact Selection
        val displayValue = if (contactPhone.isNotBlank()) {
            "$contactName ($contactPhone)"
        } else {
            contactName
        }

        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.contact_picker_title)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { 
                        checkSmsPermission()
                        contactLauncher.launch(null)
                    }
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Edit,
                        contentDescription = stringResource(R.string.contact_picker_title)
                    )
                }
            }
        )

        // Message Text (Filtering Emojis)
        if (showTextField) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { newValue ->
                    // Filter out Emojis / Surrogate pairs / Non-BMP characters
                    val filtered = newValue.filter { char ->
                        char.code <= 0xFFFF && !char.isSurrogate()
                    }
                    onMessageTextChange(filtered)
                },
                label = { Text(stringResource(R.string.message_emojis_not_supported)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (!it.isFocused) onAutoSave()
                },
                supportingText = {
                    Text(stringResource(R.string.message_emojis_hint))
                }
            )
        }
    }
}
