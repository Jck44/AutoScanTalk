package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation

@Composable
fun UsageLocationRow(
    usage: UsageLocation,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (leadingContent != null) 8.dp else 0.dp)
        ) {
            val typePrefix = if (usage is UsageLocation.PageUsage) {
                stringResource(R.string.common_page)
            } else {
                stringResource(R.string.common_template)
            }
            Text(
                text = stringResource(R.string.page_dialog_usage_item, typePrefix, usage.name),
                style = MaterialTheme.typography.bodyMedium
            )
            if (usage.buttonLabel.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.page_dialog_usage_button_label, usage.buttonLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (trailingContent != null) {
            trailingContent()
        }
    }
}
