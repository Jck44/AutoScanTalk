package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions

@Composable
fun TemplatesWorkbenchScreen(
    templateViewModel: TemplateViewModel,
    onNavigateBack: () -> Unit,
    onTemplateClick: (String) -> Unit
) {
    var editingButtonTemplate by remember { mutableStateOf<ButtonTemplate?>(null) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Seiten-Vorlagen, 1 = Button-Vorlagen

    GhostTalkScaffold(
        title = stringResource(R.string.nav_templates),
        onNavigateBack = onNavigateBack
    ) { innerPadding ->
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left half: Page templates
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TemplateScreenContent(
                        templateViewModel = templateViewModel,
                        onTemplateClick = onTemplateClick
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .padding(vertical = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                }

                // Right half: Button templates
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Button-Vorlagen",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ButtonTemplatesPanel(
                            actions = templateViewModel,
                            onEditTemplate = { editingButtonTemplate = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            // Responsive mobile view using Tabs
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                androidx.compose.material3.SecondaryTabRow(
                    selectedTabIndex = activeSubTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = { Text("Seiten") }
                    )
                    androidx.compose.material3.Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = { Text("Buttons") }
                    )
                }

                if (activeSubTab == 0) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        TemplateScreenContent(
                            templateViewModel = templateViewModel,
                            onTemplateClick = onTemplateClick
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp)) {
                        ButtonTemplatesPanel(
                            actions = templateViewModel,
                            onEditTemplate = { editingButtonTemplate = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (editingButtonTemplate != null) {
        val template = editingButtonTemplate!!
        val pages by templateViewModel.pageRepository.getAllPagesFlow().collectAsState(initial = emptyList())
        val templates by templateViewModel.templates.collectAsState()
        
        com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog(
            buttonConfig = template.buttonConfig,
            pages = pages,
            templates = templates,
            defaultStartPageId = templateViewModel.settingsRepository.defaultStartPageId,
            onDismiss = { editingButtonTemplate = null },
            onSave = { newConfig ->
                templateViewModel.updateButtonTemplate(template.copy(name = newConfig.label, buttonConfig = newConfig))
                editingButtonTemplate = null
            },
            onTest = { config ->
                templateViewModel.executeButtonAction(config)
            },
            onDelete = {
                templateViewModel.deleteButtonTemplate(template)
                editingButtonTemplate = null
            },
            onCreatePage = { name, r, c, tId, cb ->
                templateViewModel.createNewPage(name, r, c, "", tId, cb)
            },
            isTextCached = { templateViewModel.isTextCached(it) },
            onPrefetchText = { text, cb -> templateViewModel.prefetchText(text, cb) },
            onSaveAsTemplate = {}
        )
    }
}

@Composable
private fun TemplateScreenContent(
    templateViewModel: TemplateViewModel,
    onTemplateClick: (String) -> Unit
) {
    val templates by templateViewModel.templates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.PageTemplate?>(null) }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val dynamicCardHeight = adaptiveCardHeight()
        val dimensions = LocalDimensions.current

        Column(modifier = Modifier.fillMaxSize()) {
            val searchQuery by templateViewModel.searchQuery.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { templateViewModel.updateSearchQuery(it) },
                placeholder = { Text("Vorlagen durchsuchen") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { templateViewModel.updateSearchQuery("") }) {
                            androidx.compose.material3.Icon(Icons.Default.Clear, contentDescription = "Löschen")
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.paddingMedium),
                singleLine = true
            )

            if (templates.isEmpty()) {
                com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkEmptyState(
                    icon = GhostTalkIcons.GridView,
                    title = "Keine Vorlagen gefunden",
                    description = "Erstellen Sie eine neue Seitenvorlage, um loszulegen.",
                    actionLabel = "Vorlage erstellen",
                    onAction = { showAddDialog = true }
                )
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 240.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = dimensions.screenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                    contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
                ) {
                    items(templates.size, key = { index -> templates[index].id }) { index ->
                        val template = templates[index]
                        com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard(
                            title = template.name,
                            subtitle = "Raster: ${template.rows}x${template.columns}",
                            icon = GhostTalkIcons.GridView,
                            onClick = { onTemplateClick(template.id) },
                            height = dynamicCardHeight,
                            trailingAction = {
                                androidx.compose.material3.IconButton(onClick = { templateToDelete = template }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddTemplateDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, rows, cols ->
                    templateViewModel.createTemplate(name, rows, cols)
                    showAddDialog = false
                }
            )
        }

        templateToDelete?.let { template ->
            com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
                title = "Vorlage löschen",
                onDismiss = { templateToDelete = null },
                confirmText = "Löschen",
                onConfirm = {
                    templateViewModel.deleteTemplate(template)
                    templateToDelete = null
                },
                dismissText = "Abbrechen",
                isDestructive = true
            ) {
                Text("Sind Sie sicher, dass Sie die Vorlage '${template.name}' löschen möchten?")
            }
        }
    }
}
