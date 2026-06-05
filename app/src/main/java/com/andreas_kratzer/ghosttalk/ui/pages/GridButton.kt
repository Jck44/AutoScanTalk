@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.ui.pages
 
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MarkAccidentalButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.theme.ControlDeviceBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.ControlDeviceBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.ControlDeviceBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.ControlDeviceBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrequentActionBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrequentActionBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrequentActionBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrequentActionBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GeminiBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.GeminiBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GeminiBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.GeminiBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.PlayMediaBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.PlayMediaBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.PlayMediaBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.PlayMediaBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.SmartHomeBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.SmartHomeBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.SmartHomeBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.SmartHomeBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.WeatherBadgeBgDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.WeatherBadgeBgLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.WeatherBadgeTextDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.WeatherBadgeTextLight

object GridButtonColors {
    fun getBadgeColors(action: ButtonAction, isDark: Boolean): Pair<Color, Color> {
        return when (action) {
            is SpeakTextButtonAction -> {
                if (isDark) SpeakTextBadgeBgDark to SpeakTextBadgeTextDark
                else SpeakTextBadgeBgLight to SpeakTextBadgeTextLight
            }
            is NavigateToPageButtonAction, is NavigateBackButtonAction, is NavigateToStartPageButtonAction -> {
                if (isDark) NavigateBadgeBgDark to NavigateBadgeTextDark
                else NavigateBadgeBgLight to NavigateBadgeTextLight
            }
            is SmartHomeButtonAction -> {
                if (isDark) SmartHomeBadgeBgDark to SmartHomeBadgeTextDark
                else SmartHomeBadgeBgLight to SmartHomeBadgeTextLight
            }
            is GeminiButtonAction, is GeminiSearchButtonAction, is GeminiNanoButtonAction, is GeminiVisionButtonAction -> {
                if (isDark) GeminiBadgeBgDark to GeminiBadgeTextDark
                else GeminiBadgeBgLight to GeminiBadgeTextLight
            }
            is ControlDeviceButtonAction -> {
                if (isDark) ControlDeviceBadgeBgDark to ControlDeviceBadgeTextDark
                else ControlDeviceBadgeBgLight to ControlDeviceBadgeTextLight
            }
            is WeatherButtonAction -> {
                if (isDark) WeatherBadgeBgDark to WeatherBadgeTextDark
                else WeatherBadgeBgLight to WeatherBadgeTextLight
            }
            is FrequentActionButtonAction, is SmartPredictionButtonAction, is PreviousActionButtonAction, is MarkAccidentalButtonAction -> {
                if (isDark) FrequentActionBadgeBgDark to FrequentActionBadgeTextDark
                else FrequentActionBadgeBgLight to FrequentActionBadgeTextLight
            }
            is PlayMediaButtonAction -> {
                if (isDark) PlayMediaBadgeBgDark to PlayMediaBadgeTextDark
                else PlayMediaBadgeBgLight to PlayMediaBadgeTextLight
            }
        }
    }
}
 
@Composable
fun GridButton(
    buttonConfig: ButtonConfig?,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isRowFocused: Boolean = false,
    isEditorMode: Boolean = !LocalIsUserModeActive.current,
    overrideLabel: String? = null,
    targetPageName: String? = null,
    heatmapIntensity: Float? = null,
    effortMetrics: com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics? = null,
    onClick: () -> Unit
) {
    val dimensions = LocalDimensions.current
    val isActive = buttonConfig?.isActive ?: true
    val stateTag = if (isFocused) "button_focused" else if (isRowFocused) "row_focused" else "button_idle"
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    Box(
        modifier = modifier
            .testTag(buttonConfig?.id ?: "")
    ) {
        Card(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .alpha(if (isEditorMode && !isActive) 0.5f else 1f)
                .testTag(stateTag),
            shape = MaterialTheme.shapes.small,
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
            Box(modifier = Modifier.fillMaxSize()) {
                if (buttonConfig != null) {
                    if (isEditorMode) {
                        // Editor mode: Column layout with badge on top, label centered below
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Action badge at top
                            // Centralized icon lookup via ActionCategoryRegistry
                            val category = ActionCategoryRegistry.getCategoryForAction(buttonConfig.buttonAction)
                            val actionIcon = GhostTalkIcons.getIconForCategory(category)

                            val actionBadgeText = when (buttonConfig.buttonAction) {
                                is NavigateToPageButtonAction -> targetPageName
                                is NavigateToStartPageButtonAction -> stringResource(R.string.button_action_navigate_to_start_page)
                                else -> null
                            }
                            
                            val isDark = isSystemInDarkTheme()
                            val (badgeBgColor, badgeTxtColor) = GridButtonColors.getBadgeColors(buttonConfig.buttonAction, isDark)
                            
                            Surface(
                                color = badgeBgColor,
                                contentColor = badgeTxtColor,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(start = 8.dp, top = 8.dp)
                                    .padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = actionIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    
                                    if (actionBadgeText != null) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = actionBadgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            
                            // Label centered in remaining space
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = overrideLabel ?: buttonConfig.label,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = dimensions.buttonFontSize,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = dimensions.buttonFontSize,
                                        lineHeight = dimensions.buttonFontSize * 1.1f,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }
                        }
                    } else {
                        // User mode: just centered label, no badge
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(dimensions.paddingMedium),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = overrideLabel ?: buttonConfig.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = dimensions.buttonFontSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = dimensions.buttonFontSize,
                                    lineHeight = dimensions.buttonFontSize * 1.1f,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }

                    // --- Visual Analytics Overlays (Heatmap & F-Index) ---
                    // Heatmap Overlay
                    if (heatmapIntensity != null && heatmapIntensity > 0f) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = heatmapIntensity * 0.35f),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }

                    // Access Effort Badge (F-Index based color code)
                    if (effortMetrics != null) {
                        val badgeColor = when {
                            effortMetrics.frustrationIndex < 0.3f -> Color(0xFF78909C) // Grey / Neutral
                            effortMetrics.frustrationIndex < 0.7f -> Color(0xFFFBC02D) // Yellow / Warning
                            else -> Color(0xFFD32F2F) // Red / Critical Frustration
                        }
                        Surface(
                            color = badgeColor,
                            contentColor = Color.White,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                        ) {
                            Text(
                                text = "~${effortMetrics.shortestPathTimeSec}s",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp, 
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else if (isEditorMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimensions.paddingMedium),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(dimensions.iconSizeLarge),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
