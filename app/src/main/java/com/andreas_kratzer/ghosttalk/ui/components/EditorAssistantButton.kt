package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

@Composable
fun EditorAssistantButton(
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    testTag: String = "page_editor_split_wizard_trigger_menu"
) {
    val description = "Layout- & Struktur-Assistent"
    if (compact) {
        IconButton(
            onClick = onClick,
            modifier = modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = GhostTalkIcons.AutoAwesome,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = GhostTalkIcons.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Assistent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
