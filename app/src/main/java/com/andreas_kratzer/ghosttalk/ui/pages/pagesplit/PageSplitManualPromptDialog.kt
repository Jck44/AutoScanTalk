package com.andreas_kratzer.ghosttalk.ui.pages.pagesplit

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Dialog für das manuelle Kopieren des Prompts und Einfügen der KI-Antwort.
 */
@Composable
fun PageSplitManualPromptDialog(
    promptText: String,
    onEvaluateResponse: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var pastedJson by remember { mutableStateOf("") }
    val parseError = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manuelle KI-Kategorisierung", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Kopiere den generierten Prompt und füge ihn in eine KI deiner Wahl (z.B. ChatGPT, Gemini Web) ein. Kopiere die Antwort der KI und füge sie unten ein.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Prompt Text Display (Read-Only)
                OutlinedTextField(
                    value = promptText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("1. Prompt kopieren") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Prompt", promptText)))
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Prompt kopieren")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input for LLM JSON Response
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { 
                        pastedJson = it 
                        parseError.value = null
                    },
                    label = { Text("2. KI-Antwort (JSON) einfügen") },
                    placeholder = { Text("Füge hier das von der KI generierte JSON-Objekt ein...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 10,
                    isError = parseError.value != null
                )
                if (parseError.value != null) {
                    Text(
                        text = parseError.value ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        onEvaluateResponse(pastedJson)
                    } catch (e: Exception) {
                        parseError.value = "Ungültiges JSON-Format. Bitte stelle sicher, dass die Struktur genau dem Prompt entspricht."
                    }
                },
                enabled = pastedJson.isNotBlank()
            ) {
                Text("Antwort auswerten")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
