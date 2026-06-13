package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.pages.IncomingReferencesDialog
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    initialButtonId: String? = null,
    pageViewModel: PageViewModel,
    gridEditorViewModel: GridEditorViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    pageSplitViewModel: PageSplitViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    onNavigateBack: () -> Unit,
    onEditPage: ((String, String?) -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null,
    onOpenStructureEditor: ((String, Boolean) -> Unit)? = null
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = allPages.find { it.id == pageId }

    LaunchedEffect(page) {
        if (page != null) {
            pageViewModel.loadPage(page)
        }
    }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    var localName by remember(page.name) { mutableStateOf(page.name) }
    var showIncomingLinksDialog by remember { mutableStateOf(false) }
    var incomingUsages by remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    val context = LocalContext.current
    
    // Layout & Page Split Dialog States
    val showLayoutAssistantDialog = remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val handleNavigateBack = {
        if (localName.isNotBlank()) {
            onNavigateBack()
        }
    }

    BackHandler {
        handleNavigateBack()
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val resolvedPage by pageViewModel.resolvedPage.collectAsState()
    LaunchedEffect(resolvedPage) {
        gridEditorViewModel.setResolvedPage(resolvedPage)
    }

    LaunchedEffect(localName) {
        if (localName != page.name && localName.isNotBlank()) {
            delay(500)
            gridEditorViewModel.updateGridSettings(
                itemId = page.id,
                update = GridSettingsUpdate(name = localName)
            )
        }
    }

    GhostTalkScaffold(
        title = "",
        onNavigateBack = handleNavigateBack,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        titleContent = {
            ValidatedTextField(
                value = localName,
                onValueChange = { localName = it },
                isRequired = true,
                errorMessage = stringResource(R.string.error_page_name_required),
                onFocusLost = {
                    if (it.isNotBlank() && it != page.name) {
                        gridEditorViewModel.updateGridSettings(
                            itemId = page.id,
                            update = GridSettingsUpdate(name = it)
                        )
                    }
                },
                placeholder = { Text(stringResource(R.string.page_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp) // Reduce vertical impact
                    .testTag("page_editor_name_field")
            )
        },
        actions = {
            val isEditPreviewActive by pageViewModel.isEditPreviewActive.collectAsState()
            
            IconButton(
                onClick = { pageViewModel.toggleEditPreviewActive() },
                modifier = Modifier.testTag("page_editor_preview_toggle")
            ) {
                Icon(
                    imageVector = if (isEditPreviewActive) GhostTalkIcons.Visibility else GhostTalkIcons.VisibilityOff,
                    contentDescription = stringResource(R.string.page_editor_preview_toggle),
                    tint = if (isEditPreviewActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        incomingUsages = pageViewModel.getPageUsages(page.id)
                        showIncomingLinksDialog = true
                    }
                },
                modifier = Modifier.testTag("page_editor_incoming_links")
            ) {
                Icon(
                    imageVector = GhostTalkIcons.Link,
                    contentDescription = stringResource(R.string.page_incoming_links_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.testTag("page_editor_overflow_menu_trigger")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Mehr Optionen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_editor_analytics_toggle)) },
                        onClick = {
                            showOverflowMenu = false
                            pageViewModel.toggleAnalyticsOverlay()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = GhostTalkIcons.BarChart,
                                contentDescription = null,
                                tint = if (isAnalyticsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("page_editor_analytics_toggle_menu")
                    )
                    DropdownMenuItem(
                        text = { Text("Layout- & Struktur-Assistent") },
                        onClick = {
                            showOverflowMenu = false
                            showLayoutAssistantDialog.value = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = GhostTalkIcons.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("page_editor_split_wizard_trigger_menu")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.structure_editor_open)) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenStructureEditor?.invoke(pageId, false)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("page_editor_bulk_reorder_trigger_menu")
                    )
                }
            }

            if (onExitEditor != null) {
                IconButton(
                    onClick = onExitEditor,
                    modifier = Modifier.testTag("page_editor_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Editor beenden",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { paddingValues ->
        val templates by pageViewModel.templates.collectAsState()
        
        GridEditorContent(
            item = page,
            actions = gridEditorViewModel,
            availablePages = unfilteredPages,
            templates = templates,
            featureGuard = pageViewModel.featureGuard,
            bookDefaultScanPattern = bookDefaultScanPattern,
            paddingValues = paddingValues,
            onEditPage = onEditPage,
            initialButtonId = initialButtonId
        )

        // Render Layout & Split Dialogs
        if (showLayoutAssistantDialog.value) {
            PageLayoutAssistantDialog(
                page = page,
                pageSplitViewModel = pageSplitViewModel,
                onStartPageSplit = {
                    onOpenStructureEditor?.invoke(page.id, true)
                },
                onStartMagicCleanup = {
                    pageSplitViewModel.magicCleanup(page.id) {
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Magische Bereinigung erfolgreich abgeschlossen!",
                                actionLabel = "Rückgängig",
                                duration = androidx.compose.material3.SnackbarDuration.Long
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                gridEditorViewModel.undo { undoMsg ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(undoMsg)
                                    }
                                }
                            }
                        }
                    }
                },
                onDismiss = { showLayoutAssistantDialog.value = false }
            )
        }

        val magicCleanupProgress by pageSplitViewModel.magicCleanupProgress.collectAsState()

        magicCleanupProgress?.let { progressMessage ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.width(280.dp)
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Magische Bereinigung läuft...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        if (showIncomingLinksDialog) {
            IncomingReferencesDialog(
                pageName = page.name,
                usages = incomingUsages,
                onDismiss = { showIncomingLinksDialog = false },
                onNavigateToUsage = { usage ->
                    showIncomingLinksDialog = false
                    if (usage is UsageLocation.PageUsage) {
                        onEditPage?.invoke(usage.id, null)
                    } else {
                        android.widget.Toast.makeText(context, R.string.page_incoming_links_template_toast, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
