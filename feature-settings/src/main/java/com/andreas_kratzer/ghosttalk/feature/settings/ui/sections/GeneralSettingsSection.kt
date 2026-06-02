package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import java.util.Locale
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onNavigateBack: () -> Unit = {},
    onBookDeleted: () -> Unit = onNavigateBack
) {
    val theme by viewModel.themeMode.collectAsState("SYSTEM")
    val persistLogs by viewModel.persistActionLogs.collectAsState(false)
    val defaultStartPageId by viewModel.defaultStartPageId.collectAsState(null)
    val allPages by viewModel.allPages.collectAsState()

    val screenBehavior by viewModel.userModeScreenBehavior.collectAsState("NORMAL")
    val startupBehavior by viewModel.startupBehavior.collectAsState("BOOK_SELECTION")
    val activeBook by viewModel.activeBook.collectAsState()
    val forceKeyboard by viewModel.forceSoftKeyboard.collectAsState(false)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteSecurity by remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var startPageSearchQuery by remember { mutableStateOf("") }

    val dimensions = LocalDimensions.current
    LocalContext.current
    val focusManager = LocalFocusManager.current

    val bookName = activeBook?.name ?: ""
    stringResource(R.string.book_delete_description)
    val deleteConfirmTitle = stringResource(R.string.book_dialog_delete_title)
    val deleteConfirmMessage = stringResource(R.string.book_dialog_delete_confirm, bookName)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        if (isGlobal) {
            // 1. Benutzeroberfläche (Sprache, Design)
            val categoryUi = stringResource(R.string.settings_category_ui)
            PreferenceCategory(categoryUi, modifier = Modifier.weight(1f)) {
                // App Sprache
                val selectedAppLanguage by viewModel.selectedAppLanguage.collectAsState("default")
                val appLanguageLabel = stringResource(R.string.settings_app_language)
                val systemDefault = stringResource(R.string.settings_system_default)
                
                val currentLanguageLabel = if (selectedAppLanguage == "default" || selectedAppLanguage == null) {
                    systemDefault
                } else {
                    val locale = Locale.forLanguageTag(selectedAppLanguage!!)
                    locale.getDisplayName(locale)
                }

                SettingsDropdownItem(
                    label = appLanguageLabel,
                    selectedOption = currentLanguageLabel,
                    options = listOf(
                        systemDefault to { viewModel.setAppLanguage("default") }
                    ) + listOf("de", "en").map { code ->
                        Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(code)) to { viewModel.setAppLanguage(code) }
                    }
                )

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

            // 2. Verhalten (Tastatur, Logs, Startup)
            val categoryBehavior = stringResource(R.string.settings_category_behavior)
            PreferenceCategory(categoryBehavior, modifier = Modifier.weight(1f)) {
                SettingsToggleItem(
                    label = stringResource(R.string.settings_force_soft_keyboard),
                    checked = forceKeyboard,
                    onCheckedChange = { viewModel.setForceSoftKeyboard(it) }
                )

                SettingsToggleItem(
                    label = stringResource(R.string.settings_persist_logs),
                    checked = persistLogs,
                    onCheckedChange = { viewModel.setPersistActionLogs(it) }
                )

                val startupBehaviorLabel = stringResource(R.string.settings_startup_behavior)
                val startupBookList = stringResource(R.string.settings_startup_behavior_book_list)
                val startupLastBook = stringResource(R.string.settings_startup_behavior_last_book)
                val startupUserMode = stringResource(R.string.settings_startup_behavior_user_mode)

                val startupLabel = when (startupBehavior) {
                    "SELECTED_BOOK" -> startupLastBook
                    "USER_MODE" -> startupUserMode
                    else -> startupBookList
                }

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

        if (!isGlobal) {
            val categoryGeneral = stringResource(R.string.settings_category_general)
            stringResource(R.string.settings_keep_screen_on)
            val screenBehaviorLabel = stringResource(R.string.settings_screen_behavior)
            val screenOn = stringResource(R.string.settings_screen_behavior_on)
            val screenDimmed = stringResource(R.string.settings_screen_behavior_dimmed)
            val screenBlack = stringResource(R.string.settings_screen_behavior_black)
            val startPageLabel = stringResource(R.string.settings_start_page)
            val startPageAuto = stringResource(R.string.settings_start_page_auto)

            val bookNameLabel = stringResource(R.string.book_name_label)
            val deleteBookLabel = stringResource(R.string.book_delete_description)
            stringResource(R.string.book_dialog_delete_title)
            stringResource(R.string.book_dialog_delete_confirm, activeBook?.name ?: "")

            PreferenceCategory(categoryGeneral, modifier = Modifier.weight(1f)) {
                // 1. Book Rename
                activeBook?.let { book ->
                    var editName by remember(book.id) { mutableStateOf(book.name) }
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

                // 2. Default Start Page Selector with Filter
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
                                    text = { Text("Keine Seiten gefunden", style = MaterialTheme.typography.bodyLarge) },
                                    onClick = { },
                                    enabled = false
                                )
                            }
                        }
                    }
                }

                // 3. Combined Screen Behavior
                val behaviorLabel = when {
                    screenBehavior == "DIMMED" -> screenDimmed
                    screenBehavior == "BLACK" -> screenBlack
                    else -> screenOn
                }

                SettingsDropdownItem(
                    label = screenBehaviorLabel,
                    selectedOption = behaviorLabel,
                    options = listOf(
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

                // 4. Delete Book Button (moved into category)
                Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                Button(
                    onClick = {
                        if (viewModel.securityManager.isSecurityRequiredForDeletion()) {
                            showDeleteSecurity = true
                        } else {
                            showDeleteConfirm = true
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

    if (showDeleteSecurity) {
        SecurityEntryDialog(
            onDismiss = { showDeleteSecurity = false },
            onConfirm = { success ->
                if (success) {
                    showDeleteSecurity = false
                    showDeleteConfirm = true
                }
            },
            securityManager = viewModel.securityManager,
            isBiometricEnabled = viewModel.settingsRepository.isBiometricEnabled
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(deleteConfirmTitle) },
            text = { Text(deleteConfirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteActiveBook {
                            onBookDeleted() 
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(CoreR.string.action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(CoreR.string.action_cancel))
                }
            }
        )
    }
}
