package com.andreas_kratzer.ghosttalk.ui.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalCurrentPageId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.templates.ButtonTemplatesPanel
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import kotlinx.coroutines.launch

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
    onEditPage: ((String, String?) -> Unit)? = null,
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
        val pageMetricsMap by pageViewModel?.pageMetrics?.collectAsState(emptyMap()) ?: remember { mutableStateOf(emptyMap()) }
        val isAnalyticsOverlayEnabled by pageViewModel?.isAnalyticsOverlayEnabled?.collectAsState(false) ?: remember { mutableStateOf(false) }
        val pageMetrics = if (isAnalyticsOverlayEnabled) pageMetricsMap else emptyMap()

        val isEditPreviewActive by pageViewModel?.isEditPreviewActive?.collectAsState(false) ?: remember { mutableStateOf(false) }
        val resolvedPage by pageViewModel?.resolvedPage?.collectAsState(null) ?: remember { mutableStateOf(null) }
        val pageToShow = if (isEditPreviewActive) (resolvedPage ?: item) else item

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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = if (isLandscape) dimensions.paddingMedium else dimensions.paddingLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GridEditorHeader(
                        item = item,
                        isEditPreviewActive = isEditPreviewActive,
                        onSummaryClick = { showLayoutSettingsSheet = true }
                    )

                    val effectiveScanPattern = pageToShow.scanPattern ?: bookDefaultScanPattern
                    val isRowByRow = effectiveScanPattern == "row_by_row" || effectiveScanPattern == "row_column"

                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val sizeInfo = calculateGridSize(
                            maxWidth = maxWidth,
                            maxHeight = maxHeight,
                            rows = pageToShow.rows,
                            cols = pageToShow.columns,
                            isRowByRow = isRowByRow,
                            horizontalPadding = horizontalPadding,
                            isTablet = dimensions.isTablet,
                            dimensions = dimensions
                        )

                        val gridSpacingPx = with(LocalDensity.current) { dimensions.gridSpacing.toPx() }
                        GridEditorGrid(
                            pageToShow = pageToShow,
                            gridState = gridState,
                            sizeInfo = sizeInfo,
                            dimensions = dimensions,
                            density = density,
                            gridSpacingPx = gridSpacingPx,
                            availablePages = availablePages,
                            pageMetrics = pageMetrics,
                            isEditPreviewActive = isEditPreviewActive,
                            isRowByRow = isRowByRow,
                            horizontalPadding = horizontalPadding,
                            maxHeight = maxHeight,
                            actions = actions,
                            buttonReorderState = buttonReorderState,
                            rowReorderState = rowReorderState,
                            onEditRow = { editingRowIndex = it; showRowEditDialog = true },
                            onEditButton = { index ->
                                selectedButtonIndex = index
                                showDialog = true
                            }
                        )
                    }
                }

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
                    defaultStartPageId = pageViewModel?.settingsRepository?.defaultStartPageId,
                    onDismiss = { editingTemplateId = null },
                    onSave = { newConfig ->
                        pageViewModel?.updateButtonTemplate(template.copy(name = newConfig.label, buttonConfig = newConfig))
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
                    onNavigateToPage = { targetPageId ->
                        onEditPage?.invoke(targetPageId, null)
                    },
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
                    onSaveAsTemplate = { }
                )
            }

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
