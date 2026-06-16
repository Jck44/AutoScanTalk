package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.GridItem

@Composable
fun GridEditorHeader(
    item: GridItem,
    isEditPreviewActive: Boolean,
    isMultiSelectMode: Boolean,
    onSummaryClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (isMultiSelectMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.bulk_action_mode_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else if (isEditPreviewActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.page_editor_preview_active_banner),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            GridEditorSummaryBar(item = item, onClick = onSummaryClick)
        }
    }
}
