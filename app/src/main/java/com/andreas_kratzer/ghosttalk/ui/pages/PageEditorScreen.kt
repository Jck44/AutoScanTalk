package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    initialButtonId: String? = null,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: ((String, String?) -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = allPages.find { it.id == pageId }
    val dimensions = LocalDimensions.current

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
    
    // Layout & Page Split Dialog States
    val showLayoutAssistantDialog = remember { mutableStateOf(false) }
    val showOptInDialog = remember { mutableStateOf(false) }
    val showManualPromptDialog = remember { mutableStateOf(false) }
    val showWizardDialog = remember { mutableStateOf(false) }
    var manualPromptText by remember { mutableStateOf("") }
    
    val pageSplitProposal by pageViewModel.pageSplitProposal.collectAsState()
    val isPageSplitLoading by pageViewModel.isPageSplitLoading.collectAsState()

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

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { 
                    LaunchedEffect(localName) {
                        if (localName != page.name && localName.isNotBlank()) {
                            delay(500)
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                update = GridSettingsUpdate(name = localName)
                            )
                        }
                    }

                    ValidatedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        isRequired = true,
                        errorMessage = stringResource(R.string.error_page_name_required),
                        onFocusLost = {
                            if (it.isNotBlank() && it != page.name) {
                                pageViewModel.updatePageSettings(
                                    pageId = page.id,
                                    update = GridSettingsUpdate(name = it)
                                )
                            }
                        },
                        placeholder = { Text(stringResource(R.string.page_name_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = dimensions.paddingLarge)
                            .padding(vertical = 4.dp) // Reduce vertical impact
                            .testTag("page_editor_name_field")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreR.string.back_button_content_description))
                    }
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

                    val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()
                    IconButton(
                        onClick = { pageViewModel.toggleAnalyticsOverlay() },
                        modifier = Modifier.testTag("page_editor_analytics_toggle")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.BarChart,
                            contentDescription = stringResource(R.string.page_editor_analytics_toggle),
                            tint = if (isAnalyticsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            showLayoutAssistantDialog.value = true
                        },
                        modifier = Modifier.testTag("page_editor_split_wizard_trigger")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.AutoAwesome,
                            contentDescription = "Layout- & Struktur-Assistent",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            )
        }
    ) { paddingValues ->
        val templates by pageViewModel.templates.collectAsState()
        
        GridEditorContent(
            item = page,
            actions = pageViewModel,
            availablePages = unfilteredPages,
            templates = templates,
            featureGuard = pageViewModel.featureGuard,
            bookDefaultScanPattern = bookDefaultScanPattern,
            paddingValues = paddingValues,
            onEditPage = onEditPage,
            initialButtonId = initialButtonId,
            pageViewModel = pageViewModel
        )

        // Render Layout & Split Dialogs
        if (showLayoutAssistantDialog.value) {
            PageLayoutAssistantDialog(
                page = page,
                pageViewModel = pageViewModel,
                onStartPageSplit = {
                    val accepted = pageViewModel.settingsRepository.hasAcceptedPageSplitOptIn
                    if (accepted) {
                        showWizardDialog.value = true
                        pageViewModel.generatePageSplitProposal(page.id)
                    } else {
                        showOptInDialog.value = true
                    }
                },
                onStartMagicCleanup = {
                    pageViewModel.magicCleanup(page.id) {
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Magische Bereinigung erfolgreich abgeschlossen!",
                                actionLabel = "Rückgängig",
                                duration = androidx.compose.material3.SnackbarDuration.Long
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                pageViewModel.undo { undoMsg ->
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

        if (showOptInDialog.value) {
            PageSplitOptInDialog(
                onConfirmCloud = { rememberDecision ->
                    showOptInDialog.value = false
                    if (rememberDecision) {
                        pageViewModel.settingsRepository.hasAcceptedPageSplitOptIn = true
                    }
                    showWizardDialog.value = true
                    pageViewModel.generatePageSplitProposal(page.id)
                },
                onConfirmManual = {
                    showOptInDialog.value = false
                    val defaultStartPageId = pageViewModel.settingsRepository.defaultStartPageId
                    val labels = page.buttonConfigs
                        .filter { !pageViewModel.shouldFilterButtonFromSplit(it, defaultStartPageId, page.id) }
                        .map { it!!.label }
                    manualPromptText = pageViewModel.generatePageSplitPrompt(labels)
                    showManualPromptDialog.value = true
                },
                onDismiss = { showOptInDialog.value = false }
            )
        }

        if (showManualPromptDialog.value) {
            PageSplitManualPromptDialog(
                promptText = manualPromptText,
                onEvaluateResponse = { response ->
                    pageViewModel.parsePageSplitProposal(response)
                    showManualPromptDialog.value = false
                    showWizardDialog.value = true
                },
                onDismiss = { showManualPromptDialog.value = false }
            )
        }

        if (showWizardDialog.value) {
            val activeButtons = page.buttonConfigs
                .filter { it != null && it.isActive && it.label.isNotBlank() }
                .map { it!! }

            PageSplitWizardDialog(
                proposal = pageSplitProposal,
                allAvailableButtons = activeButtons,
                isLoading = isPageSplitLoading,
                onConfirm = { updatedProposal ->
                    pageViewModel.applyPageSplit(page.id, updatedProposal)
                    showWizardDialog.value = false
                },
                onDismiss = {
                    showWizardDialog.value = false
                    pageViewModel.clearPageSplitProposal()
                }
            )
        }

        val magicCleanupProgress by pageViewModel.magicCleanupProgress.collectAsState()

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
    }
}
