package com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.andreas_kratzer.ghosttalk.core.tts.CachedAudioItem
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog

@Composable
fun AudioCacheDialog(
    cacheItems: List<CachedAudioItem>,
    onDismiss: () -> Unit,
    onDelete: (CachedAudioItem) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val groupedItems = remember(cacheItems) {
        cacheItems.groupBy { it.text }
    }

    val showClearConfirmationState = remember { mutableStateOf(false) }

    GhostTalkDialog(
        title = "Gecachte Audios",
        confirmText = "Schließen",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        neutralButton = {
            if (cacheItems.isNotEmpty()) {
                TextButton(onClick = { showClearConfirmationState.value = true }) {
                    Text("Alle löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = {
            if (cacheItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine gecachten Audios vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupedItems.entries.toList(), key = { it.key }) { entry ->
                        val word = entry.key
                        val items = entry.value

                        if (items.size == 1) {
                            SingleCacheItemRow(
                                word = word,
                                item = items.first(),
                                onPlay = {
                                    try {
                                        MediaPlayer.create(context, Uri.fromFile(it.file))?.apply {
                                            setOnCompletionListener { mp -> mp.release() }
                                            start()
                                        }
                                    } catch (_: Exception) { /* ignore */ }
                                },
                                onDelete = { onDelete(it) }
                            )
                        } else {
                            ExpandableCacheItemGroup(
                                word = word,
                                items = items,
                                onPlay = {
                                    try {
                                        MediaPlayer.create(context, Uri.fromFile(it.file))?.apply {
                                            setOnCompletionListener { mp -> mp.release() }
                                            start()
                                        }
                                    } catch (_: Exception) { /* ignore */ }
                                },
                                onDelete = { onDelete(it) }
                            )
                        }
                    }
                }
            }
        }
    )

    if (showClearConfirmationState.value) {
        GhostTalkDialog(
            title = "Gesamten Cache löschen?",
            confirmText = "Ja, löschen",
            dismissText = "Abbrechen",
            isDestructive = true,
            onConfirm = {
                onClearAll()
                showClearConfirmationState.value = false
            },
            onDismiss = { showClearConfirmationState.value = false },
            content = {
                Text("Es werden alle ${cacheItems.size} Audio-Dateien vom Gerät gelöscht. Dies verursacht bei erneuter Verwendung erneute API-Kosten.")
            }
        )
    }
}

@Composable
fun SingleCacheItemRow(
    word: String,
    item: CachedAudioItem,
    onPlay: (CachedAudioItem) -> Unit,
    onDelete: (CachedAudioItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = word, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${item.voiceId} • ${item.modelId.replace("eleven_", "")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onPlay(item) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Abspielen")
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ExpandableCacheItemGroup(
    word: String,
    items: List<CachedAudioItem>,
    onPlay: (CachedAudioItem) -> Unit,
    onDelete: (CachedAudioItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${items.size} Varianten",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.voiceId,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = item.modelId.replace("eleven_", ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onPlay(item) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Abspielen", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
