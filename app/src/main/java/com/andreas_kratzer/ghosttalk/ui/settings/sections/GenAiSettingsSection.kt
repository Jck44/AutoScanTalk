package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@Composable
fun GenAiSettingsSection(viewModel: SettingsViewModel) {
    val isEnabled by viewModel.isGeminiEnabled.collectAsState(false)
    val useLocal by viewModel.useLocalGenerativeAi.collectAsState(false)
    val toolStatus by viewModel.geminiToolStatus.collectAsState(emptyMap())
    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    PreferenceCategory(stringResource(R.string.settings_category_gemini)) {
        // Gemini Nano (Local)
        SettingsToggleItem(stringResource(R.string.settings_gemini_local_enable), useLocal) { 
            viewModel.setUseLocalGenerativeAi(it, context) 
        }

        // Gemini Cloud
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
