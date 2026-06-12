package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
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

@Composable
fun SettingsSubMenu(
    section: SettingsSection,
    padding: PaddingValues,
    dimensions: Dimensions,
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onBookDeleted: () -> Unit,
    onLockClicked: () -> Unit,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit,
    onSelectSafFolderForImport: () -> Unit,
    onNavigateToVocalTraining: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.screenPaddingVertical)
    ) {
        SubmenuContent(
            section = section,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onBookDeleted = onBookDeleted,
            onLockClicked = onLockClicked,
            onLocalExport = onLocalExport,
            onLocalImport = onLocalImport,
            onSelectSafFolderForImport = onSelectSafFolderForImport,
            onNavigateToVocalTraining = onNavigateToVocalTraining
        )
        Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge * 2))
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
    onSelectSafFolderForImport: () -> Unit = {},
    onNavigateToVocalTraining: () -> Unit = {}
) {
    when (section) {
        SettingsSection.GENERAL -> {
            GeneralSettingsSection(viewModel, isGlobal = true)
            Spacer(modifier = Modifier.height(16.dp))
            GeneralSettingsSection(viewModel, isGlobal = false)
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
            VocalSwitchSettingsSection(viewModel, onNavigateToVocalTraining = onNavigateToVocalTraining)
        }
        SettingsSection.SECURITY -> {
            val pin by viewModel.security.securityPin.collectAsState()
            val timeout by viewModel.security.securityPinTimeoutMinutes.collectAsState()
            val reqDeletion by viewModel.security.isPinRequiredForDeletion.collectAsState()
            val biometricEnabled by viewModel.security.isBiometricEnabled.collectAsState()
            val reqEdit by viewModel.security.isSecurityRequiredForEdit.collectAsState()
            val reqSettings by viewModel.security.isSecurityRequiredForSettings.collectAsState()
            val reqAnalytics by viewModel.security.isSecurityRequiredForAnalytics.collectAsState()
            
            SecuritySettingsSection(
                securityPin = pin,
                onSecurityPinChange = viewModel.security::setSecurityPin,
                onClearSecurityPin = viewModel.security::clearSecurityPin,
                securityPinTimeoutMinutes = timeout,
                onSecurityPinTimeoutChange = viewModel.security::setSecurityPinTimeoutMinutes,
                isPinRequiredForDeletion = reqDeletion,
                onPinRequiredForDeletionChange = viewModel.security::setPinRequiredForDeletion,
                isBiometricEnabled = biometricEnabled,
                onBiometricEnabledChange = viewModel.security::setBiometricEnabled,
                isSecurityRequiredForEdit = reqEdit,
                onSecurityRequiredForEditChange = viewModel.security::setSecurityRequiredForEdit,
                isSecurityRequiredForSettings = reqSettings,
                onSecurityRequiredForSettingsChange = viewModel.security::setSecurityRequiredForSettings,
                isSecurityRequiredForAnalytics = reqAnalytics,
                onSecurityRequiredForAnalyticsChange = viewModel.security::setSecurityRequiredForAnalytics,
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
            SmartHomeSettingsSection(viewModel)
        }
        SettingsSection.CLOUD_SYNC -> {
            CloudSettingsSection(
                viewModel = viewModel,
                showSyncSettings = true
            )
        }
        SettingsSection.ACCOUNTS -> {
            CloudSettingsSection(
                viewModel = viewModel,
                showSyncSettings = false
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
