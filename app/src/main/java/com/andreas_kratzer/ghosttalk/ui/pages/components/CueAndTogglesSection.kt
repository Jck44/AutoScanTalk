package com.andreas_kratzer.ghosttalk.ui.pages.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.*
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard

@Composable
fun CueAndTogglesSection(
    state: ButtonConfigDialogState,
    featureGuard: FeatureGuard?,
    auditoryCueTextPlayback: TtsFieldPlaybackState,
    playingField: String?,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    onAutoSave: () -> Unit
) {
    SettingsEditTextItem(
        label = stringResource(R.string.button_auditory_cue_field),
        value = state.auditoryCueText,
        onValueChange = { state.auditoryCueText = it },
        onFocusLost = {
            auditoryCueTextPlayback.handleFocusLost(state.auditoryCueText, onAutoSave)
        },
        isPlaying = playingField == "auditoryCueText",
        isLoading = auditoryCueTextPlayback.isPrefetching,
        playPauseIconTint = if (auditoryCueTextPlayback.isCached) MaterialTheme.colorScheme.primary else null,
        onPlayPauseClick = onPlayTts?.let {
            { auditoryCueTextPlayback.handlePlayClick(state.auditoryCueText) }
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LocalDimensions.current.paddingSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        state.isActive = !state.isActive
                        onAutoSave()
                    }
                    .padding(vertical = 4.dp)
            ) {
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { 
                        state.isActive = it
                        onAutoSave()
                    },
                    thumbContent = if (state.isActive) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
                Text(
                    text = stringResource(R.string.button_is_active_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            VerticalDivider(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        state.playActionAsAuditoryCue = !state.playActionAsAuditoryCue
                        onAutoSave()
                    }
                    .padding(vertical = 4.dp)
            ) {
                Switch(
                    checked = state.playActionAsAuditoryCue,
                    onCheckedChange = { 
                        state.playActionAsAuditoryCue = it
                        onAutoSave()
                    }
                )
                Text(
                    text = stringResource(R.string.button_play_as_cue_short),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    featureGuard?.let { guard ->
        val currentAction = state.buildAction()
        val isActionEnabled = guard.isActionEnabled(currentAction)
        if (!isActionEnabled) {
            val featureName = when (currentAction) {
                is GeminiButtonAction, is GeminiSearchButtonAction -> "Gemini Cloud"
                is SmartHomeButtonAction -> "Smart Home"
                is SmartPredictionButtonAction, is FrequentActionButtonAction -> "Smart Prediction"
                is WeatherButtonAction -> "Wetter"
                is ControlDeviceButtonAction -> {
                    if (currentAction.actionType == DeviceActionType.READ_NOTIFICATIONS) "Benachrichtigungen" else ""
                }
                else -> ""
            }
            if (featureName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.feature_disabled_warning, featureName),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
