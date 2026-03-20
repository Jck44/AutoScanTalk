package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val theme by viewModel.themeMode.collectAsState("SYSTEM")
    val persistLogs by viewModel.persistActionLogs.collectAsState(false)
    val defaultStartPageId by viewModel.defaultStartPageId.collectAsState(null)
    val allPages by viewModel.allPages.collectAsState()
    
    val keepScreenOn by viewModel.keepScreenOnUserMode.collectAsState(true)
    val screenBehavior by viewModel.userModeScreenBehavior.collectAsState("NORMAL")
    val startupBehavior by viewModel.startupBehavior.collectAsState("BOOK_SELECTION")
    val userEmail by viewModel.userEmail.collectAsState(null)

    var expandedStartPage by remember { mutableStateOf(false) }
    var startPageSearchQuery by remember { mutableStateOf("") }

    val dimensions = LocalDimensions.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        if (isGlobal) {
            val categoryTitle = stringResource(R.string.settings_category_google_account)
            val accountStatusNotSignedIn = stringResource(R.string.settings_google_account_status_not_signed_in)
            val signInText = stringResource(R.string.settings_google_account_sign_in)
            val signOutText = stringResource(R.string.settings_google_account_sign_out)

            PreferenceCategory(categoryTitle, modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = userEmail ?: accountStatusNotSignedIn,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (userEmail == null) {
                        Button(
                            onClick = { viewModel.signIn(context) },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(signInText)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(signOutText)
                        }
                    }
                }
            }

            val categoryUi = stringResource(R.string.settings_category_ui)
            val themeModeLabel = stringResource(R.string.settings_theme_mode)
            val themeSystem = stringResource(R.string.settings_theme_system)
            val themeLight = stringResource(R.string.settings_theme_light)
            val themeDark = stringResource(R.string.settings_theme_dark)
            val persistLogsLabel = stringResource(R.string.settings_persist_logs)

            PreferenceCategory(categoryUi, modifier = Modifier.weight(1f)) {
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

                SettingsToggleItem(
                    label = persistLogsLabel,
                    checked = persistLogs,
                    onCheckedChange = { viewModel.setPersistActionLogs(it) }
                )
            }

            val categoryGeneral = stringResource(R.string.settings_category_general)
            val startupBehaviorLabel = stringResource(R.string.settings_startup_behavior)
            val startupBookList = stringResource(R.string.settings_startup_behavior_book_list)
            val startupLastBook = stringResource(R.string.settings_startup_behavior_last_book)
            val startupUserMode = stringResource(R.string.settings_startup_behavior_user_mode)

            PreferenceCategory(categoryGeneral, modifier = Modifier.weight(1f)) {
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
            val keepScreenOnLabel = stringResource(R.string.settings_keep_screen_on)
            val screenBehaviorLabel = stringResource(R.string.settings_screen_behavior)
            val screenNormal = stringResource(R.string.settings_screen_behavior_normal)
            val screenDimmed = stringResource(R.string.settings_screen_behavior_dimmed)
            val screenBlack = stringResource(R.string.settings_screen_behavior_black)
            val startPageLabel = stringResource(R.string.settings_start_page)
            val startPageAuto = stringResource(R.string.settings_start_page_auto)

            PreferenceCategory(categoryGeneral, modifier = Modifier.weight(1f)) {
                SettingsToggleItem(
                    label = keepScreenOnLabel,
                    checked = keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOnUserMode(it) }
                )

                if (keepScreenOn) {
                    val behaviorLabel = when (screenBehavior) {
                        "DIMMED" -> screenDimmed
                        "BLACK" -> screenBlack
                        else -> screenNormal
                    }

                    SettingsDropdownItem(
                        label = screenBehaviorLabel,
                        selectedOption = behaviorLabel,
                        options = listOf(
                            screenNormal to { viewModel.setUserModeScreenBehavior("NORMAL") },
                            screenDimmed to { viewModel.setUserModeScreenBehavior("DIMMED") },
                            screenBlack to { viewModel.setUserModeScreenBehavior("BLACK") }
                        )
                    )
                }

                // Default Start Page Selector with Filter
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
            }
 
            val actionLogLimit by viewModel.actionLogLimit.collectAsState(100)
            PreferenceCategory(stringResource(R.string.settings_category_limits), modifier = Modifier.weight(1f)) {
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_action_log_limit),
                    value = actionLogLimit.toString(),
                    onValueChange = { viewModel.setActionLogLimitInput(it) }
                )
            }
        }
    }
}
