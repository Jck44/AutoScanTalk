package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String,
    templateViewModel: TemplateViewModel,
    pageViewModel: PageViewModel, // Needed for available pages in ButtonConfigDialog
    onNavigateBack: () -> Unit
) {
    val templates by templateViewModel.templates.collectAsState()
    val allPages by pageViewModel.allPages.collectAsState()
    val template = templates.find { it.id == templateId }

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }

    if (template == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.template_loading))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.template_edit_title, template.name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button_content_description))
                    }
                }
            )
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(if (isLandscape) 8.dp else 16.dp)
        ) {
            Text(
                stringResource(R.string.template_editor_hint),
                style = if (isLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = if (isLandscape) 8.dp else 16.dp)
            )

            // Button Grid for editing
            LazyVerticalGrid(
                columns = GridCells.Fixed(template.columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalSlots = template.rows * template.columns
                for (i in 0 until totalSlots) {
                    item {
                        val buttonConfig = template.buttonConfigs.getOrNull(i)
                        GridButton(
                            buttonConfig = buttonConfig,
                            isFocused = false,
                            isEditorMode = true,
                            onClick = {
                                selectedButtonIndex = i
                            }
                        )
                    }
                }
            }
        }
    }

    if (selectedButtonIndex != null) {
        val editingIndex = selectedButtonIndex!!
        val currentConfig = template.buttonConfigs.getOrNull(editingIndex)
        val buttonId = currentConfig?.id ?: UUID.randomUUID().toString()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = allPages, // Allow templates to navigate to specific pages if needed
            featureGuard = pageViewModel.featureGuard,
            onDismiss = {
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                templateViewModel.updateButtonConfig(template.id, editingIndex, newConfig)
                selectedButtonIndex = null
            },
            onTest = { testConfig ->
                pageViewModel.actionExecutor.executeButtonAction(testConfig)
            }
        )
    }
}
