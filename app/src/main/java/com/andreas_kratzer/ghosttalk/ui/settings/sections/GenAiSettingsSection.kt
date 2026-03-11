package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.ui.settings.GeminiDeactivatedDialog
import com.andreas_kratzer.ghosttalk.ui.settings.GeminiDownloadDialog
import com.andreas_kratzer.ghosttalk.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenAiSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val isEnabled by viewModel.isGeminiEnabled.collectAsState(false)
    val useLocal by viewModel.useLocalGenerativeAi.collectAsState(false)
    val toolStatus by viewModel.geminiToolStatus.collectAsState(emptyMap())
    
    val isDownloadDialogVisible by viewModel.isDownloadDialogVisible.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatusMessage by viewModel.downloadStatusMessage.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    
    val isDeactivationDialogVisible by viewModel.isDeactivationDialogVisible.collectAsState()
    val nanoFeatureStatus by viewModel.nanoFeatureStatus.collectAsState()

    val smartEnabled by viewModel.isSmartPredictionEnabled.collectAsState(false)
    val redoPrediction by viewModel.geminiRedoPrediction.collectAsState(false)
    val geminiTimeout by viewModel.geminiTimeout.collectAsState(6000L)

    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        // --- Gemini Nano (Lokal) ---
        PreferenceCategory(stringResource(R.string.settings_category_gemini_nano), modifier = Modifier.weight(1f)) {
            val isSupported = nanoFeatureStatus != com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE
            
            SettingsToggleItem(
                label = stringResource(R.string.settings_gemini_local_enable), 
                checked = useLocal,
                enabled = isSupported
            ) { 
                viewModel.setGeminiNanoEnabled(context, it) 
            }
            
            if (!isSupported) {
                Text(
                    text = "Gemini Nano wird auf diesem Gerät nicht unterstützt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
                )
            }
            
            Text(
                text = stringResource(R.string.settings_gemini_local_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
            )

            if (useLocal) {
                SettingsEditTextItem(
                    label = "Gemini Timeout (ms)",
                    value = geminiTimeout.toString(),
                    onValueChange = { newValue -> viewModel.setGeminiTimeoutInput(newValue) }
                )
                
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                
                SettingsToggleItem(
                    label = stringResource(R.string.settings_smart_prediction_enable),
                    checked = smartEnabled,
                    onCheckedChange = { viewModel.setSmartPredictionEnabled(it) }
                )
                
                SettingsToggleItem(
                    label = stringResource(R.string.settings_gemini_redo_prediction),
                    checked = redoPrediction,
                    onCheckedChange = { viewModel.setGeminiRedoPrediction(it) }
                )
            }

            GeminiDownloadDialog(
                isVisible = isDownloadDialogVisible,
                progress = downloadProgress,
                statusMessage = downloadStatusMessage,
                isDownloading = isDownloading,
                onConfirm = { viewModel.startGeminiDownload() },
                onDismiss = { viewModel.dismissDownloadDialog() }
            )

            GeminiDeactivatedDialog(
                isVisible = isDeactivationDialogVisible,
                onDismiss = { viewModel.dismissDeactivationDialog() }
            )
        }

        // --- Gemini (Cloud) ---
        PreferenceCategory(stringResource(R.string.settings_category_gemini), modifier = Modifier.weight(1f)) {
            SettingsToggleItem(stringResource(R.string.settings_gemini_enable), isEnabled) { 
                viewModel.setGeminiEnabled(context, it) 
            }
            
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
