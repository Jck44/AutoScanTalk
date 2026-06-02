package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitOptInDialog
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitManualPromptDialog
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitWizardDialog
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction

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
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = unfilteredPages.find { it.id == pageId }
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
    
    // Page Split Dialog States
    var showOptInDialog by remember { mutableStateOf(false) }
    var showManualPromptDialog by remember { mutableStateOf(false) }
    var showWizardDialog by remember { mutableStateOf(false) }
    var manualPromptText by remember { mutableStateOf("") }
    
    val pageSplitProposal by pageViewModel.pageSplitProposal.collectAsState()
    val isPageSplitLoading by pageViewModel.isPageSplitLoading.collectAsState()
    val scope = rememberCoroutineScope()

    val handleNavigateBack = {
        if (localName.isNotBlank()) {
            onNavigateBack()
        }
    }

    BackHandler {
        handleNavigateBack()
    }

    Scaffold(
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
                    val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()
                    IconButton(
                        onClick = { pageViewModel.toggleAnalyticsOverlay() },
                        modifier = Modifier.testTag("page_editor_analytics_toggle")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.BarChart,
                            contentDescription = stringResource(R.string.page_editor_analytics_toggle),
                            tint = if (isAnalyticsEnabled) {
                                androidx.compose.material3.MaterialTheme.colorScheme.primary
                            } else {
                                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            val accepted = pageViewModel.settingsRepository.hasAcceptedPageSplitOptIn
                            if (accepted) {
                                showWizardDialog = true
                                pageViewModel.generatePageSplitProposal(page.id)
                            } else {
                                showOptInDialog = true
                            }
                        },
                        modifier = Modifier.testTag("page_editor_split_wizard_trigger")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.AutoAwesome,
                            contentDescription = "Seite aufteilen",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
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
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
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

        // Render Page Split Dialogs
        if (showOptInDialog) {
            PageSplitOptInDialog(
                onConfirmCloud = { rememberDecision ->
                    showOptInDialog = false
                    if (rememberDecision) {
                        pageViewModel.settingsRepository.hasAcceptedPageSplitOptIn = true
                    }
                    showWizardDialog = true
                    pageViewModel.generatePageSplitProposal(page.id)
                },
                onConfirmManual = {
                    showOptInDialog = false
                    val defaultStartPageId = pageViewModel.settingsRepository.defaultStartPageId
                    val labels = page.buttonConfigs
                        .filter { !pageViewModel.shouldFilterButtonFromSplit(it, defaultStartPageId, page.id) }
                        .map { it!!.label }
                    manualPromptText = pageViewModel.generatePageSplitPrompt(labels)
                    showManualPromptDialog = true
                },
                onDismiss = { showOptInDialog = false }
            )
        }

        if (showManualPromptDialog) {
            PageSplitManualPromptDialog(
                promptText = manualPromptText,
                onEvaluateResponse = { response ->
                    pageViewModel.parsePageSplitProposal(response)
                    showManualPromptDialog = false
                    showWizardDialog = true
                },
                onDismiss = { showManualPromptDialog = false }
            )
        }

        if (showWizardDialog) {
            val activeButtons = page.buttonConfigs
                .filter { it != null && it.isActive && it.label.isNotBlank() }
                .map { it!! }

            PageSplitWizardDialog(
                proposal = pageSplitProposal,
                allAvailableButtons = activeButtons,
                isLoading = isPageSplitLoading,
                onConfirm = { updatedProposal ->
                    pageViewModel.applyPageSplit(page.id, updatedProposal)
                    showWizardDialog = false
                },
                onDismiss = {
                    showWizardDialog = false
                    pageViewModel.clearPageSplitProposal()
                }
            )
        }
    }
}
