package com.andreas_kratzer.ghosttalk.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = allPages.find { it.id == pageId }

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_editor_title, page.name)) },
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
                stringResource(R.string.page_editor_hint),
                style = if (isLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = if (isLandscape) 8.dp else 16.dp)
            )

            val effectiveScanPattern = page.scanPattern ?: bookDefaultScanPattern
            val rowDefaultLabelTemplate = stringResource(R.string.page_row_label)

            // Button Grid for editing
            LazyVerticalGrid(
                columns = GridCells.Fixed(page.columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val totalRows = page.rows
                for (r in 0 until totalRows) {
                    if (effectiveScanPattern == "row_by_row") {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RowNameEditor(
                                initialName = page.rowNames.getOrNull(r) ?: rowDefaultLabelTemplate.format(r + 1),
                                onNameChanged = { newName ->
                                    pageViewModel.updateRowName(page.id, r, newName)
                                }
                            )
                        }
                    }

                    val startIdx = r * page.columns
                    val endIdx = minOf(startIdx + page.columns, page.buttonConfigs.size)

                    for (i in startIdx until endIdx) {
                        item {
                            val buttonConfig = page.buttonConfigs[i]
                            GridButton(
                                buttonConfig = buttonConfig,
                                isFocused = false,
                                isEditorMode = true,
                                onClick = {
                                    selectedButtonIndex = i
                                    showDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog && selectedButtonIndex != null) {
        val editingIndex = selectedButtonIndex!!
        val currentConfig = page.buttonConfigs.getOrNull(editingIndex)
        val buttonId = currentConfig?.id ?: UUID.randomUUID().toString()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = allPages,
            featureGuard = pageViewModel.featureGuard,
            onDismiss = {
                showDialog = false
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                pageViewModel.updateButtonConfig(page.id, editingIndex, newConfig)
                showDialog = false
                selectedButtonIndex = null
            }
        )
    }
}

@Composable
fun RowNameEditor(initialName: String, onNameChanged: (String) -> Unit) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.page_editor_row_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { 
            if (text != initialName) onNameChanged(text) 
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && text != initialName) {
                    onNameChanged(text)
                }
            }
    )
}
