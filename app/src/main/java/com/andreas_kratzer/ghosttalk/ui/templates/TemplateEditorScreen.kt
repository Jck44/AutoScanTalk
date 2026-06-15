package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState(initial = "linear")
    val template = templates.find { it.id == templateId }

    if (template == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.template_loading))
        }
        return
    }

    BackHandler {
        onNavigateBack()
    }

    var localName by remember(template.name) { mutableStateOf(template.name) }
    
    LaunchedEffect(localName) {
        if (localName != template.name) {
            delay(500)
            templateViewModel.updateTemplate(template.copy(name = localName))
        }
    }

    val isMultiSelectModeState = rememberSaveable { mutableStateOf(false) }
    val selectedButtonIndicesState = rememberSaveable { mutableStateOf(emptySet<Int>()) }
    val showMoveDialogState = rememberSaveable { mutableStateOf(false) }
    val showDuplicateDialogState = rememberSaveable { mutableStateOf(false) }
    val showConfirmDeleteDialogState = rememberSaveable { mutableStateOf(false) }

    var isMultiSelectMode by isMultiSelectModeState
    var selectedButtonIndices by selectedButtonIndicesState

    if (isMultiSelectMode) {
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.bulk_action_selected_count, selectedButtonIndices.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedButtonIndices = emptySet()
                        }) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = stringResource(R.string.bulk_action_cancel)
                            )
                        }
                    },
                    actions = {
                        if (selectedButtonIndices.isNotEmpty()) {
                            androidx.compose.material3.IconButton(onClick = { showMoveDialogState.value = true }) {
                                androidx.compose.material3.Icon(
                                    imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.ArrowForward,
                                    contentDescription = stringResource(R.string.bulk_action_move)
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { showDuplicateDialogState.value = true }) {
                                androidx.compose.material3.Icon(
                                    imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Copy,
                                    contentDescription = stringResource(R.string.bulk_action_copy)
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { showConfirmDeleteDialogState.value = true }) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.bulk_action_delete)
                                )
                            }
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { paddingValues ->
            GridEditorContent(
                item = template,
                actions = templateViewModel,
                availablePages = unfilteredPages,
                templates = templates, // Local templates from templateViewModel
                featureGuard = pageViewModel.featureGuard,
                bookDefaultScanPattern = bookDefaultScanPattern,
                paddingValues = paddingValues,
                onEditPage = { pageId: String, _ ->
                    scope.launch {
                        val target = pageViewModel.getPageById(pageId)
                        if (target != null) {
                            onNavigateBack() // Close Template Editor
                            pageViewModel.loadPage(target)
                        }
                    }
                },
                isMultiSelectModeState = isMultiSelectModeState,
                selectedButtonIndicesState = selectedButtonIndicesState,
                showMoveDialogState = showMoveDialogState,
                showDuplicateDialogState = showDuplicateDialogState,
                showConfirmDeleteDialogState = showConfirmDeleteDialogState
            )
        }
    } else {
        GhostTalkScaffold(
        title = "",
        onNavigateBack = onNavigateBack,
        titleContent = {
            OutlinedTextField(
                value = localName,
                onValueChange = { localName = it },
                label = { Text(stringResource(R.string.template_name_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    ) { paddingValues ->
        GridEditorContent(
            item = template,
            actions = templateViewModel,
            availablePages = unfilteredPages,
            templates = templates, // Local templates from templateViewModel
            featureGuard = pageViewModel.featureGuard,
            bookDefaultScanPattern = bookDefaultScanPattern,
            paddingValues = paddingValues,
            onEditPage = { pageId: String, _ ->
                scope.launch {
                    val target = pageViewModel.getPageById(pageId)
                    if (target != null) {
                        onNavigateBack() // Close Template Editor
                        pageViewModel.loadPage(target)
                    }
                }
            },
            isMultiSelectModeState = isMultiSelectModeState,
                selectedButtonIndicesState = selectedButtonIndicesState,
                showMoveDialogState = showMoveDialogState,
                showDuplicateDialogState = showDuplicateDialogState,
                showConfirmDeleteDialogState = showConfirmDeleteDialogState
            )
        }
    }
}
