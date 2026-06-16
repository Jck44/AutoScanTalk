package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.ActionHistoryDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupRestoreProgressDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.search.SettingsSearchBar
import com.andreas_kratzer.ghosttalk.feature.settings.ui.search.SettingsSearchResults
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onBookDeleted: () -> Unit = { onNavigateBack?.invoke() },
    onNavigateToStart: () -> Unit = {},
    isGlobal: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToVocalTraining: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val containerWidthDp = with(density) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isLargeScreen = containerWidthDp >= 720.dp

    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val editingProfileId by viewModel.editingProfileId.collectAsState()
    val editingProfileName by viewModel.editingProfileName.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val showDiscardChangesDialogState = remember { mutableStateOf(false) }

    val authIntent by viewModel.authIntentFlow.collectAsState(null)
    val signInError by viewModel.signInErrorMessage.collectAsState()

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> viewModel.refresh() }

    LaunchedEffect(authIntent) {
        authIntent?.let { authLauncher.launch(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.autoSyncProfilesOnOpen()
    }

    LaunchedEffect(signInError) {
        signInError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val coroutineScope = rememberCoroutineScope()

    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            handleLocalImport(context, it, viewModel, coroutineScope, isGlobal)
        }
    }

    val localExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            handleLocalExport(context, it, viewModel, coroutineScope)
        }
    }


    val defaultSafFolderDisplayName = stringResource(R.string.settings_selected_saf_folder)
    val safImportErrorTemplate = stringResource(R.string.settings_error_import)

    val safImportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)
                val displayName = doc?.name ?: it.lastPathSegment ?: defaultSafFolderDisplayName
                viewModel.fetchAvailableBackupsFromSaf(it.toString(), displayName)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, safImportErrorTemplate.format(e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(isLargeScreen) {
        if (isLargeScreen && selectedSection == null) {
            selectedSection = SettingsSection.GENERAL
        }
    }

    // Track previous editingProfileId to detect transitions synchronously (same frame)
    // LaunchedEffect runs *after* the frame, causing a 1-frame flash of stale content.
    val previousEditingIdState = remember { mutableStateOf(editingProfileId) }
    if (previousEditingIdState.value != editingProfileId) {
        previousEditingIdState.value = editingProfileId

        if (editingProfileId != null) {
            // Entering edit mode
            selectedSection = if (isLargeScreen) SettingsSection.GENERAL else null
        } else {
            // Leaving edit mode → navigate back to profile list
            selectedSection = SettingsSection.PROFILE
        }
        searchQuery = ""
    }

    val handleBack: () -> Unit = {
        if (editingProfileId != null) {
            if (!isLargeScreen && selectedSection != null) {
                // On mobile edit mode, go back to the edit categories menu
                selectedSection = null
            } else {
                // On tablet or mobile root edit menu, prompt/cancel edit session
                if (hasUnsavedChanges) {
                    showDiscardChangesDialogState.value = true
                } else {
                    viewModel.cancelEditingProfile()
                }
            }
        } else if (isLargeScreen || selectedSection == null) {
            onNavigateBack?.invoke()
        } else {
            selectedSection = null
        }
    }

    if (editingProfileId != null || (!isLargeScreen && selectedSection != null) || onNavigateBack != null) {
        BackHandler {
            handleBack()
        }
    }



    val screenTitle = if (editingProfileId != null) {
        val editingTitle = if (isGlobal) stringResource(R.string.settings_edit_global_profile) else stringResource(R.string.settings_edit_profile)
        if (selectedSection != null) {
            "$editingTitle – " + stringResource(selectedSection!!.getTitleRes())
        } else {
            editingTitle
        }
    } else {
        if (isLargeScreen) {
            if (isGlobal) stringResource(R.string.settings_global_settings) else stringResource(R.string.settings_title_settings)
        } else {
            if (selectedSection != null) {
                stringResource(selectedSection!!.getTitleRes())
            } else {
                if (isGlobal) stringResource(R.string.settings_global_settings) else stringResource(R.string.settings_title_settings)
            }
        }
    }

    val showBackIcon = editingProfileId != null || (!isLargeScreen && selectedSection != null) || onNavigateBack != null
    val backCallback = if (editingProfileId != null || (!isLargeScreen && selectedSection != null)) handleBack else onNavigateBack

    GhostTalkScaffold(
        title = screenTitle,
        onNavigateBack = if (showBackIcon) backCallback else null,
        showTopBar = showTopBar
    ) { paddingValues ->
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                if (editingProfileId != null) {
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                )
                                // Redundant since safeDrawing contains ime, but kept explicitly for robust keyboard interaction
                                .imePadding()
                                .padding(dimensions.paddingMedium),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelEditingProfile() },
                                modifier = Modifier.padding(end = dimensions.paddingSmall)
                            ) {
                                Text(stringResource(CoreR.string.action_cancel))
                            }
                            Button(
                                onClick = { viewModel.saveEditingProfile() }
                            ) {
                                Text(stringResource(CoreR.string.action_save))
                            }
                        }
                    }
                }
            }
        ) { innerScaffoldPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(innerScaffoldPadding)
            ) {
            if (isLargeScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Sidebar
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                    ) {
                        if (editingProfileId != null) {
                            ProfileNameEditCard(
                                name = editingProfileName ?: "",
                                onNameChange = { viewModel.updateEditingProfileName(it) }
                            )
                            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                        }

                        SettingsSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
                        
                        Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                        
                        val visibleSections = if (editingProfileId != null) ProfileEditSections else SettingsSection.entries

                        if (searchQuery.isNotBlank()) {
                            SettingsSearchResults(
                                searchQuery = searchQuery,
                                editingProfileId = editingProfileId,
                                dimensions = dimensions,
                                onResultClick = { section, title ->
                                    selectedSection = section
                                    viewModel.setHighlightedSettingKey(title)
                                    searchQuery = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                            ) {
                                items(visibleSections) { section ->
                                    val isSelected = selectedSection == section
                                    Surface(
                                        onClick = { selectedSection = section },
                                        shape = MaterialTheme.shapes.large,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = stringResource(section.getTitleRes()),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = section.icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))
                        VersionInfo()
                    }

                    VerticalDivider()

                    // Details Pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (editingProfileId != null) {
                            ProfileEditBanner(
                                profileName = editingProfileName ?: stringResource(R.string.settings_draft),
                                modifier = Modifier.padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.screenPaddingVertical)
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedSection?.let { section ->
                                SettingsSubMenu(
                                    section = section,
                                    padding = PaddingValues(0.dp),
                                    dimensions = dimensions,
                                    viewModel = viewModel,
                                    onNavigateBack = { onNavigateBack?.invoke() },
                                    onBookDeleted = onBookDeleted,
                                    onLockClicked = {
                                        viewModel.security.lock()
                                        onNavigateToStart()
                                    },
                                    onLocalExport = { localExportLauncher.launch("GhostTalk_Backup.zip") },
                                    onLocalImport = { localImportLauncher.launch("*/*") },
                                    onSelectSafFolderForImport = { safImportFolderLauncher.launch(null) },
                                    onNavigateToVocalTraining = onNavigateToVocalTraining
                                )
                            }
                        }
                    }
                }
            } else {
                // Mobile (Single Pane)
                if (selectedSection == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.screenPaddingVertical)
                    ) {
                        if (editingProfileId != null) {
                            ProfileEditBanner(
                                profileName = editingProfileName ?: stringResource(R.string.settings_draft)
                            )
                            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                            ProfileNameEditCard(
                                name = editingProfileName ?: "",
                                onNameChange = { viewModel.updateEditingProfileName(it) }
                            )
                            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                        }

                        SettingsSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
                        
                        Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                        if (searchQuery.isNotBlank()) {
                            SettingsSearchResults(
                                searchQuery = searchQuery,
                                editingProfileId = editingProfileId,
                                dimensions = dimensions,
                                onResultClick = { section, title ->
                                    selectedSection = section
                                    viewModel.setHighlightedSettingKey(title)
                                    searchQuery = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            val visibleSections = if (editingProfileId != null) ProfileEditSections else SettingsSection.entries
                            SettingsMainMenu(
                                padding = PaddingValues(0.dp),
                                dimensions = dimensions,
                                sections = visibleSections,
                                onSectionSelected = { selectedSection = it }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        if (editingProfileId != null) {
                            ProfileEditBanner(
                                profileName = editingProfileName ?: stringResource(R.string.settings_draft),
                                modifier = Modifier.padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.screenPaddingVertical)
                            )
                        }
                        SettingsSubMenu(
                            section = selectedSection!!,
                            padding = PaddingValues(0.dp),
                            dimensions = dimensions,
                            viewModel = viewModel,
                            onNavigateBack = { onNavigateBack?.invoke() },
                            onBookDeleted = onBookDeleted,
                            onLockClicked = {
                                viewModel.security.lock()
                                onNavigateToStart()
                            },
                            onLocalExport = { localExportLauncher.launch("GhostTalk_Backup.zip") },
                            onLocalImport = { localImportLauncher.launch("*/*") },
                            onSelectSafFolderForImport = { safImportFolderLauncher.launch(null) },
                            onNavigateToVocalTraining = onNavigateToVocalTraining
                        )
                    }
                }
            }
        }
    }
    }

    val showActionHistory by viewModel.showActionHistoryDialog.collectAsState()

    if (showDiscardChangesDialogState.value) {
        GhostTalkDialog(
            title = stringResource(R.string.settings_dialog_discard_changes_title),
            onDismiss = { showDiscardChangesDialogState.value = false },
            confirmText = stringResource(R.string.settings_dialog_discard_changes_confirm),
            onConfirm = {
                showDiscardChangesDialogState.value = false
                viewModel.cancelEditingProfile()
            },
            dismissText = stringResource(CoreR.string.action_cancel),
            isDestructive = true
        ) {
            Text(stringResource(R.string.settings_dialog_discard_changes_message))
        }
    }

    if (showActionHistory) {
        ActionHistoryDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowActionHistoryDialog(false) }
        )
    }



    val showPrefetch by viewModel.showPrefetchDialog.collectAsState()
    if (showPrefetch) {
        com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.TtsPrefetchDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowPrefetchDialog(false) }
        )
    }

    val isBackupRestoreRunning by viewModel.isBackupRestoreRunning.collectAsState()
    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsState()
    val backupRestoreStatus by viewModel.backupRestoreStatus.collectAsState()

    if (isBackupRestoreRunning) {
        BackupRestoreProgressDialog(
            progress = backupRestoreProgress,
            status = backupRestoreStatus
        )
    }
}
