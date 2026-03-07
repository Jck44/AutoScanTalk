package com.andreas_kratzer.ghosttalk.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.sections.CloudSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.ExperimentalSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GeminiNanoSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GenAiSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GeneralSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.LanguageSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.MaintenanceSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.NotificationSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.ScanningSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.TestSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.VoiceSettingsSection
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

enum class SettingsSection(val titleRes: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_category_general, Icons.Default.Settings),
    VOICE(R.string.settings_category_voice, GhosTTalkIcons.RecordVoiceOver),
    SCANNING(R.string.settings_category_scanning, GhosTTalkIcons.SettingsAccessibility),
    CLOUD(R.string.settings_category_cloud, GhosTTalkIcons.Cloud),
    GEMINI(R.string.settings_category_gemini, GhosTTalkIcons.AutoAwesome),
    NOTIFICATIONS(R.string.settings_category_notifications, GhosTTalkIcons.Notifications),
    ADVANCED(R.string.settings_category_advanced, GhosTTalkIcons.Science)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
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

    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (selectedSection == null) 
                            stringResource(R.string.settings_title) 
                        else 
                            stringResource(selectedSection!!.titleRes), 
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectedSection == null) onNavigateBack() else selectedSection = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
        ) {
            if (selectedSection == null) {
                // Main Menu
                SettingsMainMenuList(onSectionSelect = { selectedSection = it })
                
                Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge))
                VersionInfo()
            } else {
                // Submenu Content
                SubmenuContent(selectedSection!!, viewModel)
                Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge))
            }
        }
    }
}

@Composable
fun SettingsMainMenuList(onSectionSelect: (SettingsSection) -> Unit) {
    val dimensions = LocalDimensions.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        SettingsSection.entries.forEach { section ->
            Surface(
                onClick = { onSectionSelect(section) },
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
    }
}

@Composable
fun SubmenuContent(section: SettingsSection, viewModel: SettingsViewModel) {
    when (section) {
        SettingsSection.GENERAL -> {
            LanguageSettingsSection(viewModel)
            GeneralSettingsSection(viewModel)
        }
        SettingsSection.VOICE -> {
            VoiceSettingsSection(viewModel)
        }
        SettingsSection.SCANNING -> {
            ScanningSettingsSection(viewModel)
        }
        SettingsSection.CLOUD -> {
            CloudSettingsSection(viewModel)
        }
        SettingsSection.GEMINI -> {
            GenAiSettingsSection(viewModel)
            GeminiNanoSettingsSection(viewModel)
        }
        SettingsSection.NOTIFICATIONS -> {
            NotificationSettingsSection(viewModel)
        }
        SettingsSection.ADVANCED -> {
            ExperimentalSettingsSection(viewModel)
            TestSettingsSection(viewModel)
            MaintenanceSection(viewModel)
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

@Composable
fun PreferenceCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    val dimensions = LocalDimensions.current
    Column(modifier = Modifier.padding(vertical = dimensions.paddingMedium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = dimensions.paddingSmall, bottom = dimensions.paddingMedium)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingMedium), content = content)
        }
    }
}

@Composable
fun SettingsToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val dimensions = LocalDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label, 
            modifier = Modifier.weight(1f), 
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickableItem(label: String, value: String, onClick: () -> Unit) {
    val dimensions = LocalDimensions.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimensions.paddingMedium)
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelMedium, 
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SettingsEditTextItem(
    label: String, 
    value: String, 
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
) {
    val dimensions = LocalDimensions.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall),
        keyboardOptions = keyboardOptions,
        singleLine = true
    )
}
