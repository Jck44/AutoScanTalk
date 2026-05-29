package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CacheStatusRow(
    textToCache: String,
    isTextCached: ((String) -> Boolean)?,
    onPrefetchText: ((String, () -> Unit) -> Unit)?
) {
    if (textToCache.isBlank()) return

    var isCached by remember(textToCache, isTextCached) { 
        mutableStateOf(isTextCached?.invoke(textToCache) ?: false) 
    }
    var isPrefetching by remember(textToCache) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isCached) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (isCached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
            Text(
                text = if (isCached) "Im Cache (Offline verfügbar)" else "Nicht im Cache (Benötigt Internet)",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        
        if (!isCached && onPrefetchText != null) {
            if (isPrefetching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                TextButton(
                    onClick = {
                        isPrefetching = true
                        onPrefetchText(textToCache) {
                            isPrefetching = false
                            isCached = isTextCached?.invoke(textToCache) ?: false
                        }
                    }
                ) {
                    Text("Jetzt cachen")
                }
            }
        }
    }
}
