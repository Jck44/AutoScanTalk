package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenAiSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val isEnabled by viewModel.isGeminiEnabled.collectAsState(false)
    val toolStatus by viewModel.geminiToolStatus.collectAsState(emptyMap())
    val geminiApiKey by viewModel.geminiApiKey.collectAsState("")
    val useGeminiApiKey by viewModel.useGeminiApiKey.collectAsState(false)
    val userEmail by viewModel.userEmail.collectAsState()

    val smartEnabled by viewModel.isSmartPredictionEnabled.collectAsState(false)

    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    var clipboardKey by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        try {
            val text = clipboardManager.getText()?.text
            if (text != null && text.trim().matches(Regex("^AIzaSy[A-Za-z0-9_-]{33}$"))) {
                clipboardKey = text.trim()
            }
        } catch (_: Exception) {
            // Ignore clipboard errors
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        // --- Smarte Vorhersagen ---
        PreferenceCategory("Smarte Vorhersagen (Statistik)", modifier = Modifier.weight(1f)) {
            SettingsToggleItem(
                label = stringResource(R.string.settings_smart_prediction_enable),
                checked = smartEnabled,
                onCheckedChange = { viewModel.setSmartPredictionEnabled(it) }
            )
            
            Text(
                text = "Ermöglicht der App, basierend auf Klick-Historie, Ort und Zeit des Nutzers, Kachel-Empfehlungen auf Smart-Prediction-Buttons anzuzeigen.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
            )
        }

        // --- Gemini (Cloud) -----
        PreferenceCategory(stringResource(R.string.settings_category_gemini), modifier = Modifier.weight(1f)) {
            SettingsToggleItem(stringResource(R.string.settings_gemini_enable), isEnabled) { 
                viewModel.setGeminiEnabled(context, it) 
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                
                Text(
                    text = stringResource(R.string.settings_gemini_auth_method),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setUseGeminiApiKey(false) }
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = !useGeminiApiKey,
                            onClick = { viewModel.setUseGeminiApiKey(false) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.settings_gemini_auth_oauth), style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setUseGeminiApiKey(true) }
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = useGeminiApiKey,
                            onClick = { viewModel.setUseGeminiApiKey(true) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.settings_gemini_auth_apikey), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
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
                        onValueChange = { viewModel.setGeminiApiKey(it) }
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
                                    android.net.Uri.parse("https://aistudio.google.com/app/apikey")
                                )
                                context.startActivity(intent)
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_gemini_api_key_link_button))
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
                Column(modifier = Modifier.fillMaxWidth().padding(dimensions.paddingMedium)) {
                    Text(
                        text = "Cloud Features Status:", 
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    toolStatus.forEach { (name, status) ->
                        val isAvailable = status == GeminiUseCase.ToolStatus.AVAILABLE
                        val statusText = when (status) {
                            GeminiUseCase.ToolStatus.AVAILABLE -> stringResource(R.string.settings_gemini_tool_status_active)
                            GeminiUseCase.ToolStatus.REQUIRES_AUTH -> stringResource(R.string.settings_gemini_tool_status_requires_auth)
                            GeminiUseCase.ToolStatus.FAILED -> stringResource(R.string.settings_gemini_tool_status_failed)
                            GeminiUseCase.ToolStatus.PENDING -> stringResource(R.string.settings_gemini_tool_status_pending)
                        }
                        val color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Text(text = "• $name: $statusText", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.activateGemini(context) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(top = dimensions.paddingMedium)
                    ) {
                        Text(stringResource(R.string.settings_gemini_activate_button))
                    }
                }
            }
        }
    }
}
