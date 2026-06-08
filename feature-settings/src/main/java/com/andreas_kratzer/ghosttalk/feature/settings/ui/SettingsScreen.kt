package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.ActionHistoryDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupRestoreProgressDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.UsageStatisticsDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.BookSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.CallSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.CloudSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.ExperimentalSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.GenAiSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.GeneralSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.MaintenanceSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.PermissionsSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.ProfileSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.ScanningSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.SecuritySettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.SmartHomeSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.TestSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.VocalSwitchSettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.VoiceSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

enum class SettingsSection(private val titleRes: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_category_general, Icons.Default.Settings),
    PROFILE(R.string.settings_category_profile, Icons.Default.Person),
    MANAGE_BOOK(R.string.settings_category_manage_book, GhostTalkIcons.Book),
    VOICE(R.string.settings_category_voice, Icons.Default.PlayArrow),
    AUDIO_HARDWARE(R.string.settings_category_audio_hardware, GhostTalkIcons.VolumeUp),
    SCANNING(R.string.settings_category_scanning, GhostTalkIcons.SwitchAccessShortcut),
    VOCAL_SWITCH(R.string.settings_category_vocal_switch, GhostTalkIcons.RecordVoiceOver),
    PERMISSIONS(R.string.settings_category_notifications, GhostTalkIcons.Notifications),
    SECURITY(R.string.settings_category_security, GhostTalkIcons.Security),
    AI(R.string.settings_category_gemini, GhostTalkIcons.AutoAwesome),
    CALLS(R.string.settings_category_call, Icons.Default.Phone),
    SMART_INTEGRATION(R.string.settings_category_smart_home, Icons.Default.Home),
    CLOUD_SYNC(R.string.settings_category_cloud, GhostTalkIcons.Cloud),
    MAINTENANCE(R.string.settings_category_maintenance, GhostTalkIcons.Science);

    fun getTitleRes(): Int {
        return titleRes
    }
}

data class SettingsSearchItem(
    val title: String,
    val description: String,
    val section: SettingsSection
)

@Composable
fun getSearchableItems(): List<SettingsSearchItem> {
    return listOf(
        // GENERAL
        SettingsSearchItem(stringResource(R.string.settings_app_language), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_theme_mode), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_startup_behavior), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_force_soft_keyboard), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_persist_logs), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_screen_behavior), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem("Betreuer-Tablet (Caregiver-Modus)", stringResource(R.string.settings_category_general), SettingsSection.GENERAL),

        // MANAGE BOOK
        SettingsSearchItem(stringResource(R.string.book_name_label), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),
        SettingsSearchItem(stringResource(R.string.settings_start_page), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),
        SettingsSearchItem(stringResource(R.string.book_delete_description), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),

        // VOICE
        SettingsSearchItem(stringResource(R.string.settings_tts_engine), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_elevenlabs_model), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.elevenlabs_stability), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.elevenlabs_similarity_boost), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_tts_language), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_select_voice), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_tts_playback_speed), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),

        // AUDIO HARDWARE
        SettingsSearchItem(stringResource(R.string.settings_audio_tts), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_audio_cues), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_recording_source), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_block_volume_keys), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_speaker_volume_label), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_headphone_volume_label), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_bluetooth_delay), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),

        // SCANNING
        SettingsSearchItem(stringResource(R.string.settings_scan_delay), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_late_click_threshold), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_holding_time), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_scan_pattern), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_auto_scan), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_restart_scan), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_limit_scan_cycles), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_scan_cycle_limit), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem("Statische Zeile über jeder Seite anzeigen", stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_switch_key), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),

        // VOCAL SWITCH
        SettingsSearchItem(stringResource(R.string.settings_category_vocal_switch), stringResource(R.string.settings_category_vocal_switch), SettingsSection.VOCAL_SWITCH),
        SettingsSearchItem("Vocal Switch Training / Stimme trainieren", stringResource(R.string.settings_category_vocal_switch), SettingsSection.VOCAL_SWITCH),

        // SECURITY
        SettingsSearchItem(stringResource(R.string.settings_security_set_pin_title), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_pin_timeout), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_pin_required_for_deletion), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_biometric_enabled), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_edit), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_settings), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_analytics), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),

        // AI
        SettingsSearchItem(stringResource(R.string.settings_gemini_enable), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_gemini_api_key), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem("Antwort-Timeout", stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_gemini_redo_prediction), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_smart_prediction_enable), stringResource(R.string.settings_category_gemini), SettingsSection.AI),

        // CALLS
        SettingsSearchItem(stringResource(R.string.settings_max_call_duration), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_duration_feedback_interval), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_intro_outgoing), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_intro_incoming), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_filter_not_in_contacts), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_auto_enable_speakerphone), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem("Telefonanrufe simulieren", stringResource(R.string.settings_category_call), SettingsSection.CALLS),

        // SMART_INTEGRATION
        SettingsSearchItem(stringResource(R.string.settings_hue_bridge_ip), stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),
        SettingsSearchItem("Philips Hue Bridge Verbindung", stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),
        SettingsSearchItem("Spotify Verbindung", stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),

        // PROFILE
        SettingsSearchItem(stringResource(R.string.settings_category_profile), stringResource(R.string.settings_category_profile), SettingsSection.PROFILE),

        // CLOUD_SYNC
        SettingsSearchItem(stringResource(R.string.settings_cloud_sync_enabled), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_sync_drive_location), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_cloud_sync_interval), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_category_local_backup), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),

        // PERMISSIONS
        SettingsSearchItem(stringResource(R.string.settings_category_notifications), stringResource(R.string.settings_category_notifications), SettingsSection.PERMISSIONS),

        // MAINTENANCE
        SettingsSearchItem(stringResource(R.string.settings_category_experimental), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),
        SettingsSearchItem(stringResource(R.string.settings_category_button_history), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),
        SettingsSearchItem(stringResource(R.string.settings_category_cloud_import), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE)
    )
}

