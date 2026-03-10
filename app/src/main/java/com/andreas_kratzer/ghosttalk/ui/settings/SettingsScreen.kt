package com.andreas_kratzer.ghosttalk.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.sections.VoiceSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.CloudSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.ExperimentalSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GenAiSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GeneralSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.LanguageSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.MaintenanceSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.NotificationSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.ScanningSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.SecuritySettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.TestSettingsSection
import com.andreas_kratzer.ghosttalk.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.components.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

enum class SettingsSection(val titleRes: Int, val icon: ImageVector, val isGlobal: Boolean, val isScoped: Boolean) {
    GENERAL(R.string.settings_category_general, Icons.Default.Settings, isGlobal = true, isScoped = true),
    VOICE(R.string.settings_category_voice, GhosTTalkIcons.RecordVoiceOver, isGlobal = false, isScoped = true),
    SCANNING(R.string.settings_category_scanning, GhosTTalkIcons.SettingsAccessibility, isGlobal = false, isScoped = true),
    SECURITY(R.string.settings_category_security, GhosTTalkIcons.Security, isGlobal = true, isScoped = false),
    CLOUD(R.string.settings_category_cloud, GhosTTalkIcons.Cloud, isGlobal = true, isScoped = true),
    GEMINI(R.string.settings_category_gemini, GhosTTalkIcons.AutoAwesome, isGlobal = false, isScoped = true),
    NOTIFICATIONS(R.string.settings_category_notifications, GhosTTalkIcons.Notifications, isGlobal = true, isScoped = false),
    ADVANCED(R.string.settings_category_advanced, GhosTTalkIcons.Science, isGlobal = true, isScoped = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
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
            handleLocalImport(context, it, viewModel, coroutineScope)
        }
    }

    val localExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
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
                onLockClicked = {
                    viewModel.lock()
                    onNavigateToStart()
                },
                onLocalExport = { localExportLauncher.launch("GhosTTalk_Backup.json") },
                onLocalImport = { localImportLauncher.launch("application/json") }
            )
        }
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
                    stringResource(if (isGlobal) R.string.settings_title_global else R.string.settings_title_book)
                else
                    stringResource(selectedSection.titleRes),
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun SettingsMainMenu(
    isGlobal: Boolean,
    padding: PaddingValues,
    dimensions: com.andreas_kratzer.ghosttalk.ui.theme.Dimensions,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(section.titleRes),
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
                            imageVector = GhosTTalkIcons.ArrowForward,
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
    dimensions: com.andreas_kratzer.ghosttalk.ui.theme.Dimensions,
    isGlobal: Boolean,
    viewModel: SettingsViewModel,
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
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.readText()
            coroutineScope.launch {
                viewModel.importLocalBackup(
                    json = jsonContent,
                    onSuccess = {
                        Toast.makeText(context, context.getString(R.string.page_import_success), Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Fehler beim Import: ${e.message}", Toast.LENGTH_LONG).show()
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
            val jsonContent = viewModel.exportLocalBackup()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val writer = OutputStreamWriter(outputStream)
                    writer.write(jsonContent)
                    writer.close()
                }
            }
            Toast.makeText(context, context.getString(R.string.page_export_success), Toast.LENGTH_SHORT).show()
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
    onLockClicked: () -> Unit = {},
    onLocalExport: () -> Unit = {},
    onLocalImport: () -> Unit = {}
) {
    when (section) {
        SettingsSection.GENERAL -> {
            if (isGlobal) {
                LanguageSettingsSection(viewModel)
            }
            GeneralSettingsSection(viewModel, isGlobal = isGlobal)
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
                securityManager = viewModel.securityManager
            )
        }
        SettingsSection.CLOUD -> {
            CloudSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.GEMINI -> {
            GenAiSettingsSection(viewModel, isGlobal = isGlobal)
        }
        SettingsSection.NOTIFICATIONS -> {
            NotificationSettingsSection(viewModel)
        }
        SettingsSection.ADVANCED -> {
            if (isGlobal) {
                ExperimentalSettingsSection(viewModel) // Weather timeout is here and global
            }
            TestSettingsSection(viewModel, isGlobal = isGlobal)
            if (!isGlobal) {
                MaintenanceSection(
                    viewModel = viewModel,
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

