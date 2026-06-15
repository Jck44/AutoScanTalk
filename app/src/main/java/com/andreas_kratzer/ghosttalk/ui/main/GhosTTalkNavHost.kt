package com.andreas_kratzer.ghosttalk.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorMode
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsScreen
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.VocalTrainingScreen
import com.andreas_kratzer.ghosttalk.ui.books.BookListScreen
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageWorkbenchScreen
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateEditorScreen
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateScreen
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("UNUSED_VALUE")
@Composable
fun GhostTalkNavHost(
    navController: NavHostController,
    bookViewModel: BookViewModel,
    pageViewModel: PageViewModel,
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    pageRepository: PageRepository,
    bookRepository: BookRepository,
    sampleDataInitializer: SampleDataInitializer,
    securityManager: SecurityManager
) {
    val isUnlocked by securityManager.isUnlocked.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pendingRoute = remember { mutableStateOf<String?>(null) }
    val showUnlockDialog = remember { mutableStateOf(false) }

    val navigateWithSecurity: (String) -> Unit = { route ->
        val isProtected = when {
            route.startsWith("settings") -> securityManager.isSecurityRequiredForSettings()
            route == "analytics_dashboard" -> securityManager.isSecurityRequiredForAnalytics()
            route == "content_management" || 
            route == "templates" || 
            route.startsWith("editor") || 
            route.startsWith("template_editor") -> securityManager.isSecurityRequiredForEdit()
            else -> false
        }
        
        if (!isUnlocked && isProtected) {
            pendingRoute.value = route
        } else {
            navController.safeNavigate(route)
        }
    }

    if (pendingRoute.value != null) {
        SecurityEntryDialog(
            onDismiss = {
                pendingRoute.value = null
            },
            onConfirm = { success: Boolean ->
                if (success) {
                    val route = pendingRoute.value!!
                    pendingRoute.value = null
                    navController.safeNavigate(route)
                } else {
                    pendingRoute.value = null
                }
            },
            securityManager = securityManager,
            isBiometricEnabled = settingsRepository.isBiometricEnabled
        )
    }

    if (showUnlockDialog.value) {
        SecurityEntryDialog(
            onDismiss = { showUnlockDialog.value = false },
            onConfirm = { success: Boolean ->
                showUnlockDialog.value = false
            },
            securityManager = securityManager,
            isBiometricEnabled = settingsRepository.isBiometricEnabled
        )
    }

    // Handle auto-navigation
    LaunchedEffect(Unit) {
        bookViewModel.autoOpenBookEvent.collect { selectedBookId ->
            if (navController.currentDestination?.route != "book_list") {
                navController.currentBackStackEntryFlow.first {
                    it.destination.route == "book_list"
                }
            }
            pageViewModel.setActiveBookId(selectedBookId)
            settingsRepository.activeBookId = selectedBookId
            settingsViewModel.refresh()

            val behavior = settingsRepository.startupBehavior
            // On auto-open, remove book_list from the back stack entirely
            // (inclusive = true) so the landing/user mode is the real root:
            // system-back there closes the app instead of returning to a list
            // the user never chose to see. "Back to books" navigates explicitly.
            if (behavior == "USER_MODE") {
                // Navigate directly to user mode
                val finalPage = resolveStartPage(selectedBookId, settingsRepository, pageRepository)

                if (finalPage != null) {
                    pageViewModel.loadPage(finalPage)
                    runOnMainThread {
                        navController.navigate("start") {
                            popUpTo("book_list") { inclusive = true }
                        }
                        navController.navigate("main") {
                            launchSingleTop = true
                        }
                    }
                } else {
                    // Fallback to start screen if no pages
                    runOnMainThread {
                        navController.navigate("start") {
                            popUpTo("book_list") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            } else {
                runOnMainThread {
                    navController.navigate("start") {
                        popUpTo("book_list") { inclusive = true }
                    }
                }
            }
        }
    }


    // Handle Settings Navigation Events
    LaunchedEffect(Unit) {
        settingsViewModel.navigationEvents.collect { event ->
            runOnMainThread {
                when (event) {
                    is SettingsViewModel.SettingsNavigationEvent.EditButton -> {
                        navController.navigate("editor/${event.pageId}?mode=${EditorMode.RASTER.route}&buttonId=${event.buttonId}")
                    }
                    is SettingsViewModel.SettingsNavigationEvent.JumpToPage -> {
                        navController.navigate("editor/${event.pageId}?mode=${EditorMode.RASTER.route}")
                    }
                    is SettingsViewModel.SettingsNavigationEvent.StartSetup -> {
                        settingsRepository.isSetupCompleted = false
                        navController.navigate("onboarding_setup") {
                            popUpTo("start") { inclusive = true }
                        }
                    }
                }
            }
        }
    }

    val isSetupCompleted = settingsRepository.isSetupCompleted
    val startDestination = remember(isSetupCompleted) {
        if (isSetupCompleted) "book_list" else "onboarding_setup"
    }

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { androidx.compose.animation.EnterTransition.None },
        exitTransition = { androidx.compose.animation.ExitTransition.None },
        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
        popExitTransition = { androidx.compose.animation.ExitTransition.None }
    ) {
        composable("onboarding_setup") {
            com.andreas_kratzer.ghosttalk.ui.setup.SetupScreen(
                viewModel = settingsViewModel,
                onSetupFinished = {
                    coroutineScope.launch {
                        val defaultBookId = "book-default"
                        // 1. Ensure at least one book exists. returns either default or first existing.
                        val initializedBookId = sampleDataInitializer.initializeIfNeeded(defaultBookId)
                        
                        // 2. Load the user's last active book preference
                        val persistedActiveBookId = settingsRepository.activeBookId
                        
                        // 3. Verify it still exists in the DB
                        val finalActiveBookId = if (bookRepository.getBookById(persistedActiveBookId) != null) {
                            persistedActiveBookId
                        } else {
                            initializedBookId
                        }

                        // 4. Set the final active book
                        settingsRepository.activeBookId = finalActiveBookId
                        pageViewModel.setActiveBookId(finalActiveBookId)
                        
                        settingsRepository.isSetupCompleted = true
                        
                        runOnMainThread {
                            navController.navigate("book_list") {
                                popUpTo("onboarding_setup") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onRequestDefaultDialer = { activity ->
                    settingsViewModel.call.requestDefaultDialer(activity)
                }
            )
        }
        composable("book_list") {
            val isResolvingStartDestination by bookViewModel.isResolvingStartDestination.collectAsState()
            // book_list lingers in composition for a frame after we navigate away
            // (popped entry). If the gate flips to false during that window it
            // would briefly render the list → flash. Guard on being the *current*
            // destination: while lingering, the current route is already "start",
            // so we stay blank regardless of the gate.
            val currentEntry by navController.currentBackStackEntryAsState()
            val isCurrentDestination = currentEntry?.destination?.route == "book_list"
            val showList = !isResolvingStartDestination && isCurrentDestination
            if (!showList) {
                // Auto-open pending or we've already navigated away: render nothing
                // so the book list never flashes.
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())
            } else {
                BookListScreen(
                    bookViewModel = bookViewModel,
                    settingsRepository = settingsRepository,
                    securityManager = securityManager,
                    onBookSelected = { selectedBookId ->
                        pageViewModel.setActiveBookId(selectedBookId)
                        settingsRepository.activeBookId = selectedBookId
                        settingsViewModel.refresh()
                        navController.navigate("start")
                    },
                    onNavigateToGlobalSettings = { navigateWithSecurity("settings?isGlobal=true") }
                )
            }
        }
        composable("start") {
            // Reaching "start" means we've navigated away from book_list, so
            // release the gate so it renders normally on later visits.
            LaunchedEffect(Unit) { bookViewModel.markStartDestinationResolved() }
            BookShellScreen(
                bookViewModel = bookViewModel,
                pageViewModel = pageViewModel,
                settingsViewModel = settingsViewModel,
                settingsRepository = settingsRepository,
                securityManager = securityManager,
                onNavigateToUserMode = {
                    coroutineScope.launch {
                        val activeBookId = pageViewModel.activeBookId.value ?: "book-default"
                        val finalPage = resolveStartPage(activeBookId, settingsRepository, pageRepository)
                        if (finalPage != null) {
                            pageViewModel.loadPage(finalPage)
                            navigateWithSecurity("main")
                        }
                    }
                },
                onNavigateToBooks = {
                    // User explicitly wants the list now → release the gate so it
                    // renders (it stays blank only during startup auto-open).
                    bookViewModel.markStartDestinationResolved()
                    runOnMainThread {
                        // Manual selection path keeps book_list in the stack → pop
                        // back to it. Auto-open path removed it → navigate fresh.
                        if (!navController.popBackStack("book_list", inclusive = false)) {
                            navController.navigate("book_list")
                        }
                    }
                },
                onNavigateToGlobalSettings = { navigateWithSecurity("settings?isGlobal=false") },
                onRequestUnlock = { showUnlockDialog.value = true },
                onEditPage = { pageId ->
                    navController.safeNavigate("editor/$pageId?mode=${EditorMode.RASTER.route}")
                },
                onEditPageWithAssistant = { pageId, openAssistant ->
                    navController.safeNavigate("editor/$pageId?mode=${EditorMode.RASTER.route}&openAssistant=$openAssistant")
                },
                onEditTemplate = { templateId ->
                    navController.safeNavigate("template_editor/$templateId")
                },
                onOpenStructureEditor = {
                    val bookId = pageViewModel.activeBookId.value ?: "book-default"
                    coroutineScope.launch {
                        val finalPage = resolveStartPage(bookId, settingsRepository, pageRepository)
                        if (finalPage != null) {
                            runOnMainThread {
                                navigateWithSecurity("editor/${finalPage.id}?mode=${EditorMode.STRUKTUR.route}")
                            }
                        }
                    }
                },
                onNavigateToTemplates = { navController.safeNavigate("templates") },
                onNavigateToStaticRow = {
                    val bookId = pageViewModel.activeBookId.value ?: "book-default"
                    navController.safeNavigate("editor/static_row_$bookId?mode=${EditorMode.RASTER.route}")
                }
            )
        }
        composable("main") {
            val callViewModel = hiltViewModel<com.andreas_kratzer.ghosttalk.ui.pages.CallViewModel>()

            // USER_MODE startup may go straight to "main" (skipping "start"), so
            // release the gate here too.
            LaunchedEffect(Unit) { bookViewModel.markStartDestinationResolved() }

            LaunchedEffect(Unit) {
                if (securityManager.isPinSet()) {
                    securityManager.lock()
                }
            }

            PageScreen(
                pageViewModel = pageViewModel,
                callViewModel = callViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(
            "settings?isGlobal={isGlobal}",
            arguments = listOf(navArgument("isGlobal") { 
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val isGlobal = backStackEntry.arguments?.getBoolean("isGlobal") ?: false
            SettingsScreen(
                viewModel = settingsViewModel,
                isGlobal = isGlobal,
                onNavigateBack = { navController.safePopBackStack() },
                onBookDeleted = {
                    navController.navigate("book_list") {
                        popUpTo("book_list") { inclusive = true }
                    }
                },
                onNavigateToStart = {
                    navController.navigate("start") {
                        popUpTo("start") { inclusive = true }
                    }
                },
                onNavigateToVocalTraining = {
                    navController.safeNavigate("vocal_training")
                }
            )
        }
        composable("templates") {
            val templateViewModel = hiltViewModel<TemplateViewModel>()
            TemplateScreen(
                templateViewModel = templateViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                onTemplateClick = { templateId ->
                    navController.safeNavigate("template_editor/$templateId")
                }
            )
        }
        composable("template_editor/{templateId}") { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")
            if (templateId != null) {
                val templateViewModel = hiltViewModel<TemplateViewModel>()
                TemplateEditorScreen(
                    templateId = templateId,
                    templateViewModel = templateViewModel,
                    pageViewModel = pageViewModel,
                    onNavigateBack = { navController.safePopBackStack() }
                )
            }
        }
        composable(
            "editor/{pageId}?mode={mode}&buttonId={buttonId}&triggerSplit={triggerSplit}&openAssistant={openAssistant}",
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = EditorMode.RASTER.route },
                navArgument("buttonId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("triggerSplit") { type = NavType.BoolType; defaultValue = false },
                navArgument("openAssistant") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId")
            val mode = backStackEntry.arguments?.getString("mode") ?: EditorMode.RASTER.route
            val buttonId = backStackEntry.arguments?.getString("buttonId")
            val triggerSplit = backStackEntry.arguments?.getBoolean("triggerSplit") ?: false
            val openAssistant = backStackEntry.arguments?.getBoolean("openAssistant") ?: false
            val gridEditorViewModel = hiltViewModel<com.andreas_kratzer.ghosttalk.ui.pages.GridEditorViewModel>()
            if (pageId != null) {
                PageWorkbenchScreen(
                    pageId = pageId,
                    initialMode = mode,
                    initialButtonId = buttonId,
                    initialTriggerSplit = triggerSplit,
                    initialOpenAssistant = openAssistant,
                    pageViewModel = pageViewModel,
                    gridEditorViewModel = gridEditorViewModel,
                    onNavigateBack = { navController.safePopBackStack() },
                    onExitEditor = {
                        val popped = navController.popBackStack("start", inclusive = false)
                        if (!popped) {
                            navController.safePopBackStack()
                        }
                    }
                )
            }
        }
        composable("vocal_training") {
            VocalTrainingScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }
    }
}

private suspend fun resolveStartPage(
    bookId: String,
    settingsRepository: SettingsRepository,
    pageRepository: PageRepository
): com.andreas_kratzer.ghosttalk.core.model.Page? {
    val startId = settingsRepository.defaultStartPageId
    val startPage = if (startId != null) {
        withContext(Dispatchers.IO) { pageRepository.getPageById(startId) }
    } else null
    return startPage ?: withContext(Dispatchers.IO) {
        pageRepository.getPagesForBook(bookId).firstOrNull()
    }
}

private fun runOnMainThread(action: () -> Unit) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        action()
    } else {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
}

private fun NavHostController.safePopBackStack() {
    runOnMainThread {
        val state = currentBackStackEntry?.lifecycle?.currentState
        if (state != null && state.isAtLeast(Lifecycle.State.STARTED)) {
            popBackStack()
        }
    }
}

private fun NavHostController.safeNavigate(route: String) {
    runOnMainThread {
        val currentRoute = currentDestination?.route
        val state = currentBackStackEntry?.lifecycle?.currentState
        if (currentRoute == route) {
            return@runOnMainThread
        }
        if (state != null && state.isAtLeast(Lifecycle.State.STARTED)) {
            val shouldLaunchSingleTop = !route.startsWith("settings")
            navigate(route) {
                launchSingleTop = shouldLaunchSingleTop
            }
        }
    }
}


