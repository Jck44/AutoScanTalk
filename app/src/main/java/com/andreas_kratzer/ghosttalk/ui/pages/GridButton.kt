package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive

@Composable
fun GridButton(
    buttonConfig: ButtonConfig?,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isRowFocused: Boolean = false,
    isEditorMode: Boolean = !LocalIsUserModeActive.current,
    overrideLabel: String? = null,
    onClick: () -> Unit
) {
    val dimensions = LocalDimensions.current
    val isActive = buttonConfig?.isActive ?: true
    val stateTag = if (isFocused) "button_focused" else if (isRowFocused) "row_focused" else "button_idle"
    
    Box(
        modifier = modifier
            .testTag(buttonConfig?.id ?: "")
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isEditorMode && !isActive) 0.5f else 1f)
                .testTag(stateTag)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (buttonConfig != null) dimensions.cardElevation else 0.dp
            ),
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
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensions.paddingMedium)
            ) {
                if (buttonConfig != null) {
                    if (isEditorMode) {
                        // 1. Text Badge at top-left
                        val actionBadgeText = when (buttonConfig.buttonAction) {
                            is SpeakTextButtonAction -> "Sprechen"
                            is NavigateToPageButtonAction -> "Nav"
                            is SmartHomeButtonAction -> "Home"
                            is GeminiButtonAction, is GeminiSearchButtonAction, is GeminiNanoButtonAction, is GeminiVisionButtonAction -> "KI"
                            is ControlDeviceButtonAction -> "Gerät"
                            is WeatherButtonAction -> "Wetter"
                            is FrequentActionButtonAction, is SmartPredictionButtonAction, is PreviousActionButtonAction -> "Verlauf"
                        }
                        
                        if (actionBadgeText.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = actionBadgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                    }

                    // 3. Label Text centered
                    Text(
                        text = overrideLabel ?: buttonConfig.label,
                        color = if (isEditorMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = dimensions.buttonFontSize,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = dimensions.buttonFontSize,
                            lineHeight = dimensions.buttonFontSize * 1.1f,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                } else if (isEditorMode) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(dimensions.iconSizeLarge).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
