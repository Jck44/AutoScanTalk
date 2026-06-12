package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

@Composable
fun LabelFieldSection(
    state: ButtonConfigDialogState,
    context: Context,
    pages: List<Page>,
    onSuggestLabel: ((ButtonConfig, onResult: (String) -> Unit) -> Unit)?,
    labelPlayback: TtsFieldPlaybackState,
    playingField: String?,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    onAutoSave: () -> Unit
) {
    var isSuggestingLabel by remember { mutableStateOf(false) }

    SettingsEditTextItem(
        label = stringResource(R.string.button_label_field),
        value = state.label,
        onValueChange = { state.label = it },
        onFocusLost = {
            labelPlayback.handleFocusLost(state.label, onAutoSave)
        },
        isPlaying = playingField == "label",
        isLoading = labelPlayback.isPrefetching,
        playPauseIconTint = if (labelPlayback.isCached) MaterialTheme.colorScheme.primary else null,
        onPlayPauseClick = onPlayTts?.let {
            { labelPlayback.handlePlayClick(state.label) }
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSuggestingLabel) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.generating_suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            val hasAi = onSuggestLabel != null
            TextButton(
                onClick = {
                    val currentConfig = state.buildConfig()
                    if (onSuggestLabel != null) {
                        isSuggestingLabel = true
                        onSuggestLabel(currentConfig) { suggestion ->
                            isSuggestingLabel = false
                            if (suggestion.isNotBlank()) {
                                state.label = suggestion
                                labelPlayback.handleFocusLost(suggestion, onAutoSave)
                            } else {
                                val localSuggest = getLocalLabelSuggestion(currentConfig, pages, context)
                                if (localSuggest.isNotBlank()) {
                                    state.label = localSuggest
                                    labelPlayback.handleFocusLost(localSuggest, onAutoSave)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_label_suggestion_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        val localSuggest = getLocalLabelSuggestion(currentConfig, pages, context)
                        if (localSuggest.isNotBlank()) {
                            state.label = localSuggest
                            labelPlayback.handleFocusLost(localSuggest, onAutoSave)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_label_suggestion_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = GhostTalkIcons.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(if (hasAi) R.string.ki_suggestion else R.string.local_suggestion_action),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
