package com.andreas_kratzer.ghosttalk.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.AnalyticsDashboardScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageListScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

enum class BookShellTab {
    Inhalte,
    Statistik
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShellScreen(
    bookViewModel: BookViewModel,
    pageViewModel: PageViewModel,
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    securityManager: SecurityManager,
    onNavigateToUserMode: () -> Unit,
    onNavigateToBooks: () -> Unit,
    onNavigateToGlobalSettings: () -> Unit,
    onRequestUnlock: () -> Unit,
    onEditPage: (String) -> Unit,
    onEditPageWithAssistant: (String, Boolean) -> Unit,
    onEditTemplate: (String) -> Unit,
    onOpenStructureEditor: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToStaticRow: () -> Unit
) {
    val BookShellTabSaver = Saver<BookShellTab, String>(
        save = { it.name },
        restore = { value -> try { BookShellTab.valueOf(value) } catch (e: Exception) { BookShellTab.Inhalte } }
    )
    var currentTab by rememberSaveable(stateSaver = BookShellTabSaver) { mutableStateOf(BookShellTab.Inhalte) }
    var menuExpanded by remember { mutableStateOf(false) }
    // tabSelected: tracks whether the user explicitly chose a tab. While false,
    // the big "Nutzermodus" landing is shown so user mode stays in focus.
    var tabSelected by remember { mutableStateOf(false) }
    // Set when entering user mode, so we can return to the landing on resume
    // (but not when coming back from the editor, where we keep the tab).
    var returningFromUserMode by remember { mutableStateOf(false) }

    val activeBookId by pageViewModel.activeBookId.collectAsState()
    val allBooks by bookViewModel.allBooks.collectAsState()
    val activeBook = allBooks.find { it.id == activeBookId }
    val bookName = activeBook?.name ?: "GhostTalk"

    val isUnlocked by securityManager.isUnlocked.collectAsState()
    val isLocked = securityManager.isPinSet() && !isUnlocked

    // Derived synchronously — no LaunchedEffect, no 1-frame flash.
    // Landing shows when locked OR when no tab has been picked yet.
    val showLanding by remember { derivedStateOf { isLocked || !tabSelected } }

    // After successful unlock, auto-advance to editor so the user doesn't
    // have to click a tab after entering the PIN.
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) tabSelected = true
    }

    LaunchedEffect(Unit) {
        pageViewModel.shellTabRequest.collect { tab ->
            currentTab = tab
            tabSelected = true
        }
    }

    // When returning from user mode, refocus the big landing button.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && returningFromUserMode) {
                returningFromUserMode = false
                tabSelected = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val enterUserMode: () -> Unit = {
        returningFromUserMode = true
        onNavigateToUserMode()
    }

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
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToGlobalSettings,
                        modifier = Modifier.testTag("book_shell_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(CoreR.string.settings_title_book)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        NavigationSuiteScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            navigationSuiteItems = {
                item(
                    selected = false,
                    onClick = enterUserMode,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.nav_speak),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_speak),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.testTag("start_card_user_mode")
                )
                item(
                    selected = !showLanding && currentTab == BookShellTab.Inhalte,
                    onClick = {
                        if (isLocked) onRequestUnlock()
                        else { tabSelected = true; currentTab = BookShellTab.Inhalte }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.nav_content)) },
                    label = { Text(stringResource(R.string.nav_content)) }
                )
                item(
                    selected = !showLanding && currentTab == BookShellTab.Statistik,
                    onClick = {
                        if (isLocked) onRequestUnlock()
                        else { tabSelected = true; currentTab = BookShellTab.Statistik }
                    },
                    icon = { Icon(GhostTalkIcons.BarChart, contentDescription = stringResource(R.string.nav_stats)) },
                    label = { Text(stringResource(R.string.nav_stats)) }
                )
            }
        ) {
            if (showLanding) {
                BookShellLanding(
                    onNavigateToUserMode = enterUserMode,
                    isLocked = isLocked,
                    onRequestUnlock = onRequestUnlock
                )
            } else {
                when (currentTab) {
                    BookShellTab.Inhalte -> {
                        PageListScreen(
                            pageViewModel = pageViewModel,
                            onNavigateBack = null,
                            onEditPage = onEditPage,
                            onEditTemplate = onEditTemplate,
                            onOpenStructureEditor = onOpenStructureEditor,
                            onNavigateToTemplates = onNavigateToTemplates,
                            onNavigateToStaticRow = onNavigateToStaticRow,
                            showTopBar = false
                        )
                    }
                    BookShellTab.Statistik -> {
                        AnalyticsDashboardScreen(
                            pageViewModel = pageViewModel,
                            onNavigateBack = null,
                            onEditPage = onEditPageWithAssistant,
                            showTopBar = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookShellLanding(
    onNavigateToUserMode: () -> Unit,
    isLocked: Boolean,
    onRequestUnlock: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onNavigateToUserMode,
            modifier = Modifier
                .size(width = 240.dp, height = 80.dp)
                .testTag("pin_locked_user_mode_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.start_user_mode),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (isLocked) {
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onRequestUnlock) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.book_shell_unlock_editor))
            }
        }
    }
}
