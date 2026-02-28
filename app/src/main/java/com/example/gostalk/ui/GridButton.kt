package com.example.gostalk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gostalk.model.ButtonConfig

@Composable
fun GridButton(
    buttonConfig: ButtonConfig?,
    isFocused: Boolean = false,
    isEditorMode: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (buttonConfig != null) 4.dp else 0.dp),
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else if (isEditorMode) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (buttonConfig != null) {
                if (isEditorMode) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant
            } else {
                if (isEditorMode) MaterialTheme.colorScheme.surface 
                else MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            if (buttonConfig != null) {
                AutoSizeText(
                    text = buttonConfig.label,
                    color = if (isEditorMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isEditorMode) {
                Text(
                    text = "+",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
