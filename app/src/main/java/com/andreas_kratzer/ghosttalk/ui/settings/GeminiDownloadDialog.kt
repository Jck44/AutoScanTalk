package com.andreas_kratzer.ghosttalk.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@Composable
fun GeminiDownloadDialog(
    isVisible: Boolean,
    progress: Float,
    statusMessage: String,
    isDownloading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val dimensions = LocalDimensions.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Gemini Nano Modell")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = onConfirm) {
                    Text("Herunterladen")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Im Hintergrund fortsetzen")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
