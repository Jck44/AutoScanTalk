package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.ActionHistoryDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupRestoreProgressDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.UsageStatisticsDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.UserModeSessionsDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.CallSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.CloudSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.ExperimentalSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.GenAiSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.GeneralSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.MaintenanceSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.PermissionsSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.ScanningSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.SecuritySettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.SmartHomeSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.TestSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.VoiceSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

enum class SettingsSection(private val titleRes: Int, val icon: ImageVector, val isGlobal: Boolean, val isScoped: Boolean) {
    GENERAL(R.string.settings_category_general, Icons.Default.Settings, isGlobal = true, isScoped = true),
    VOICE(R.string.settings_category_voice, GhostTalkIcons.RecordVoiceOver, isGlobal = false, isScoped = true),
    SCANNING(R.string.settings_category_scanning, GhostTalkIcons.SettingsAccessibility, isGlobal = false, isScoped = true),
    SECURITY(R.string.settings_category_security, GhostTalkIcons.Security, isGlobal = true, isScoped = false),
    TELEPHONY(R.string.settings_category_call, Icons.Default.Phone, isGlobal = true, isScoped = false),
    CLOUD(R.string.settings_category_cloud, GhostTalkIcons.Cloud, isGlobal = true, isScoped = true),
    SMART_HOME(R.string.settings_category_smart_home, Icons.Default.Home, isGlobal = true, isScoped = false),
    GEMINI(R.string.settings_category_gemini, GhostTalkIcons.AutoAwesome, isGlobal = false, isScoped = true),
    NOTIFICATIONS(R.string.settings_category_notifications, GhostTalkIcons.Notifications, isGlobal = true, isScoped = false),
    ADVANCED(R.string.settings_category_advanced, GhostTalkIcons.Science, isGlobal = true, isScoped = true);

    fun getTitleRes(isGlobal: Boolean): Int {
        return if (this == CLOUD && !isGlobal) {
            R.string.settings_category_cloud_book
        } else {
            titleRes
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onBookDeleted: () -> Unit = onNavigateBack,
    onNavigateToStart: () -> Unit = {},
    isGlobal: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    
    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

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

    BackHandler {
        if (selectedSection == null) {
            onNavigateBack()
        } else {
            selectedSection = null
        }
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                selectedSection = selectedSection,
                isGlobal = isGlobal,
                onBack = { if (selectedSection == null) onNavigateBack() else selectedSection = null }
            )
        }
    ) { padding ->
        if (selectedSection == null) {
            SettingsMainMenu(
                isGlobal = isGlobal,
                padding = padding,
                dimensions = dimensions,
                onSectionSelected = { selectedSection = it }
            )
        } else {
            SettingsSubMenu(
                section = selectedSection!!,
                padding = padding,
                dimensions = dimensions,
                isGlobal = isGlobal,
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onBookDeleted = onBookDeleted,
                onLockClicked = {
                    viewModel.lock()
                    onNavigateToStart()
                },
                onLocalExport = { localExportLauncher.launch("GhostTalk_Backup.zip") },
                onLocalImport = { localImportLauncher.launch("*/*") }
            )
        }
    }

    val showActionHistory by viewModel.showActionHistoryDialog.collectAsState()
    val showUsageStats by viewModel.showUsageStatsDialog.collectAsState()
    val showUserModeSessions by viewModel.showUserModeSessionsDialog.collectAsState()

    if (showActionHistory) {
        ActionHistoryDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowActionHistoryDialog(false) }
        )
    }

    if (showUsageStats) {
        UsageStatisticsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowUsageStatsDialog(false) }
        )
    }

    if (showUserModeSessions) {
        UserModeSessionsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowUserModeSessionsDialog(false) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    selectedSection: SettingsSection?,
    isGlobal: Boolean,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (selectedSection == null)
                    stringResource(if (isGlobal) CoreR.string.settings_title_global else CoreR.string.settings_title_book)
                else
                    stringResource(selectedSection.getTitleRes(isGlobal)),
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun SettingsMainMenu(
    isGlobal: Boolean,
    padding: PaddingValues,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    onSectionSelected: (SettingsSection) -> Unit
) {
    val sections = SettingsSection.entries.filter { if (isGlobal) it.isGlobal else it.isScoped }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        contentPadding = PaddingValues(bottom = dimensions.paddingDoubleExtraLarge)
    ) {
        items(sections) { section ->
            Surface(
                onClick = { onSectionSelected(section) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_${section.name}")
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(section.getTitleRes(isGlobal)),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = GhostTalkIcons.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge))
                VersionInfo()
            }
        }
    }
}

@Composable
private fun SettingsSubMenu(
    section: SettingsSection,
    padding: PaddingValues,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    isGlobal: Boolean,
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onBookDeleted: () -> Unit,
    onLockClicked: () -> Unit,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
    ) {
        SubmenuContent(
            section,
            viewModel,
            isGlobal = isGlobal,
            onNavigateBack = onNavigateBack,
            onBookDeleted = onBookDeleted,
            onLockClicked = onLockClicked,
            onLocalExport = onLocalExport,
            onLocalImport = onLocalImport
        )
        Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge * 2))
    }
}

