package com.andreas_kratzer.ghosttalk.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsScreen
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.AnalyticsDashboardScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageListScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

enum class BookShellTab {
    Inhalte,
    Statistik,
    Einstellungen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShellScreen(
    bookViewModel: BookViewModel,
    pageViewModel: PageViewModel,
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    onNavigateToUserMode: () -> Unit,
    onNavigateToBooks: () -> Unit,
    onNavigateToGlobalSettings: () -> Unit,
    onEditPage: (String) -> Unit,
    onEditPageWithAssistant: (String, Boolean) -> Unit,
    onEditTemplate: (String) -> Unit,
    onOpenStructureEditor: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToStaticRow: () -> Unit
) {
    var currentTab by remember { mutableStateOf(BookShellTab.Inhalte) }
    var menuExpanded by remember { mutableStateOf(false) }

    val activeBookId by pageViewModel.activeBookId.collectAsState()
    val allBooks by bookViewModel.allBooks.collectAsState()
    val activeBook = allBooks.find { it.id == activeBookId }
    val bookName = activeBook?.name ?: "GhostTalk"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("book_switcher_button")
                        ) {
                            Text(
                                text = bookName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            allBooks.forEach { book ->
                                DropdownMenuItem(
                                    text = { Text(book.name) },
                                    onClick = {
                                        menuExpanded = false
                                        pageViewModel.setActiveBookId(book.id)
                                        settingsRepository.activeBookId = book.id
                                        settingsViewModel.refresh()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.start_back_to_books)) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToBooks()
                                },
                                modifier = Modifier.testTag("book_switcher_back_to_books")
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(SettingsR.string.settings_global_settings)) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToGlobalSettings()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavigationSuiteScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            navigationSuiteItems = {
                item(
                    selected = false,
                    onClick = onNavigateToUserMode,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.nav_speak)) },
                    label = { Text(stringResource(R.string.nav_speak)) },
                    modifier = Modifier.testTag("start_card_user_mode")
                )
                item(
                    selected = currentTab == BookShellTab.Inhalte,
                    onClick = { currentTab = BookShellTab.Inhalte },
                    icon = { Icon(Icons.Default.List, contentDescription = stringResource(R.string.nav_content)) },
                    label = { Text(stringResource(R.string.nav_content)) }
                )
                item(
                    selected = currentTab == BookShellTab.Statistik,
                    onClick = { currentTab = BookShellTab.Statistik },
                    icon = { Icon(GhostTalkIcons.BarChart, contentDescription = stringResource(R.string.nav_stats)) },
                    label = { Text(stringResource(R.string.nav_stats)) }
                )
                item(
                    selected = currentTab == BookShellTab.Einstellungen,
                    onClick = { currentTab = BookShellTab.Einstellungen },
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(CoreR.string.settings_title_book)) },
                    label = { Text(stringResource(CoreR.string.settings_title_book)) }
                )
            }
        ) {
            when (currentTab) {
                BookShellTab.Inhalte -> {
                    PageListScreen(
                        pageViewModel = pageViewModel,
                        onNavigateBack = null,
                        onEditPage = onEditPage,
                        onEditTemplate = onEditTemplate,
                        onOpenStructureEditor = onOpenStructureEditor,
                        onNavigateToTemplates = onNavigateToTemplates,
                        onNavigateToStaticRow = onNavigateToStaticRow
                    )
                }
                BookShellTab.Statistik -> {
                    AnalyticsDashboardScreen(
                        pageViewModel = pageViewModel,
                        onNavigateBack = null,
                        onEditPage = onEditPageWithAssistant
                    )
                }
                BookShellTab.Einstellungen -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        isGlobal = false,
                        onNavigateBack = null
                    )
                }
            }
        }
    }
}
