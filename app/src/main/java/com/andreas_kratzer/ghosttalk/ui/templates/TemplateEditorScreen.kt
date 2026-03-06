package com.andreas_kratzer.ghosttalk.ui.templates

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.RowNameEditor
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String,
    templateViewModel: TemplateViewModel,
    pageViewModel: PageViewModel, // Needed for available pages in ButtonConfigDialog
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val templates by templateViewModel.templates.collectAsState()
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val template = templates.find { it.id == templateId }
    val dimensions = LocalDimensions.current

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (template == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.template_loading))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    OutlinedTextField(
                        value = template.name,
                        onValueChange = { newName ->
                            templateViewModel.updateTemplate(template.copy(name = newName))
                        },
                        label = { Text(stringResource(R.string.template_name_label)) },
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
                    val rowsLabel = stringResource(R.string.page_rows_field) + ": ${template.rows}"
                    Text(
                        text = rowsLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = template.rows.toFloat(),
                        onValueChange = { newValue ->
                            templateViewModel.updateTemplate(template.copy(rows = newValue.toInt()))
                        },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val colsLabel = stringResource(R.string.page_cols_field) + ": ${template.columns}"
                    Text(
                        text = colsLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = template.columns.toFloat(),
                        onValueChange = { newValue ->
                            templateViewModel.updateTemplate(template.copy(columns = newValue.toInt()))
                        },
                        valueRange = 1f..6f,
                        steps = 4
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
            val currentPatternLabel = options.find { it.first == template.scanPattern }?.second ?: stringResource(R.string.page_pattern_default)

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
                                templateViewModel.updateTemplate(template.copy(scanPattern = pattern))
                                expandedPattern = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            val effectiveScanPattern = template.scanPattern ?: bookDefaultScanPattern
            val rowDefaultLabelTemplate = stringResource(R.string.page_row_label)

            // Button Grid for editing
            LazyVerticalGrid(
                columns = GridCells.Fixed(template.columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
            ) {
                val totalRows = template.rows
                val numCols = template.columns

                for (r in 0 until totalRows) {
                    if (effectiveScanPattern == "row_by_row") {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RowNameEditor(
                                initialName = template.rowNames.getOrNull(r) ?: rowDefaultLabelTemplate.format(r + 1),
                                onNameChanged = { newName ->
                                    val updatedNames = template.rowNames.toMutableList()
                                    while (updatedNames.size <= r) updatedNames.add(rowDefaultLabelTemplate.format(updatedNames.size + 1))
                                    updatedNames[r] = newName
                                    templateViewModel.updateTemplate(template.copy(rowNames = updatedNames))
                                }
                            )
                        }
                    }

                    for (c in 0 until numCols) {
                        item {
                            val globalIndex = r * 6 + c // Persistent 6x6 mapping
                            val buttonConfig = template.buttonConfigs.getOrNull(globalIndex)
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
        val currentConfig = template.buttonConfigs.getOrNull(editingIndex)
        val buttonId = currentConfig?.id ?: UUID.randomUUID().toString()
        val allTemplates by pageViewModel.templates.collectAsState()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = unfilteredPages,
            featureGuard = pageViewModel.featureGuard,
            templates = allTemplates,
            onDismiss = {
                showDialog = false
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                templateViewModel.updateButtonConfig(template, editingIndex, newConfig)
                showDialog = false
                selectedButtonIndex = null
            },
            onTest = { testConfig ->
                pageViewModel.actionExecutor.executeButtonAction(testConfig)
            },
            onNavigateToPage = { pageId ->
                scope.launch {
                    val target = pageViewModel.pageManagementDelegate.getPageById(pageId)
                    if (target != null) {
                        onNavigateBack() // Close Template Editor
                        pageViewModel.loadPage(target)
                    }
                }
            },
            onCreatePage = { name, rows, cols, tId, onCreated ->
                val bookId = pageViewModel.activeBookId.value ?: "book-default"
                pageViewModel.createNewPage(name, rows, cols, bookId, tId, onCreated)
            }
        )
    }
}
