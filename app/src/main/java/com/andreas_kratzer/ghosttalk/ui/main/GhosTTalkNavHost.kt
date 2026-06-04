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
import androidx.navigation.navArgument
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.ContentManagementScreen
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsScreen
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookListScreen
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.AnalyticsDashboardScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageEditorScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageListScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
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
    securityManager: SecurityManager
) {
    val isUnlocked by securityManager.isUnlocked.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pendingRoute = remember { mutableStateOf<String?>(null) }

    val navigateWithSecurity: (String) -> Unit = { route ->
        val isProtected = when {
            route.startsWith("settings") -> securityManager.isSecurityRequiredForSettings()
            route == "analytics_dashboard" -> securityManager.isSecurityRequiredForAnalytics()
            route == "content_management" || 
            route == "page_list" || 
            route == "templates" || 
            route.startsWith("page_editor") || 
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
            if (behavior == "USER_MODE") {
                // Navigate directly to user mode
                val startId = settingsRepository.defaultStartPageId
                val startPage = if (startId != null) {
                    withContext(Dispatchers.IO) { pageRepository.getPageById(startId) }
                } else null

                val finalPage = startPage ?: withContext(Dispatchers.IO) {
                    pageRepository.getPagesForBook(selectedBookId).firstOrNull()
                }
                
                if (finalPage != null) {
                    pageViewModel.loadPage(finalPage)
                    runOnMainThread {
                        navController.navigate("start") {
                            popUpTo("book_list") { inclusive = false }
                        }
                        navController.navigate("main") {
                            launchSingleTop = true
                        }
                    }
                } else {
                    // Fallback to start screen if no pages
                    runOnMainThread {
                        navController.navigate("start") {
                            popUpTo("book_list") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            } else {
                runOnMainThread {
                    navController.navigate("start") {
                        popUpTo("book_list") { inclusive = false }
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
                        navController.navigate("page_editor/${event.pageId}?buttonId=${event.buttonId}")
                    }
                    is SettingsViewModel.SettingsNavigationEvent.JumpToPage -> {
                        navController.navigate("page_editor/${event.pageId}")
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
                onSetupFinished = {
                    settingsRepository.isSetupCompleted = true
                    runOnMainThread {
                        navController.navigate("book_list") {
                            popUpTo("onboarding_setup") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRequestDefaultDialer = { activity ->
                    settingsViewModel.requestDefaultDialer(activity)
                }
            )
        }
        composable("book_list") {
            BookListScreen(
                bookViewModel = bookViewModel,
                settingsRepository = settingsRepository,
                onBookSelected = { selectedBookId ->
                    pageViewModel.setActiveBookId(selectedBookId)
                    settingsRepository.activeBookId = selectedBookId
                    settingsViewModel.refresh()
                    navController.navigate("start")
                },
                onNavigateToGlobalSettings = { navigateWithSecurity("settings?isGlobal=true") }
            )
        }
        composable("start") {
            val activeBookId by pageViewModel.activeBookId.collectAsState()
            val allBooks by bookViewModel.allBooks.collectAsState()
            val activeBook = allBooks.find { it.id == activeBookId }
            StartScreen(
                bookName = activeBook?.name ?: "GhostTalk",
                onNavigateToUserMode = {
                    val startId = settingsRepository.defaultStartPageId
                    coroutineScope.launch {
                        android.util.Log.d("NAV_DEBUG", "onNavigateToUserMode clicked")
                        val startPage = if (startId != null) {
                            withContext(Dispatchers.IO) { pageRepository.getPageById(startId) }
                        } else null

                        val finalPage = startPage ?: withContext(Dispatchers.IO) {
                            pageRepository.getPagesForBook(activeBookId ?: "book-default").firstOrNull()
                        }
                        
                        if (finalPage != null) {
                            pageViewModel.loadPage(finalPage)
                            android.util.Log.d("NAV_DEBUG", "onNavigateToUserMode: loaded page ${finalPage.id}, navigating to main")
                            navigateWithSecurity("main")
                        }
                    }
                },
                onNavigateToSettings = { navigateWithSecurity("settings?isGlobal=false") },
                onNavigateToContentManagement = { navigateWithSecurity("content_management") },
                onNavigateToAnalyticsDashboard = { navigateWithSecurity("analytics_dashboard") },
                onNavigateToBooks = { navController.safePopBackStack() }
            )
        }
        composable("content_management") {
            ContentManagementScreen(
                onNavigateToPageManager = { navController.safeNavigate("page_list") },
                onNavigateToTemplateManager = { navController.safeNavigate("templates") },
                onNavigateBack = { navController.safePopBackStack() }
            )
        }
        composable("analytics_dashboard") {
            AnalyticsDashboardScreen(
                pageViewModel = pageViewModel,
                onNavigateBack = { navController.safePopBackStack() }
            )
        }
        composable("main") {
            PageScreen(
                pageViewModel = pageViewModel,
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
        composable("page_list") {
            PageListScreen(
                pageViewModel = pageViewModel,
                onNavigateBack = { navController.safePopBackStack() },
                onEditPage = { pageId: String ->
                    navController.safeNavigate("page_editor/$pageId")
                },
                onEditTemplate = { templateId: String ->
                    navController.safeNavigate("template_editor/$templateId")
                }
            )
        }
        composable(
            "page_editor/{pageId}?buttonId={buttonId}",
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
                navArgument("buttonId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId")
            val buttonId = backStackEntry.arguments?.getString("buttonId")
            if (pageId != null) {
                PageEditorScreen(
                    pageId = pageId,
                    initialButtonId = buttonId,
                    pageViewModel = pageViewModel,
                    onNavigateBack = { navController.safePopBackStack() },
                    onExitEditor = {
                        navController.popBackStack("page_list", inclusive = false)
                    },
                    onEditPage = { targetPageId, currentButtonId ->
                        if (currentButtonId != null) {
                            navController.currentBackStackEntry?.arguments?.putString("buttonId", currentButtonId)
                        }
                        runOnMainThread {
                            navController.navigate("page_editor/$targetPageId")
                        }
                    }
                )
            }
        }
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
        android.util.Log.d("NAV_DEBUG", "safePopBackStack called. currentDestination=${currentDestination?.route} state=$state")
        if (state != null && state.isAtLeast(Lifecycle.State.STARTED)) {
            popBackStack()
        }
    }
}

private fun NavHostController.safeNavigate(route: String) {
    runOnMainThread {
        val currentRoute = currentDestination?.route
        val state = currentBackStackEntry?.lifecycle?.currentState
        android.util.Log.d("NAV_DEBUG", "safeNavigate called. route=$route currentRoute=$currentRoute state=$state")
        if (currentRoute == route) {
            return@runOnMainThread
        }
        if (state != null && state.isAtLeast(Lifecycle.State.STARTED)) {
            navigate(route) {
                launchSingleTop = true
            }
        }
    }
}


