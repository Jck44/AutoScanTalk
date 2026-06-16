package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun PhoneNumberPickerDialog(
    title: String,
    phoneNumbers: List<PhoneNumberInfo>,
    onDismiss: () -> Unit,
    onNumberSelected: (PhoneNumberInfo) -> Unit
) {
    val dimensions = LocalDimensions.current

    GhostTalkDialog(
        title = stringResource(R.string.contact_picker_select_number_title),
        onDismiss = onDismiss,
        confirmText = stringResource(android.R.string.cancel),
        onConfirm = onDismiss
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
            )
            HorizontalDivider()
            LazyColumn {
                items(phoneNumbers) { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNumberSelected(info) }
                            .padding(vertical = dimensions.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(dimensions.paddingMedium))
                        Column {
                            Text(
                                text = info.number,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = info.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
