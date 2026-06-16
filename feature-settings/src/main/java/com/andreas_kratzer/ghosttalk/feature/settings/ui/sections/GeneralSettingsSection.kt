@file:Suppress("UNUSED_VALUE")
package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.highlightSetting
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean
) {
    val theme by viewModel.themeMode.collectAsState()
    val persistLogs by viewModel.persistActionLogs.collectAsState()
    val screenBehavior by viewModel.userModeScreenBehavior.collectAsState()
    val startupBehavior by viewModel.startupBehavior.collectAsState()
    val forceKeyboard by viewModel.forceSoftKeyboard.collectAsState()

    val dimensions = LocalDimensions.current
    val highlightedKey by viewModel.highlightedSettingKey.collectAsState()

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        if (isGlobal) {
            // 1. Benutzeroberfläche (Sprache, Design)
            val categoryUi = stringResource(R.string.settings_category_ui)
            PreferenceCategory(
                title = categoryUi,
                isCloudProfile = true,
                modifier = Modifier.weight(1f)
            ) {
                // App Sprache
                val selectedAppLanguage by viewModel.selectedAppLanguage.collectAsState()
                val appLanguageLabel = stringResource(R.string.settings_app_language)
                val systemDefault = stringResource(R.string.settings_system_default)
                
                val currentLanguageLabel = if (selectedAppLanguage == "default" || selectedAppLanguage == null) {
                    systemDefault
                } else {
                    val locale = Locale.forLanguageTag(selectedAppLanguage!!)
                    locale.getDisplayName(locale)
                }

                Box(modifier = Modifier.fillMaxWidth().highlightSetting(appLanguageLabel, highlightedKey)) {
                    SettingsDropdownItem(
                        label = appLanguageLabel,
                        selectedOption = currentLanguageLabel,
                        options = listOf(
                            systemDefault to { viewModel.setAppLanguage("default") }
                        ) + listOf("de", "en").map { code ->
                            Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(code)) to { viewModel.setAppLanguage(code) }
                        }
                    )
                }

                // App Design
                val themeModeLabel = stringResource(R.string.settings_theme_mode)
                val themeSystem = stringResource(R.string.settings_theme_system)
                val themeLight = stringResource(R.string.settings_theme_light)
                val themeDark = stringResource(R.string.settings_theme_dark)
                
                val themeLabel = when (theme) {
                    "LIGHT" -> themeLight
                    "DARK" -> themeDark
                    else -> themeSystem
                }

                Box(modifier = Modifier.fillMaxWidth().highlightSetting(themeModeLabel, highlightedKey)) {
                    SettingsDropdownItem(
                        label = themeModeLabel,
                        selectedOption = themeLabel,
                        options = listOf(
                            themeSystem to { viewModel.setThemeMode("SYSTEM") },
                            themeLight to { viewModel.setThemeMode("LIGHT") },
                            themeDark to { viewModel.setThemeMode("DARK") }
                        )
                    )
                }

                // Betreuer-Tablet (Caregiver) Toggle
                val isCaregiver by viewModel.isCaregiverDevice.collectAsState()
                val caregiverLabel = stringResource(R.string.settings_caregiver_mode)
                Box(modifier = Modifier.fillMaxWidth().highlightSetting(caregiverLabel, highlightedKey)) {
                    SettingsToggleItem(
                        label = caregiverLabel,
                        checked = isCaregiver,
                        description = stringResource(R.string.settings_caregiver_mode_desc),
                        onCheckedChange = { viewModel.setCaregiverDevice(it) }
                    )
                }
            }

            // 2. Verhalten (Tastatur, Logs, Startup)
            val categoryBehavior = stringResource(R.string.settings_category_behavior)
            PreferenceCategory(
                title = categoryBehavior,
                isCloudProfile = true,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxWidth().highlightSetting(stringResource(R.string.settings_force_soft_keyboard), highlightedKey)) {
                    SettingsToggleItem(
                        label = stringResource(R.string.settings_force_soft_keyboard),
                        checked = forceKeyboard,
                        description = stringResource(R.string.settings_force_soft_keyboard_desc),
                        onCheckedChange = { viewModel.setForceSoftKeyboard(it) }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().highlightSetting(stringResource(R.string.settings_persist_logs), highlightedKey)) {
                    SettingsToggleItem(
                        label = stringResource(R.string.settings_persist_logs),
                        checked = persistLogs,
                        onCheckedChange = { viewModel.setPersistActionLogs(it) }
                    )
                }

                val startupBehaviorLabel = stringResource(R.string.settings_startup_behavior)
                val startupBookList = stringResource(R.string.settings_startup_behavior_book_list)
                val startupLastBook = stringResource(R.string.settings_startup_behavior_last_book)
                val startupUserMode = stringResource(R.string.settings_startup_behavior_user_mode)

                val startupLabel = when (startupBehavior) {
                    "SELECTED_BOOK" -> startupLastBook
                    "USER_MODE" -> startupUserMode
                    else -> startupBookList
                }

                Box(modifier = Modifier.fillMaxWidth().highlightSetting(startupBehaviorLabel, highlightedKey)) {
                    SettingsDropdownItem(
                        label = startupBehaviorLabel,
                        selectedOption = startupLabel,
                        options = listOf(
                            startupBookList to { viewModel.setStartupBehavior("BOOK_SELECTION") },
                            startupLastBook to { viewModel.setStartupBehavior("SELECTED_BOOK") },
                            startupUserMode to { viewModel.setStartupBehavior("USER_MODE") }
                        )
                    )
                }
            }
        }

        if (!isGlobal) {
            val categoryGeneral = stringResource(R.string.settings_category_general)
            val screenBehaviorLabel = stringResource(R.string.settings_screen_behavior)
            val screenOn = stringResource(R.string.settings_screen_behavior_on)
            val screenDimmed = stringResource(R.string.settings_screen_behavior_dimmed)
            val screenBlack = stringResource(R.string.settings_screen_behavior_black)

            PreferenceCategory(
                title = categoryGeneral,
                isCloudProfile = true,
                modifier = Modifier.weight(1f)
            ) {
                // Combined Screen Behavior
                val keepScreenOn by viewModel.keepScreenOnUserMode.collectAsState()

                val behaviorLabel = when {
                    !keepScreenOn -> stringResource(R.string.settings_system_default)
                    screenBehavior == "DIMMED" -> screenDimmed
                    screenBehavior == "BLACK" -> screenBlack
                    else -> screenOn
                }

                Box(modifier = Modifier.fillMaxWidth().highlightSetting(screenBehaviorLabel, highlightedKey)) {
                    SettingsDropdownItem(
                        label = screenBehaviorLabel,
                        selectedOption = behaviorLabel,
                        options = listOf(
                            stringResource(R.string.settings_system_default) to {
                                viewModel.setKeepScreenOnUserMode(false)
                            },
                            screenOn to {
                                viewModel.setKeepScreenOnUserMode(true)
                                viewModel.setUserModeScreenBehavior("NORMAL")
                            },
                            screenDimmed to {
                                viewModel.setKeepScreenOnUserMode(true)
                                viewModel.setUserModeScreenBehavior("DIMMED")
                            },
                            screenBlack to {
                                viewModel.setKeepScreenOnUserMode(true)
                                viewModel.setUserModeScreenBehavior("BLACK")
                            }
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookSettingsSection(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    onBookDeleted: () -> Unit = onNavigateBack
) {
    val defaultStartPageId by viewModel.defaultStartPageId.collectAsState()
    val allPages by viewModel.allPages.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val forceKeyboard by viewModel.forceSoftKeyboard.collectAsState()

    val showDeleteConfirm = remember { mutableStateOf(false) }
    val showDeleteSecurity = remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var startPageSearchQuery by remember { mutableStateOf("") }

    val dimensions = LocalDimensions.current
    val focusManager = LocalFocusManager.current

    val bookName = activeBook?.name ?: ""
    val deleteConfirmTitle = stringResource(R.string.book_dialog_delete_title)
    val deleteConfirmMessage = stringResource(R.string.book_dialog_delete_confirm, bookName)

    val highlightedKey by viewModel.highlightedSettingKey.collectAsState()

    val startPageLabel = stringResource(R.string.settings_start_page)
    val startPageAuto = stringResource(R.string.settings_start_page_auto)
    val bookNameLabel = stringResource(R.string.book_name_label)
    val deleteBookLabel = stringResource(R.string.book_delete_description)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        val categoryManageBook = stringResource(R.string.settings_category_manage_book)
        PreferenceCategory(
            title = categoryManageBook,
            isCloudProfile = false, // Purely local to this specific book
            modifier = Modifier.weight(1f)
        ) {
            // Default Start Page Selector with Filter
            Box(modifier = Modifier.fillMaxWidth().highlightSetting(startPageLabel, highlightedKey)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                ) {
                    Text(
                        text = startPageLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedStartPage,
                        onExpandedChange = { expandedStartPage = !expandedStartPage }
                    ) {
                        val startPageName = allPages.find { it.id == defaultStartPageId }?.name
                            ?: startPageAuto

                        OutlinedTextField(
                            value = if (expandedStartPage) startPageSearchQuery else startPageName,
                            onValueChange = { if (expandedStartPage) startPageSearchQuery = it },
                            readOnly = !expandedStartPage,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStartPage) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedStartPage,
                            onDismissRequest = {
                                expandedStartPage = false
                                startPageSearchQuery = ""
                            }
                        ) {
                            val filteredPages = allPages.filter { it.name.contains(startPageSearchQuery, ignoreCase = true) }

                            // Auto Option (always show when query is empty)
                            if (startPageSearchQuery.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(startPageAuto, style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        viewModel.setDefaultStartPageId(null)
                                        focusManager.clearFocus()
                                        expandedStartPage = false
                                    }
                                )
                            }

                            filteredPages.forEach { page ->
                                DropdownMenuItem(
                                    text = { Text(page.name, style = MaterialTheme.typography.bodyLarge) },
                                    onClick = {
                                        viewModel.setDefaultStartPageId(page.id)
                                        focusManager.clearFocus()
                                        startPageSearchQuery = ""
                                        expandedStartPage = false
                                    }
                                )
                            }

                            if (filteredPages.isEmpty() && startPageSearchQuery.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_no_pages_found), style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { },
                                    enabled = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            // Book Rename
            activeBook?.let { book ->
                var editName by remember(book.id) { mutableStateOf(book.name) }
                Box(modifier = Modifier.fillMaxWidth().highlightSetting(bookNameLabel, highlightedKey)) {
                    SettingsEditTextItem(
                        label = bookNameLabel,
                        value = editName,
                        onValueChange = {
                            editName = it
                            if (it.isNotBlank()) {
                                viewModel.updateActiveBookName(it)
                            }
                        },
                        forceKeyboard = forceKeyboard
                    )
                }
            }

            // Delete Book Button
            Spacer(modifier = Modifier.height(dimensions.paddingLarge))
            Box(modifier = Modifier.fillMaxWidth().highlightSetting(deleteBookLabel, highlightedKey)) {
                Button(
                    onClick = {
                        if (viewModel.securityManager.isSecurityRequiredForDeletion()) {
                            showDeleteSecurity.value = true
                        } else {
                            showDeleteConfirm.value = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(deleteBookLabel)
                }
            }
        }
    }

    if (showDeleteSecurity.value) {
        SecurityEntryDialog(
            onDismiss = { showDeleteSecurity.value = false },
            onConfirm = { success ->
                if (success) {
                    showDeleteSecurity.value = false
                    showDeleteConfirm.value = true
                }
            },
            securityManager = viewModel.securityManager,
            isBiometricEnabled = viewModel.settingsRepository.isBiometricEnabled
        )
    }

    if (showDeleteConfirm.value) {
        GhostTalkDialog(
            title = deleteConfirmTitle,
            confirmText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_delete),
            dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm.value = false
                viewModel.deleteActiveBook {
                    onBookDeleted()
                }
            },
            onDismiss = { showDeleteConfirm.value = false },
            content = {
                Text(deleteConfirmMessage)
            }
        )
    }
}
