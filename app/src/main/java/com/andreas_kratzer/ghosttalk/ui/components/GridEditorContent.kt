package com.andreas_kratzer.ghosttalk.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.GridItem
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.RowNameEditor
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridEditorContent(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard,
    bookDefaultScanPattern: String?,
    paddingValues: PaddingValues,
    onEditPage: ((String) -> Unit)? = null
) {
    val dimensions = LocalDimensions.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

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
                val rowsLabel = stringResource(R.string.page_rows_field) + ": ${item.rows}"
                Text(
                    text = rowsLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = item.rows.toFloat(),
                    onValueChange = { newValue ->
                        actions.updateGridSettings(
                            itemId = item.id,
                            newName = item.name,
                            newScanPattern = item.scanPattern,
                            newRowNames = item.rowNames,
                            newRows = newValue.toInt(),
                            newColumns = item.columns
                        )
                    },
                    valueRange = 1f..7f,
                    steps = 5
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                val colsLabel = stringResource(R.string.page_cols_field) + ": ${item.columns}"
                Text(
                    text = colsLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = item.columns.toFloat(),
                    onValueChange = { newValue ->
                        actions.updateGridSettings(
                            itemId = item.id,
                            newName = item.name,
                            newScanPattern = item.scanPattern,
                            newRowNames = item.rowNames,
                            newRows = item.rows,
                            newColumns = newValue.toInt()
                        )
                    },
                    valueRange = 1f..7f,
                    steps = 5
                )
            }
        }

        // Scan Pattern Dropdown
        var expandedPattern by remember { mutableStateOf(false) }
        val options = listOf(
            null to stringResource(R.string.page_pattern_default),
            "linear" to stringResource(R.string.settings_pattern_linear),
            "row_by_row" to stringResource(R.string.settings_pattern_row_by_row)
        )
        val currentPatternLabel = options.find { it.first == item.scanPattern }?.second ?: stringResource(R.string.page_pattern_default)

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
                            actions.updateGridSettings(
                                itemId = item.id,
                                newName = item.name,
                                newScanPattern = pattern,
                                newRowNames = item.rowNames
                            )
                            expandedPattern = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        val effectiveScanPattern = item.scanPattern ?: bookDefaultScanPattern
        val rowDefaultLabelTemplate = stringResource(R.string.page_row_label)

        // Button Grid for editing
        LazyVerticalGrid(
            columns = GridCells.Fixed(item.columns),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
        ) {
            val totalRows = item.rows
            for (r in 0 until totalRows) {
                if (effectiveScanPattern == "row_by_row") {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RowNameEditor(
                            initialName = item.rowNames.getOrNull(r) ?: rowDefaultLabelTemplate.format(r + 1),
                            onNameChanged = { newName ->
                                actions.updateRowName(item.id, r, newName)
                            }
                        )
                    }
                }

                val numCols = item.columns
                for (c in 0 until numCols) {
                    item {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
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

    if (showDialog && selectedButtonIndex != null) {
        val editingIndex = selectedButtonIndex!!
        val currentConfig = item.buttonConfigs.getOrNull(editingIndex)
        val buttonId = currentConfig?.id ?: UUID.randomUUID().toString()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = availablePages,
            featureGuard = featureGuard,
            templates = templates,
            onDismiss = {
                showDialog = false
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                actions.updateButtonConfig(item.id, editingIndex, newConfig)
                showDialog = false
                selectedButtonIndex = null
            },
            onTest = { testConfig ->
                actions.executeButtonAction(testConfig)
            },
            onNavigateToPage = onEditPage,
            onCreatePage = { name, rows, cols, templateId, onCreated ->
                val bookId = (item as? Page)?.bookId ?: "book-default"
                actions.createNewPage(
                    name = name,
                    rows = rows,
                    columns = cols,
                    bookId = bookId,
                    templateId = templateId,
                    onCreated = onCreated
                )
            }
        )
    }
}
