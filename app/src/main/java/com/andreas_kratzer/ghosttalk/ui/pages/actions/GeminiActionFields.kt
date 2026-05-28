package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun GeminiActionFields(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    availableTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    onAutoSave: () -> Unit = {}
) {
    val dimensions = LocalDimensions.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.button_gemini_prompt_field)) },
            placeholder = { Text(stringResource(R.string.button_gemini_prompt_hint)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().onFocusChanged { 
                if (!it.isFocused) onAutoSave()
            }
        )

        if (availableTools.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_gemini_available_tools_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                for (tool in availableTools) {
                    ToolItem(tool)
                }
            }
        } else {
             Text(
                text = stringResource(R.string.button_gemini_no_tools_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ToolItem(tool: com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GeminiNanoActionFields() {
    Text(
        text = "Gemini Nano arbeitet aktuell ohne externe Tools.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun GeminiVisionActionFields(
    prompt: String,
    useCloud: Boolean,
    isCloudEnabled: Boolean = true,
    playShutterSound: Boolean = true,
    onPromptChanged: (String) -> Unit,
    onUseCloudChanged: (Boolean) -> Unit,
    onPlayShutterSoundChanged: (Boolean) -> Unit,
    onAutoSave: () -> Unit = {}
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val dimensions = LocalDimensions.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.button_gemini_vision_prompt_label)) },
            placeholder = { Text(stringResource(R.string.button_gemini_vision_prompt_hint)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().onFocusChanged { 
                if (!it.isFocused) onAutoSave()
            }
        )

        com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
            label = stringResource(R.string.button_gemini_vision_use_cloud_label),
            checked = useCloud,
            enabled = isCloudEnabled,
            onCheckedChange = onUseCloudChanged,
            onValueChangeFinished = onAutoSave
        )
        
        com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
            label = stringResource(R.string.button_gemini_vision_shutter_sound_label),
            checked = playShutterSound,
            onCheckedChange = onPlayShutterSoundChanged,
            onValueChangeFinished = onAutoSave
        )
    }
}