@Composable
fun Modifier.highlightSetting(label: String, highlightedKey: String?): Modifier {
    val isHighlighted = label == highlightedKey
    val alpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.6f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    return this.background(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        shape = MaterialTheme.shapes.medium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onBookDeleted: () -> Unit = onNavigateBack,
    onNavigateToStart: () -> Unit = {},
    isGlobal: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToGlobalSettings: () -> Unit = {},
    onNavigateToVocalTraining: () -> Unit = {}
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 720

    var selectedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

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

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)
                val displayName = doc?.name ?: it.lastPathSegment ?: "Ausgewählter SAF-Ordner"
                viewModel.selectLocalFolderSaf(it.toString(), displayName)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Fehler beim Zuweisen der Berechtigungen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val safImportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)
                val displayName = doc?.name ?: it.lastPathSegment ?: "Ausgewählter SAF-Ordner"
                viewModel.fetchAvailableBackupsFromSaf(it.toString(), displayName)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Fehler beim Importieren: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(isLargeScreen) {
        if (isLargeScreen && selectedSection == null) {
            selectedSection = SettingsSection.GENERAL
        }
    }

    val handleBack = {
        if (isLargeScreen || selectedSection == null) {
            onNavigateBack()
        } else {
            selectedSection = null
        }
    }

    BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                selectedSection = selectedSection,
                isLargeScreen = isLargeScreen,
                onBack = handleBack
            )
        }
    ) { paddingValues ->
        if (isLargeScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Sidebar
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                ) {
                    SettingsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                    
                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                    
                    if (searchQuery.isNotBlank()) {
                        val searchItems = getSearchableItems()
                        val results = searchItems.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (results.isEmpty()) {
                                item {
                                    Text(
                                        text = "Keine Einstellungen gefunden",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(dimensions.paddingMedium)
                                    )
                                }
                            } else {
                                items(results) { result ->
                                    SearchResultItem(
                                        result = result,
                                        onClick = {
                                            selectedSection = result.section
                                            viewModel.setHighlightedSettingKey(result.title)
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                        ) {
                            items(SettingsSection.entries) { section ->
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    selectedSection?.let { section ->
                        SettingsSubMenu(
                            section = section,
                            padding = PaddingValues(0.dp),
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
                            onLocalImport = { localImportLauncher.launch("*/*") },
                            onSelectSafFolder = { safFolderLauncher.launch(null) },
                            onSelectSafFolderForImport = { safImportFolderLauncher.launch(null) },
                            onNavigateToVocalTraining = onNavigateToVocalTraining
                        )
                    }
                }
            }
        } else {
            // Mobile (Single Pane)
            if (selectedSection == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
                ) {
                    SettingsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                    
                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                    if (searchQuery.isNotBlank()) {
                        val searchItems = getSearchableItems()
                        val results = searchItems.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.description.contains(searchQuery, ignoreCase = true)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            if (results.isEmpty()) {
                                item {
                                    Text(
                                        text = "Keine Einstellungen gefunden",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(dimensions.paddingMedium)
                                    )
                                }
                            } else {
                                items(results) { result ->
                                    SearchResultItem(
                                        result = result,
                                        onClick = {
                                            selectedSection = result.section
                                            viewModel.setHighlightedSettingKey(result.title)
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        SettingsMainMenu(
                            padding = PaddingValues(0.dp),
                            dimensions = dimensions,
                            onSectionSelected = { selectedSection = it }
                        )
                    }
                }
            } else {
                SettingsSubMenu(
                    section = selectedSection!!,
                    padding = paddingValues,
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
                    onLocalImport = { localImportLauncher.launch("*/*") },
                    onSelectSafFolder = { safFolderLauncher.launch(null) },
                    onSelectSafFolderForImport = { safImportFolderLauncher.launch(null) },
                    onNavigateToVocalTraining = onNavigateToVocalTraining
                )
            }
        }
    }

    val showActionHistory by viewModel.showActionHistoryDialog.collectAsState()
    val showUsageStats by viewModel.showUsageStatsDialog.collectAsState()

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
    isLargeScreen: Boolean,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (isLargeScreen || selectedSection == null)
                    "Einstellungen"
                else
                    stringResource(selectedSection.getTitleRes()),
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
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.settings_search_label))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun SearchResultItem(
    result: SettingsSearchItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = result.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = result.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    imageVector = result.section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun SettingsMainMenu(
    padding: PaddingValues,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    onSectionSelected: (SettingsSection) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(vertical = dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        contentPadding = PaddingValues(bottom = dimensions.paddingDoubleExtraLarge)
    ) {
        items(SettingsSection.entries) { section ->
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
                            text = stringResource(section.getTitleRes()),
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
    onLocalImport: () -> Unit,
    onSelectSafFolder: () -> Unit,
    onSelectSafFolderForImport: () -> Unit,
    onNavigateToVocalTraining: () -> Unit
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
            onNavigateBack = onNavigateBack,
            onBookDeleted = onBookDeleted,
            onLockClicked = onLockClicked,
            onLocalExport = onLocalExport,
            onLocalImport = onLocalImport,
            onSelectSafFolder = onSelectSafFolder,
            onSelectSafFolderForImport = onSelectSafFolderForImport,
            onNavigateToVocalTraining = onNavigateToVocalTraining
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
    onNavigateBack: () -> Unit = {},
    onBookDeleted: () -> Unit = {},
    onLockClicked: () -> Unit = {},
    onLocalExport: () -> Unit = {},
    onLocalImport: () -> Unit = {},
    onSelectSafFolder: () -> Unit = {},
    onSelectSafFolderForImport: () -> Unit = {},
    onNavigateToVocalTraining: () -> Unit = {}
) {
    when (section) {
        SettingsSection.GENERAL -> {
            GeneralSettingsSection(viewModel, isGlobal = true, onNavigateBack = onNavigateBack, onBookDeleted = onBookDeleted)
            Spacer(modifier = Modifier.height(16.dp))
            GeneralSettingsSection(viewModel, isGlobal = false, onNavigateBack = onNavigateBack, onBookDeleted = onBookDeleted)
        }
        SettingsSection.PROFILE -> {
            ProfileSettingsSection(viewModel)
        }
        SettingsSection.MANAGE_BOOK -> {
            BookSettingsSection(viewModel, onNavigateBack = onNavigateBack, onBookDeleted = onBookDeleted)
        }
        SettingsSection.VOICE -> {
            VoiceSettingsSection(viewModel, isGlobal = false)
        }
        SettingsSection.AUDIO_HARDWARE -> {
            VoiceSettingsSection(viewModel, isGlobal = true)
        }
        SettingsSection.SCANNING -> {
            ScanningSettingsSection(viewModel, isGlobal = false)
        }
        SettingsSection.VOCAL_SWITCH -> {
            VocalSwitchSettingsSection(viewModel, isGlobal = false, onNavigateToVocalTraining = onNavigateToVocalTraining)
        }
        SettingsSection.SECURITY -> {
            val pin by viewModel.securityPin.collectAsState(null)
            val timeout by viewModel.securityPinTimeoutMinutes.collectAsState(30L)
            val reqDeletion by viewModel.isPinRequiredForDeletion.collectAsState(false)
            val biometricEnabled by viewModel.isBiometricEnabled.collectAsState(false)
            val reqEdit by viewModel.isSecurityRequiredForEdit.collectAsState(false)
            val reqSettings by viewModel.isSecurityRequiredForSettings.collectAsState(false)
            val reqAnalytics by viewModel.isSecurityRequiredForAnalytics.collectAsState(false)
            
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
                isSecurityRequiredForAnalytics = reqAnalytics,
                onSecurityRequiredForAnalyticsChange = viewModel::setSecurityRequiredForAnalytics,
                onLockClicked = onLockClicked,
                isPinRequired = !pin.isNullOrEmpty(),
                onPinRequiredChange = { /* Handled within SecuritySettingsSection via onClearSecurityPin and onSecurityPinChange */ },
                securityManager = viewModel.securityManager,
                isBiometricSupported = viewModel.isBiometricSupported
            )
        }
        SettingsSection.AI -> {
            GenAiSettingsSection(viewModel)
        }
        SettingsSection.CALLS -> {
            CallSettingsSection(viewModel)
        }
        SettingsSection.SMART_INTEGRATION -> {
            SmartHomeSettingsSection(viewModel, isGlobal = false)
        }
        SettingsSection.CLOUD_SYNC -> {
            CloudSettingsSection(
                viewModel = viewModel,
                isGlobal = false,
                onLocalExport = onLocalExport,
                onLocalImport = onLocalImport,
                onSelectSafFolderForImport = onSelectSafFolderForImport
            )
            Spacer(modifier = Modifier.height(16.dp))
            CloudSettingsSection(
                viewModel = viewModel,
                isGlobal = true,
                onLocalExport = onLocalExport,
                onLocalImport = onLocalImport,
                onSelectSafFolderForImport = onSelectSafFolderForImport
            )
        }
        SettingsSection.PERMISSIONS -> {
            PermissionsSettingsSection(viewModel)
        }
        SettingsSection.MAINTENANCE -> {
            ExperimentalSettingsSection(viewModel)
            Spacer(modifier = Modifier.height(16.dp))
            TestSettingsSection(viewModel, isGlobal = false)
            Spacer(modifier = Modifier.height(16.dp))
            TestSettingsSection(viewModel, isGlobal = true)
            Spacer(modifier = Modifier.height(16.dp))
            MaintenanceSection(
                viewModel = viewModel,
                isGlobal = false,
                onLocalExport = onLocalExport,
                onLocalImport = onLocalImport
            )
            Spacer(modifier = Modifier.height(16.dp))
            MaintenanceSection(
                viewModel = viewModel,
                isGlobal = true,
                onLocalExport = {},
                onLocalImport = onLocalImport,
                onSelectSafFolderForImport = onSelectSafFolderForImport
            )
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