private fun handleLocalImport(
    context: android.content.Context,
    uri: android.net.Uri,
    viewModel: SettingsViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isGlobal: Boolean
) {
    coroutineScope.launch {
        try {
            val fileName = uri.path?.lowercase() ?: ""
            val isZip = fileName.endsWith(".zip") || context.contentResolver.getType(uri) == "application/zip"

            if (isZip) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (isGlobal) {
                        viewModel.importGlobalManualBackupZip(
                            inputStream = inputStream,
                            onSuccess = { _ ->
                                Toast.makeText(context, "Buch erfolgreich importiert.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        viewModel.importLocalBackupZip(
                            inputStream = inputStream,
                            onSuccess = {
                                Toast.makeText(context, context.getString(CoreR.string.page_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            } else {
                // Legacy JSON import
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonContent = reader.readText()
                    if (isGlobal) {
                        viewModel.importGlobalManualBackup(
                            json = jsonContent,
                            onSuccess = { _ ->
                                Toast.makeText(context, "Buch erfolgreich importiert.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        viewModel.importLocalBackup(
                            json = jsonContent,
                            onSuccess = {
                                Toast.makeText(context, context.getString(CoreR.string.page_import_success), Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Fehler beim Import: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun handleLocalExport(
    context: android.content.Context,
    uri: android.net.Uri,
    viewModel: SettingsViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    coroutineScope.launch {
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    viewModel.exportLocalBackupZip(outputStream)
                }
            }
            Toast.makeText(context, context.getString(CoreR.string.page_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Fehler beim Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun SubmenuContent(
    section: SettingsSection, 
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onNavigateBack: () -> Unit = {},
    onBookDeleted: () -> Unit = {},
    onLockClicked: () -> Unit = {},
    onLocalExport: () -> Unit = {},
    onLocalImport: () -> Unit = {}
) {
    when (section) {
        SettingsSection.GENERAL -> {
            GeneralSettingsSection(viewModel, isGlobal = isGlobal, onNavigateBack = onNavigateBack, onBookDeleted = onBookDeleted)
        }
        SettingsSection.VOICE -> {
            VoiceSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.SCANNING -> {
            ScanningSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.SECURITY -> {
            val pin by viewModel.securityPin.collectAsState(null)
            val timeout by viewModel.securityPinTimeoutMinutes.collectAsState(30L)
            val reqDeletion by viewModel.isPinRequiredForDeletion.collectAsState(false)
            val biometricEnabled by viewModel.isBiometricEnabled.collectAsState(false)
            val reqEdit by viewModel.isSecurityRequiredForEdit.collectAsState(false)
            val reqSettings by viewModel.isSecurityRequiredForSettings.collectAsState(false)
            
            SecuritySettingsSection(
                securityPin = pin,
                onSecurityPinChange = viewModel::setSecurityPin,
                onClearSecurityPin = viewModel::clearSecurityPin,
                securityPinTimeoutMinutes = timeout,
                onSecurityPinTimeoutChange = viewModel::setSecurityPinTimeoutMinutes,
                isPinRequiredForDeletion = reqDeletion,
                onPinRequiredForDeletionChange = viewModel::setPinRequiredForDeletion,
                isBiometricEnabled = biometricEnabled,
                onBiometricEnabledChange = viewModel::setBiometricEnabled,
                isSecurityRequiredForEdit = reqEdit,
                onSecurityRequiredForEditChange = viewModel::setSecurityRequiredForEdit,
                isSecurityRequiredForSettings = reqSettings,
                onSecurityRequiredForSettingsChange = viewModel::setSecurityRequiredForSettings,
                onLockClicked = onLockClicked,
                isPinRequired = !pin.isNullOrEmpty(),
                onPinRequiredChange = { /* Handled within SecuritySettingsSection via onClearSecurityPin and onSecurityPinChange */ },
                securityManager = viewModel.securityManager,
                isBiometricSupported = viewModel.isBiometricSupported
            )
        }
        SettingsSection.CLOUD -> {
            CloudSettingsSection(
                viewModel = viewModel,
                isGlobal = isGlobal,
                onLocalExport = onLocalExport,
                onLocalImport = onLocalImport
            )
        }
        SettingsSection.SMART_HOME -> {
            SmartHomeSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.GEMINI -> {
            GenAiSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.NOTIFICATIONS -> {
            PermissionsSettingsSection(viewModel)
        }
        SettingsSection.TELEPHONY -> {
            CallSettingsSection(viewModel)
        }
        SettingsSection.ADVANCED -> {
            if (isGlobal) {
                ExperimentalSettingsSection(viewModel) // Weather timeout is here and global
                MaintenanceSection(
                    viewModel = viewModel,
                    isGlobal = true,
                    onLocalExport = {},
                    onLocalImport = onLocalImport
                )
            }
            TestSettingsSection(viewModel, isGlobal = isGlobal)
            if (!isGlobal) {
                MaintenanceSection(
                    viewModel = viewModel,
                    isGlobal = false,
                    onLocalExport = onLocalExport,
                    onLocalImport = onLocalImport
                )
            }
        }
    }
}

@Composable
fun VersionInfo() {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
        "Unknown"
    }
    Text(
        text = "Version: $versionName",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
