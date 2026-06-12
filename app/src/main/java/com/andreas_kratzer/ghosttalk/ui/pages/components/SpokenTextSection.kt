package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import java.io.File
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun SpokenTextSection(
    state: ButtonConfigDialogState,
    context: Context,
    spokenTextPlayback: TtsFieldPlaybackState,
    playingField: String?,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    micPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: () -> Unit,
    onPlayRecording: (File) -> Unit,
    onAutoSave: () -> Unit
) {
    Text(
        text = stringResource(R.string.button_spoken_text_field),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LocalDimensions.current.paddingSmall),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(SpokenTextMode.TTS, SpokenTextMode.AUDIO)
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.spokenTextMode == mode,
                            onClick = { 
                                state.spokenTextMode = mode
                                onAutoSave()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            label = { 
                                Text(
                                    text = if (mode == SpokenTextMode.TTS) {
                                        stringResource(R.string.button_spoken_text_mode_tts)
                                    } else {
                                        stringResource(R.string.button_spoken_text_mode_audio)
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.spokenTextMode == SpokenTextMode.TTS) {
                    SettingsEditTextItem(
                        label = "",
                        placeholder = stringResource(R.string.button_spoken_text_placeholder),
                        value = state.spokenText,
                        onValueChange = { state.spokenText = it },
                        onFocusLost = {
                            spokenTextPlayback.handleFocusLost(state.spokenText, onAutoSave)
                        },
                        isPlaying = playingField == "spokenText",
                        isLoading = spokenTextPlayback.isPrefetching,
                        playPauseIconTint = if (spokenTextPlayback.isCached) MaterialTheme.colorScheme.primary else null,
                        onPlayPauseClick = onPlayTts?.let {
                            { spokenTextPlayback.handlePlayClick(state.spokenText) }
                        },
                        borderless = true
                    )
                } else {
                    val audioFileExists = remember(state.audioFileName) {
                        if (state.audioFileName.isNullOrBlank()) false
                        else File(context.filesDir.resolve("audio_recordings"), state.audioFileName!!).exists()
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by if (state.isRecording) {
                        infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )
                    } else {
                        remember { mutableFloatStateOf(1f) }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_recording),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (audioFileExists) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_saved),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_ready),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (state.isRecording) {
                                        onStopVoiceRecording()
                                    } else {
                                        val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasMicPermission) {
                                            onStartVoiceRecording()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (state.isRecording) GhostTalkIcons.Stop else GhostTalkIcons.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                )
                                Text(
                                    text = if (state.isRecording) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_record)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val file = File(context.filesDir.resolve("audio_recordings"), state.audioFileName ?: "")
                                    onPlayRecording(file)
                                },
                                enabled = audioFileExists && !state.isRecording,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (state.isPlayingAudio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (state.isPlayingAudio) GhostTalkIcons.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                )
                                Text(
                                    text = if (state.isPlayingAudio) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_play)
                                )
                            }

                            IconButton(
                                onClick = { state.showDeleteConfirmation = true },
                                enabled = audioFileExists && !state.isRecording,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.button_audio_delete)
                                )
                            }
                        }
                    }

                    if (state.showDeleteConfirmation) {
                        AlertDialog(
                            onDismissRequest = { state.showDeleteConfirmation = false },
                            title = { Text(stringResource(R.string.button_audio_delete)) },
                            text = { Text(stringResource(R.string.button_audio_delete_confirm)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        state.showDeleteConfirmation = false
                                        val file = File(context.filesDir.resolve("audio_recordings"), state.audioFileName ?: "")
                                        if (file.exists()) {
                                            file.delete()
                                        }
                                        state.audioFileName = null
                                        onAutoSave()
                                        Toast.makeText(context, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.button_audio_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { state.showDeleteConfirmation = false }) {
                                    Text(stringResource(CoreR.string.dialog_close))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
