package com.andreas_kratzer.ghosttalk.ui.pages.pagesplit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog zur Abfrage der Datenschutz-Zustimmung.
 */
@Composable
fun PageSplitOptInDialog(
    onConfirmCloud: (rememberDecision: Boolean) -> Unit,
    onConfirmManual: () -> Unit,
    onDismiss: () -> Unit
) {
    var rememberDecision by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seiten-Kategorisierung (Datenschutz)", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Um diese Seite automatisch aufzuteilen, können wir die Bezeichnungen der Tasten anonymisiert an die Google Cloud API senden. Es werden dabei keinerlei persönliche Daten oder Benutzer-IDs übertragen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Tipp: Unter Einstellungen -> KI kannst du einen eigenen Gemini API Key hinterlegen, um sicherzustellen, dass die Daten ausschließlich in deinem eigenen Google Cloud Projekt verarbeitet werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberDecision,
                        onCheckedChange = { rememberDecision = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Entscheidung merken (Opt-In speichern)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCloud(rememberDecision) }
            ) {
                Text("Cloud API nutzen")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = onConfirmManual
                ) {
                    Text("Manuell (Copy/Paste)")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}
