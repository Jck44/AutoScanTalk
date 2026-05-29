package com.andreas_kratzer.ghosttalk.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalCurrentPageId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.ui.components.TemplateDropTarget
import com.andreas_kratzer.ghosttalk.ui.components.CategoryHeaderDropTarget
import com.andreas_kratzer.ghosttalk.ui.templates.ButtonTemplatesPanel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import android.annotation.SuppressLint
import androidx.compose.material3.rememberModalBottomSheetState
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction

data class GridCellTarget(val index: Int)
data class InsertTarget(val index: Int)
object TemplatesPanelTarget
data class DraggedGridCell(val index: Int, val config: ButtonConfig)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun GridEditorContent(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard,
    bookDefaultScanPattern: String?,
    paddingValues: PaddingValues,
    onEditPage: ((String) -> Unit)? = null,
    initialButtonId: String? = null,
    pageViewModel: PageViewModel? = null
) {
    CompositionLocalProvider(
        LocalCurrentPageId provides item.id,
        LocalIsUserModeActive provides false
    ) {
        val dimensions = LocalDimensions.current
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = LocalDensity.current.density
        val context = LocalContext.current

        var selectedButtonIndex by rememberSaveable { mutableStateOf<Int?>(null) }
        var showDialog by rememberSaveable { mutableStateOf(false) }
        var editingRowIndex by rememberSaveable { mutableStateOf<Int?>(null) }
        var showRowEditDialog by rememberSaveable { mutableStateOf(false) }
        var showMoveDialog by rememberSaveable { mutableStateOf(false) }
        var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
        var isDuplicating by rememberSaveable { mutableStateOf(false) }
        var showHiddenPrompt by remember { 
            mutableStateOf<com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?>(null) 
        }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var showLayoutSettingsSheet by rememberSaveable { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        LaunchedEffect(initialButtonId, item) {
            if (initialButtonId != null && item is Page) {
                val index = item.buttonConfigs.indexOfFirst { it?.id == initialButtonId }
                if (index != -1) {
                    selectedButtonIndex = index
                    showDialog = true
                }
            }
        }

        val horizontalPadding = if (isLandscape) dimensions.paddingMedium else dimensions.paddingLarge
        val gridState = rememberLazyGridState()
        val rowReorderState = rememberReorderableState()
        val buttonReorderState = rememberReorderableState()

        val dragDropState = rememberDragDropState()
        var showSaveTemplateDialogConfig by remember { mutableStateOf<ButtonConfig?>(null) }
        var newTemplateName by remember { mutableStateOf("") }
        var editingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
        val buttonTemplates by pageViewModel?.buttonTemplates?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
        val spotifyPlaylists by pageViewModel?.spotifyPlaylists?.collectAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) }
        val isSpotifyLoadingPlaylists by pageViewModel?.isLoadingPlaylists?.collectAsState(false) ?: remember { mutableStateOf(false) }
        val spotifyUserDisplayName by pageViewModel?.spotifyUserDisplayName?.collectAsState(null) ?: remember { mutableStateOf(null) }
        val editingTemplate = remember(editingTemplateId, buttonTemplates) {
            buttonTemplates.find { it.id == editingTemplateId }
        }

        fun showUndoSnackbar(message: String) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Rückgängig",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    actions.undo { undoMsg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(undoMsg)
                        }
                    }
                }
            }
        }

        val onDrop: (Any, Any) -> Unit = { draggedItem, target ->
            when (draggedItem) {
                is ButtonTemplate -> {
                    when (target) {
                        is GridCellTarget -> {
                            val config = draggedItem.buttonConfig.copy(
                                id = java.util.UUID.randomUUID().toString()
                            )
                            actions.insertButtonConfig(item.id, target.index, config, false) { success ->
                                if (success) {
                                    showUndoSnackbar("Vorlage platziert")
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Zielseite ist voll")
                                    }
                                }
                            }
                        }
                        is InsertTarget -> {
                            val config = draggedItem.buttonConfig.copy(
                                id = java.util.UUID.randomUUID().toString()
                            )
                            actions.insertButtonConfig(item.id, target.index, config, true) { success ->
                                if (success) {
                                    showUndoSnackbar("Vorlage eingefügt")
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Zielseite ist voll")
                                    }
                                }
                            }
                        }
                        is TemplateDropTarget -> {
                            if (pageViewModel != null) {
                                val allTemplates = pageViewModel.buttonTemplates.value
                                val fromItem = draggedItem
                                val toItem = target.template
                                if (fromItem.id != toItem.id) {
                                    val fromCategory = ActionCategoryRegistry.getGroupForAction(fromItem.buttonConfig.buttonAction)
                                    val toCategory = ActionCategoryRegistry.getGroupForAction(toItem.buttonConfig.buttonAction)
                                    
                                    if (fromCategory == toCategory) {
                                        val categoryTemplates = allTemplates
                                            .filter { ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction) == fromCategory }
                                            .sortedBy { it.orderIndex }
                                            .toMutableList()
                                            
                                        val fromIdxInCat = categoryTemplates.indexOfFirst { it.id == fromItem.id }
                                        val toIdxInCat = categoryTemplates.indexOfFirst { it.id == toItem.id }
                                        
                                        if (fromIdxInCat != -1 && toIdxInCat != -1) {
                                            categoryTemplates.removeAt(fromIdxInCat)
                                            categoryTemplates.add(toIdxInCat, fromItem)
                                            
                                            val grouped = allTemplates.groupBy {
                                                ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction)
                                            }
                                            
                                            val newGlobalList = mutableListOf<ButtonTemplate>()
                                            ActionCategoryRegistry.ALL_GROUPS.forEach { cat ->
                                                val itemsInCat = if (cat == fromCategory) {
                                                    categoryTemplates
                                                } else {
                                                    grouped[cat]?.sortedBy { it.orderIndex } ?: emptyList()
                                                }
                                                newGlobalList.addAll(itemsInCat)
                                            }
                                            
                                            pageViewModel.updateButtonTemplatesOrder(newGlobalList)
                                        }
                                    }
                                }
                            }
                        }
                        is CategoryHeaderDropTarget -> {
                            if (pageViewModel != null) {
                                val allTemplates = pageViewModel.buttonTemplates.value
                                val fromItem = draggedItem
                                val fromCategory = ActionCategoryRegistry.getGroupForAction(fromItem.buttonConfig.buttonAction)
                                val toCategory = target.groupName
                                
                                if (fromCategory == toCategory) {
                                    val categoryTemplates = allTemplates
                                        .filter { ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction) == fromCategory }
                                        .sortedBy { it.orderIndex }
                                        .toMutableList()
                                        
                                    val fromIdxInCat = categoryTemplates.indexOfFirst { it.id == fromItem.id }
                                    if (fromIdxInCat != -1) {
                                        categoryTemplates.removeAt(fromIdxInCat)
                                        categoryTemplates.add(0, fromItem)
                                        
                                        val grouped = allTemplates.groupBy {
                                            ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction)
                                        }
                                        
                                        val newGlobalList = mutableListOf<ButtonTemplate>()
                                        ActionCategoryRegistry.ALL_GROUPS.forEach { cat ->
                                            val itemsInCat = if (cat == fromCategory) {
                                                categoryTemplates
                                            } else {
                                                grouped[cat]?.sortedBy { it.orderIndex } ?: emptyList()
                                            }
                                            newGlobalList.addAll(itemsInCat)
                                        }
                                        
                                        pageViewModel.updateButtonTemplatesOrder(newGlobalList)
                                    }
                                }
                            }
                        }
                    }
                }
                is DraggedGridCell -> {
                    if (target is GridCellTarget) {
                        if (draggedItem.index != target.index) {
                            actions.moveButton(item.id, draggedItem.index, target.index)
                            showUndoSnackbar("Button verschoben")
                        }
                    } else if (target is InsertTarget) {
                        actions.moveButtonWithInsert(item.id, draggedItem.index, target.index)
                        showUndoSnackbar("Button verschoben")
                    } else if (target is TemplatesPanelTarget || target is TemplateDropTarget || target is CategoryHeaderDropTarget) {
                        showSaveTemplateDialogConfig = draggedItem.config
                        newTemplateName = draggedItem.config.label
                    }
                }
            }
        }

        DragDropContainer(
            state = dragDropState,
            onDrop = onDrop,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            floatingPreview = { draggedItem ->
                when (draggedItem) {
                    is ButtonTemplate -> {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = draggedItem.buttonConfig.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    is DraggedGridCell -> {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = draggedItem.config.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Grid controls and layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = if (isLandscape) dimensions.paddingMedium else dimensions.paddingLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Standardmäßig zeigen wir jetzt die Chips-Bar an. 
                    // Diese öffnet bei Klick das Bottom Sheet mit den detaillierten Einstellungen.
                    GridEditorSummaryBar(item = item, onClick = { showLayoutSettingsSheet = true })

                    val effectiveScanPattern = item.scanPattern ?: bookDefaultScanPattern
                    val isRowByRow = effectiveScanPattern == "row_by_row" || effectiveScanPattern == "row_column"

                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val sizeInfo = calculateGridSize(
                            maxWidth = maxWidth,
                            maxHeight = maxHeight,
                            rows = item.rows,
                            cols = item.columns,
                            isRowByRow = isRowByRow,
                            horizontalPadding = horizontalPadding,
                            isTablet = dimensions.isTablet,
                            dimensions = dimensions
                        )

                        val gridSpacingPx = with(LocalDensity.current) { dimensions.gridSpacing.toPx() }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(item.columns),
                            state = gridState,
                            modifier = Modifier
                                .width(sizeInfo.totalWidth)
                                .height(sizeInfo.totalHeight.coerceAtMost(maxHeight)),
                            contentPadding = PaddingValues(horizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                        ) {
                            if (isRowByRow) {
                                renderRowByRowGrid(
                                    item = item,
                                    actions = actions,
                                    gridState = gridState,
                                    rowReorderState = rowReorderState,
                                    buttonReorderState = buttonReorderState,
                                    sizeInfo = sizeInfo,
                                    dimensions = dimensions,
                                    density = density,
                                    gridSpacingPx = gridSpacingPx,
                                    availablePages = availablePages,
                                    onEditRow = { editingRowIndex = it; showRowEditDialog = true },
                                    onEditButton = { index ->
                                        selectedButtonIndex = index
                                        showDialog = true
                                    }
                                )
                            } else {
                                renderLinearGrid(
                                    item = item,
                                    actions = actions,
                                    gridState = gridState,
                                    buttonReorderState = buttonReorderState,
                                    sizeInfo = sizeInfo,
                                    dimensions = dimensions,
                                    density = density,
                                    gridSpacingPx = gridSpacingPx,
                                    availablePages = availablePages,
                                    onEditButton = { index ->
                                        selectedButtonIndex = index
                                        showDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Right Panel: Button Templates Panel
                // Visible in Landscape mode OR on Tablets, if pageViewModel is provided
                if ((isLandscape || dimensions.isTablet) && pageViewModel != null) {
                    ButtonTemplatesPanel(
                        viewModel = pageViewModel,
                        onEditTemplate = { template -> editingTemplateId = template.id },
                        modifier = Modifier
                            .width(if (isLandscape) 320.dp else 280.dp)
                            .fillMaxHeight()
                            .dropTarget(key = TemplatesPanelTarget)
                    )
                }
            }

            // Layout Settings Bottom Sheet
            if (showLayoutSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showLayoutSettingsSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp, 4.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall)
                            )
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = dimensions.paddingDoubleExtraLarge)
                            .padding(horizontal = dimensions.paddingExtraLarge)
                    ) {
                        Text(
                            text = stringResource(R.string.page_grid_info, item.rows, item.columns),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = dimensions.paddingLarge)
                        )
                        GridEditorControls(
                            item = item,
                            actions = actions,
                            forceVertical = true
                        )
                    }
                }
            }

            // Snackbar Host & Dialogs
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensions.paddingLarge),
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }

            EditorDialogs(
                item = item,
                actions = actions,
                availablePages = availablePages,
                templates = templates,
                featureGuard = featureGuard,
                editingRowIndex = editingRowIndex,
                showRowEditDialog = showRowEditDialog,
                selectedButtonIndex = selectedButtonIndex,
                showDialog = showDialog,
                showMoveDialog = showMoveDialog,
                showDuplicateDialog = showDuplicateDialog,
                isDuplicating = isDuplicating,
                showHiddenPrompt = showHiddenPrompt,
                snackbarHostState = snackbarHostState,
                scope = scope,
                onShowMoveDialog = { value: Boolean -> 
                    showMoveDialog = value 
                    if (value) isDuplicating = false
                },
                onShowDuplicateDialog = { value: Boolean -> 
                    showDuplicateDialog = value
                    if (value) isDuplicating = true
                },
                onShowHiddenPrompt = { value -> showHiddenPrompt = value },
                onDismissRowDialog = {
                    showRowEditDialog = false
                    editingRowIndex = null
                },
                onDismissButtonDialog = {
                    showDialog = false
                    if (!showMoveDialog && !showDuplicateDialog && showHiddenPrompt == null) {
                        selectedButtonIndex = null
                    }
                },
                onEditPage = onEditPage,
                philipsHueManager = pageViewModel?.philipsHueManager
            )

            if (editingTemplate != null) {
                val template = editingTemplate
                com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog(
                    buttonConfig = template.buttonConfig,
                    pages = availablePages,
                    templates = templates,
                    onDismiss = { editingTemplateId = null },
                    onSave = { newConfig ->
                        pageViewModel?.updateButtonTemplate(template.copy(name = newConfig.label, buttonConfig = newConfig))
                        // Fixed: Removed editingTemplateId = null here to prevent dialog from closing during AutoSave (e.g. when permissions are requested)
                    },
                    onTest = { config ->
                        actions.executeButtonAction(config)
                    },
                    onMove = {
                        android.widget.Toast.makeText(context, context.getString(R.string.editor_template_move_not_supported), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDuplicate = {
                        android.widget.Toast.makeText(context, context.getString(R.string.editor_template_duplicate_not_supported), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        pageViewModel?.deleteButtonTemplate(template)
                        editingTemplateId = null
                    },
                    onNavigateToPage = onEditPage,
                    availableGeminiTools = actions.availableGeminiTools,
                    onCreatePage = { name, r, c, t, callback ->
                        val bookId = (item as? Page)?.bookId
                        if (bookId != null) {
                            actions.createNewPage(name, r, c, bookId, t, callback)
                        }
                    },
                    isTextCached = { actions.isTextCached(it) },
                    onPrefetchText = { text, onComplete -> actions.prefetchText(text, onComplete) },
                    philipsHueManager = pageViewModel?.philipsHueManager,
                    hueBridgeIp = pageViewModel?.settingsRepository?.hueBridgeIp ?: "",
                    hueUsername = pageViewModel?.settingsRepository?.hueUsername ?: "",
                    hueCachedDevices = pageViewModel?.settingsRepository?.hueCachedDevices ?: "",
                    onRefreshHueCache = { silent, callback -> pageViewModel?.refreshHueDevicesCache(silent, callback) },
                    featureGuard = featureGuard,
                    onPlayTts = { text, onDone -> actions.speakTtsPreview(text, onDone) },
                    onStopTts = { actions.stopTtsPreview() },
                    isTtsElevenLabs = { actions.isTtsElevenLabs() },
                    spotifyPlaylists = spotifyPlaylists,
                    isLoadingSpotifyPlaylists = isSpotifyLoadingPlaylists,
                    spotifyUserDisplayName = spotifyUserDisplayName,
                    onConnectSpotify = { pageViewModel?.connectSpotify(context) },
                    onDisconnectSpotify = { pageViewModel?.disconnectSpotify() },
                    onLoadSpotifyPlaylists = { pageViewModel?.loadSpotifyPlaylists() },
                    onSaveAsTemplate = {
                        // Already a template
                    }
                )
            }

            // Save Template Dialog from Drag & Drop
            if (showSaveTemplateDialogConfig != null) {
                AlertDialog(
                    onDismissRequest = { showSaveTemplateDialogConfig = null },
                    title = { Text(stringResource(R.string.template_save_as_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.template_enter_name_prompt))
                            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                            OutlinedTextField(
                                value = newTemplateName,
                                onValueChange = { newTemplateName = it },
                                label = { Text(stringResource(R.string.template_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val config = showSaveTemplateDialogConfig
                                if (config != null && newTemplateName.isNotBlank() && pageViewModel != null) {
                                    pageViewModel.saveButtonAsTemplate(newTemplateName, config)
                                    android.widget.Toast.makeText(context, context.getString(R.string.editor_template_saved), android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showSaveTemplateDialogConfig = null
                            }
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveTemplateDialogConfig = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorButtonCell(
    localIndex: Int,
    globalIndex: Int,
    buttonConfig: ButtonConfig?,
    reorderState: ReorderableState,
    isTarget: Boolean,
    width: Dp,
    height: Dp,
    numCols: Int,
    gridSpacing: Dp,
    targetPageName: String? = null,
    onDragEnd: (Int) -> Unit,
    onClick: () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    val isDragging = dragDropState.isDragging
    val isDraggedHovered = dragDropState.currentHoveredTarget == GridCellTarget(globalIndex)
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val isHighlighted = isTarget || isDraggedHovered

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .reorderableItemVisuals(reorderState, localIndex)
            .dragHandle(
                state = reorderState,
                index = localIndex,
                onDragEnd = { fromIdx ->
                    if (fromIdx != null) {
                        onDragEnd(fromIdx)
                    }
                }
            )
            .dropTarget(key = GridCellTarget(globalIndex))
            .run {
                if (buttonConfig != null) {
                    dragSource(item = DraggedGridCell(globalIndex, buttonConfig), longPress = true)
                } else this
            }
    ) {
        GridButton(
            buttonConfig = buttonConfig,
            isFocused = false,
            targetPageName = targetPageName,
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .border(
                    width = if (isDraggedHovered) 3.dp else if (isTarget) 2.dp else 0.dp,
                    color = if (isDraggedHovered) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                            else if (isTarget) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        )

        if (isDragging) {
            val leftTarget = InsertTarget(globalIndex)
            val isLeftHovered = dragDropState.currentHoveredTarget == leftTarget
            // Center the drop zone in the gap: shift left by half the zone width plus half the grid spacing
            val halfGap = gridSpacing / 2
            
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = -(12.dp + halfGap))
                    .width(24.dp)
                    .fillMaxHeight()
                    .dropTarget(key = leftTarget),
                contentAlignment = Alignment.Center
            ) {
                if (isLeftHovered) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(0.85f)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }

            val isLastCol = (globalIndex % numCols) == numCols - 1
            if (isLastCol) {
                val rightTarget = InsertTarget(globalIndex + 1)
                val isRightHovered = dragDropState.currentHoveredTarget == rightTarget
                
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 12.dp + halfGap)
                        .width(24.dp)
                        .fillMaxHeight()
                        .dropTarget(key = rightTarget),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRightHovered) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight(0.85f)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.renderRowByRowGrid(
    item: GridItem,
    actions: GridEditorActions,
    gridState: LazyGridState,
    rowReorderState: ReorderableState,
    buttonReorderState: ReorderableState,
    sizeInfo: GridSizeInfo,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    density: Float,
    gridSpacingPx: Float,
    availablePages: List<Page>,
    onEditRow: (Int) -> Unit,
    onEditButton: (Int) -> Unit
) {
    val rowTargetIndex = rowReorderState.findTargetIndexForGrid(gridState)
    val buttonTargetIndex = buttonReorderState.findTargetButtonIndex(
        gridState = gridState,
        numCols = item.columns,
        isRowByRow = true,
        density = density,
        gridSpacingPx = gridSpacingPx
    )

    for (r in 0 until item.rows) {
        item(span = { GridItemSpan(item.columns) }) {
            val isRowTarget = rowTargetIndex == r
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .reorderableItemVisuals(rowReorderState, r)
                    .background(
                        if (isRowTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (isRowTarget) 3.dp else 2.dp,
                        color = if (isRowTarget) MaterialTheme.colorScheme.primary 
                                 else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    )
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Row Drag Handle & Edit Icon
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .dragHandle(
                            state = rowReorderState,
                            index = r,
                            onDragEnd = { fromIdx ->
                                if (fromIdx != null) {
                                    val to = rowReorderState.findTargetIndexForGrid(gridState)
                                    if (to != null && to != fromIdx) {
                                        actions.moveRow(item.id, fromIdx, to)
                                    }
                                }
                            }
                        )
                        .clickable { onEditRow(r) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Edit,
                        contentDescription = stringResource(R.string.page_editor_row_name_label),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Row Buttons
                Row(
                    modifier = Modifier.weight(1f).padding(dimensions.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                ) {
                    for (c in 0 until item.columns) {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
                        val targetPageName = (buttonConfig?.buttonAction as? NavigateToPageButtonAction)?.let { action ->
                            availablePages.find { it.id == action.pageId }?.name
                        }
                        
                        EditorButtonCell(
                            localIndex = globalIndex, // In RowByRow, buttons are NOT grid items, so we use globalIndex for visual reorder
                            globalIndex = globalIndex,
                            buttonConfig = buttonConfig,
                            reorderState = buttonReorderState,
                            isTarget = buttonTargetIndex == globalIndex,
                            width = sizeInfo.optimalWidth,
                            height = sizeInfo.optimalHeight,
                            numCols = item.columns,
                            gridSpacing = dimensions.gridSpacing,
                            targetPageName = targetPageName,
                            onDragEnd = { fromIdx ->
                                val to = buttonReorderState.findTargetButtonIndex(
                                    gridState = gridState,
                                    numCols = item.columns,
                                    isRowByRow = true,
                                    density = density,
                                    gridSpacingPx = gridSpacingPx
                                )
                                if (to != null && to != fromIdx) {
                                    actions.moveButton(item.id, fromIdx, to)
                                }
                            },
                            onClick = { onEditButton(globalIndex) }
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.renderLinearGrid(
    item: GridItem,
    actions: GridEditorActions,
    gridState: LazyGridState,
    buttonReorderState: ReorderableState,
    sizeInfo: GridSizeInfo,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    density: Float,
    gridSpacingPx: Float,
    availablePages: List<Page>,
    onEditButton: (Int) -> Unit
) {
    val buttonTargetIndex = buttonReorderState.findTargetButtonIndex(
        gridState = gridState,
        numCols = item.columns,
        isRowByRow = false,
        density = density,
        gridSpacingPx = gridSpacingPx
    )
    
    items(item.rows * item.columns) { localIndex ->
        val globalIndex = GridUtils.localToGlobalIndex(localIndex, item.columns)
        val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
        val targetPageName = (buttonConfig?.buttonAction as? NavigateToPageButtonAction)?.let { action ->
            availablePages.find { it.id == action.pageId }?.name
        }

        EditorButtonCell(
                            localIndex = localIndex, // In Linear Grid, buttons ARE grid items, so we use localIndex to match LazyGridState
                            globalIndex = globalIndex,
            buttonConfig = buttonConfig,
            reorderState = buttonReorderState,
            isTarget = buttonTargetIndex == globalIndex,
            width = sizeInfo.optimalWidth,
            height = sizeInfo.optimalHeight,
            numCols = item.columns,
            gridSpacing = dimensions.gridSpacing,
            targetPageName = targetPageName,
            onDragEnd = { fromLocalIdx ->
                val toGlobal = buttonReorderState.findTargetButtonIndex(
                    gridState = gridState,
                    numCols = item.columns,
                    isRowByRow = false,
                    density = density,
                    gridSpacingPx = gridSpacingPx
                )
                if (toGlobal != null) {
                    val fromGlobal = GridUtils.localToGlobalIndex(fromLocalIdx, item.columns)
                    if (toGlobal != fromGlobal) {
                        actions.moveButton(item.id, fromGlobal, toGlobal)
                    }
                }
            },
            onClick = { onEditButton(globalIndex) }
        )
    }
}
