package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: ((String) -> Unit)? = null
) {
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = unfilteredPages.find { it.id == pageId }
    val dimensions = LocalDimensions.current

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
                title = { 
                    OutlinedTextField(
                        value = page.name,
                        onValueChange = { newName ->
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                newName = newName,
                                newScanPattern = page.scanPattern,
                                newRowNames = page.rowNames,
                                newRows = page.rows,
                                newColumns = page.columns
                            )
                        },
                        label = { Text(stringResource(R.string.page_name_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(end = dimensions.paddingLarge)
                    )
                },
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
                .padding(if (isLandscape) dimensions.paddingMedium else dimensions.paddingLarge)
        ) {
            // Grid Size Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingLarge),
                horizontalArrangement = Arrangement.spacedBy(dimensions.paddingExtraLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val rowsLabel = stringResource(R.string.page_rows_field) + ": ${page.rows}"
                    Text(
                        text = rowsLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = page.rows.toFloat(),
                        onValueChange = { newValue ->
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                newName = page.name,
                                newScanPattern = page.scanPattern,
                                newRowNames = page.rowNames,
                                newRows = newValue.toInt(),
                                newColumns = page.columns
                            )
                        },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val colsLabel = stringResource(R.string.page_cols_field) + ": ${page.columns}"
                    Text(
                        text = colsLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = page.columns.toFloat(),
                        onValueChange = { newValue ->
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                newName = page.name,
                                newScanPattern = page.scanPattern,
                                newRowNames = page.rowNames,
                                newRows = page.rows,
                                newColumns = newValue.toInt()
                            )
                        },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }
            }

            // Scan Pattern Dropdown (Material 3 Expressive Style)
            var expandedPattern by remember { mutableStateOf(false) }
            val options = listOf(
                null to stringResource(R.string.page_pattern_default),
                "linear" to stringResource(R.string.settings_pattern_linear),
                "row_by_row" to stringResource(R.string.settings_pattern_row_by_row)
            )
            val currentPatternLabel = options.find { it.first == page.scanPattern }?.second ?: stringResource(R.string.page_pattern_default)

            ExposedDropdownMenuBox(
                expanded = expandedPattern,
                onExpandedChange = { expandedPattern = !expandedPattern },
                modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingLarge)
            ) {
                OutlinedTextField(
                    value = currentPatternLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.page_scan_pattern_override)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPattern) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedPattern,
                    onDismissRequest = { expandedPattern = false }
                ) {
                    options.forEach { (pattern, label) ->
                        DropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                pageViewModel.updatePageSettings(
                                    pageId = page.id,
                                    newName = page.name,
                                    newScanPattern = pattern,
                                    newRowNames = page.rowNames
                                )
                                expandedPattern = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            val effectiveScanPattern = page.scanPattern ?: bookDefaultScanPattern
            val rowDefaultLabelTemplate = stringResource(R.string.page_row_label)

            // Button Grid for editing
            LazyVerticalGrid(
                columns = GridCells.Fixed(page.columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
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

                    val numCols = page.columns
                    
                    for (c in 0 until numCols) {
                        item {
                            val globalIndex = GridUtils.getGlobalIndex(r, c)
                            val buttonConfig = page.buttonConfigs.getOrNull(globalIndex)
                            GridButton(
                                buttonConfig = buttonConfig,
                                isFocused = false,
                                isEditorMode = true,
                                onClick = {
                                    selectedButtonIndex = globalIndex
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
        val templates by pageViewModel.templates.collectAsState()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = unfilteredPages,
            featureGuard = pageViewModel.featureGuard,
            templates = templates,
            onDismiss = {
                showDialog = false
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                pageViewModel.updateButtonConfig(page.id, editingIndex, newConfig)
                showDialog = false
                selectedButtonIndex = null
            },
            onTest = { testConfig ->
                pageViewModel.actionExecutor.executeButtonAction(testConfig)
            },
            onNavigateToPage = onEditPage,
            onCreatePage = { name, rows, cols, templateId, onCreated ->
                pageViewModel.createNewPage(
                    name = name,
                    rows = rows,
                    columns = cols,
                    bookId = page.bookId,
                    templateId = templateId,
                    onCreated = onCreated
                )
            }
        )
    }
}

@Composable
fun RowNameEditor(initialName: String, onNameChanged: (String) -> Unit) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    val dimensions = LocalDimensions.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.page_editor_row_name_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { 
            if (text != initialName) onNameChanged(text) 
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && text != initialName) {
                    onNameChanged(text)
                }
            }
    )
}
