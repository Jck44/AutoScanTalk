package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenAiSettingsSection(viewModel: SettingsViewModel) {
    val isEnabled by viewModel.isGeminiEnabled.collectAsState(false)
    val geminiApiKey by viewModel.geminiApiKey.collectAsState("")
    val useGeminiApiKey by viewModel.useGeminiApiKey.collectAsState(false)
    val userEmail by viewModel.userEmail.collectAsState()

    val smartEnabled by viewModel.isSmartPredictionEnabled.collectAsState(false)
    val isVerified by viewModel.isGeminiVerified.collectAsState(false)

    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    var clipboardKey by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current

    LaunchedEffect(Unit) {
        try {
            val clipEntry = clipboard.getClipEntry()
            val text = clipEntry?.clipData?.getItemAt(0)?.text?.toString()
            if (text != null && text.trim().matches(Regex("^AIzaSy[A-Za-z0-9_-]{33}$"))) {
                clipboardKey = text.trim()
            }
        } catch (_: Exception) {
            // Ignore clipboard errors
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = androidx.compose.ui.platform.LocalDensity.current
    val containerWidthDp = with(density) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isLargeScreen = containerWidthDp >= 720.dp
    val showSideBySide = isLargeScreen || isLandscape

    @Composable
    fun SmartPredictionsCategory(modifier: Modifier = Modifier) {
        PreferenceCategory(stringResource(R.string.settings_smart_predictions_title), modifier = modifier) {
            SettingsToggleItem(
                label = stringResource(R.string.settings_smart_prediction_enable),
                checked = smartEnabled,
                description = stringResource(R.string.settings_smart_prediction_hint),
                onCheckedChange = { viewModel.setSmartPredictionEnabled(it) }
            )
        }
    }

    @Composable
    fun GeminiCategory(modifier: Modifier = Modifier) {
        PreferenceCategory(stringResource(R.string.settings_category_gemini), modifier = modifier) {
            SettingsToggleItem(stringResource(R.string.settings_gemini_enable), isEnabled) { 
                viewModel.setGeminiEnabled(context, it) 
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_gemini_auth_method),
                    selectedOption = if (useGeminiApiKey) {
                        stringResource(R.string.settings_gemini_auth_apikey)
                    } else {
                        stringResource(R.string.settings_gemini_auth_oauth)
                    },
                    options = listOf(
                        stringResource(R.string.settings_gemini_auth_oauth) to {
                            viewModel.setUseGeminiApiKey(false)
                        },
                        stringResource(R.string.settings_gemini_auth_apikey) to {
                            viewModel.setUseGeminiApiKey(true)
                        }
                    )
                )
                
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                
                if (!useGeminiApiKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingMedium)
                    ) {
                        if (userEmail != null) {
                            Text(
                                text = stringResource(R.string.settings_cloud_signed_in_as, userEmail!!),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                            )
                            Button(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_cloud_sign_out))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.signIn(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_cloud_sign_in))
                            }
                        }
                    }
                } else {
                    SettingsEditTextItem(
                        label = stringResource(R.string.settings_gemini_api_key),
                        value = geminiApiKey ?: "",
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        isPassword = true
                    )
                    Text(
                        text = stringResource(R.string.settings_gemini_api_key_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
                    )

                    Spacer(modifier = Modifier.height(dimensions.paddingSmall))

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.paddingMedium)) {
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    "https://aistudio.google.com/app/apikey".toUri()
                                )
                                context.startActivity(intent)
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_gemini_api_key_link_button))
                        }

                        Spacer(modifier = Modifier.height(dimensions.paddingSmall))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                        ) {
                            val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                            
                            OutlinedButton(
                                onClick = { activity?.let { viewModel.saveGeminiApiKeyToGoogle(it) } },
                                modifier = Modifier.weight(1f),
                                enabled = userEmail != null && !geminiApiKey.isNullOrEmpty()
                            ) {
                                Icon(GhostTalkIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_cloud_backup_now), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { activity?.let { viewModel.importGeminiApiKeyFromGoogle(it) } },
                                modifier = Modifier.weight(1f),
                                enabled = userEmail != null
                            ) {
                                Icon(GhostTalkIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_cloud_restore_now), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (clipboardKey != null) {
                            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                            Button(
                                onClick = {
                                    viewModel.setGeminiApiKey(clipboardKey)
                                    clipboardKey = null
                                },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_gemini_api_key_smart_paste))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            if (isEnabled) {
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                
                val cardColor = if (isVerified) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                val borderColor = if (isVerified) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
                val icon = if (isVerified) GhostTalkIcons.Cloud else GhostTalkIcons.AutoAwesome
                val iconColor = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val descriptionText = if (isVerified) {
                    stringResource(R.string.settings_gemini_status_verified)
                } else {
                    stringResource(R.string.settings_gemini_status_unverified)
                }

                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.paddingMedium)
                ) {
                    Column(
                        modifier = Modifier.padding(dimensions.paddingMedium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(dimensions.paddingSmall))
                            Text(
                                text = buildAnnotatedString {
                                    append(stringResource(R.string.settings_gemini_tool_status_title))
                                    append(" ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)) {
                                        append(if (isVerified) stringResource(R.string.settings_gemini_tool_status_active) else stringResource(R.string.settings_gemini_tool_status_failed))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                        
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                        
                        Button(
                            onClick = { viewModel.activateGemini(context) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isVerified) stringResource(R.string.settings_gemini_retest_button) else stringResource(R.string.settings_gemini_test_button),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSideBySide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
        ) {
            SmartPredictionsCategory(modifier = Modifier.weight(1f))
            GeminiCategory(modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
        ) {
            SmartPredictionsCategory(modifier = Modifier.fillMaxWidth())
            GeminiCategory(modifier = Modifier.fillMaxWidth())
        }
    }
}
