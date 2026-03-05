package com.andreas_kratzer.ghosttalk.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.sections.CloudSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.GenAiSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.NotificationSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.ScanningSettingsSection
import com.andreas_kratzer.ghosttalk.ui.settings.sections.VoiceSettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val authIntent by viewModel.authIntentFlow.collectAsState(null)
    val signInError by viewModel.signInErrorMessage.collectAsState()

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> viewModel.refresh() }

    LaunchedEffect(authIntent) {
        authIntent?.let { authLauncher.launch(it) }
    }

    LaunchedEffect(signInError) {
        signInError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(16.dp)
        ) {
            VoiceSettingsSection(viewModel)
            ScanningSettingsSection(viewModel)
            CloudSettingsSection(viewModel)
            GenAiSettingsSection(viewModel)
            NotificationSettingsSection(viewModel)
            
            AppLanguageSection(viewModel)
            GeneralSettingsSection(viewModel)
            MaintenanceSection(viewModel)
            
            Spacer(modifier = Modifier.height(32.dp))
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "Unknown"
            }
            Text(
                text = "Version: $versionName",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun AppLanguageSection(viewModel: SettingsViewModel) {
    val selectedAppLanguage by viewModel.selectedAppLanguage.collectAsState("default")
    var expanded by remember { mutableStateOf(false) }
    
    PreferenceCategory(stringResource(R.string.settings_category_language)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val label = if (selectedAppLanguage == "default" || selectedAppLanguage == null) {
                stringResource(R.string.settings_system_default)
            } else java.util.Locale.forLanguageTag(selectedAppLanguage!!).displayName
            
            SettingsClickableItem(stringResource(R.string.settings_app_language), label) { expanded = true }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_system_default)) }, onClick = { viewModel.setAppLanguage("default"); expanded = false })
                listOf("de", "en").forEach { code ->
                    DropdownMenuItem(text = { Text(java.util.Locale.forLanguageTag(code).displayName) }, onClick = { viewModel.setAppLanguage(code); expanded = false })
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsSection(viewModel: SettingsViewModel) {
    val theme by viewModel.themeMode.collectAsState("SYSTEM")
    val showPageId by viewModel.showPageIdInLog.collectAsState(true)
    var expandedTheme by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_category_ui)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsClickableItem(stringResource(R.string.settings_theme_mode), theme) { expandedTheme = true }
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                listOf("SYSTEM", "LIGHT", "DARK").forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { viewModel.setThemeMode(t); expandedTheme = false })
                }
            }
        }
        SettingsToggleItem(stringResource(R.string.settings_show_page_id_in_log), showPageId) { viewModel.setShowPageIdInLog(it) }
    }
}

@Composable
fun MaintenanceSection(viewModel: SettingsViewModel) {
    PreferenceCategory(stringResource(R.string.settings_category_maintenance)) {
        Button(
            onClick = { viewModel.clearButtonUsageStats(viewModel.activeBookId) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_clear_usage_stats))
        }
    }
}

@Composable
fun PreferenceCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
fun SettingsToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickableItem(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsEditTextItem(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}
