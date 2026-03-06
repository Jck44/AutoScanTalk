package com.andreas_kratzer.ghosttalk.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.ui.books.BookListScreen
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageEditorScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageListScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageScreen
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.settings.ContentManagementScreen
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsScreen
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateEditorScreen
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateScreen
import com.andreas_kratzer.ghosttalk.ui.templates.TemplateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GhosTTalkNavHost(
    navController: NavHostController,
    bookViewModel: BookViewModel,
    pageViewModel: PageViewModel,
    settingsViewModel: SettingsViewModel,
    settingsRepository: SettingsRepository,
    pageRepository: PageRepository
) {
    // Handle auto-navigation
    LaunchedEffect(Unit) {
        bookViewModel.autoOpenBookEvent.collect { selectedBookId ->
            if (navController.currentDestination?.route == "book_list") {
                pageViewModel.setActiveBookId(selectedBookId)
                settingsRepository.activeBookId = selectedBookId
                settingsViewModel.refresh()
                navController.navigate("start") {
                    popUpTo("book_list") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "book_list") {
        composable("book_list") {
            BookListScreen(
                bookViewModel = bookViewModel,
                onBookSelected = { selectedBookId ->
                    pageViewModel.setActiveBookId(selectedBookId)
                    settingsRepository.activeBookId = selectedBookId
                    settingsViewModel.refresh()
                    navController.navigate("start")
                }
            )
        }
        composable("start") {
            StartScreen(
                onNavigateToUserMode = {
                    val startId = settingsRepository.defaultStartPageId
                    CoroutineScope(Dispatchers.IO).launch {
                        val startPage = if (startId != null) {
                            pageRepository.getPageById(startId)
                        } else null

                        val finalPage = startPage ?: pageRepository.getPagesForBook(pageViewModel.activeBookId.value ?: "book-default").firstOrNull()
                        
                        if (finalPage != null) {
                            withContext(Dispatchers.Main) {
                                pageViewModel.loadPage(finalPage)
                                navController.navigate("main")
                            }
                        }
                    }
                },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToContentManagement = { navController.navigate("content_management") },
                onNavigateToBooks = { navController.navigate("book_list") }
            )
        }
        composable("content_management") {
            ContentManagementScreen(
                onNavigateToPageManager = { navController.navigate("page_list") },
                onNavigateToTemplateManager = { navController.navigate("templates") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("main") {
            PageScreen(
                pageViewModel = pageViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("templates") {
            val templateViewModel = hiltViewModel<TemplateViewModel>()
            TemplateScreen(
                templateViewModel = templateViewModel,
                onNavigateBack = { navController.popBackStack() },
                onTemplateClick = { templateId ->
                    navController.navigate("template_editor/$templateId")
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
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable("page_list") {
            PageListScreen(
                pageViewModel = pageViewModel,
                onNavigateBack = { navController.popBackStack() },
                onEditPage = { pageId: String ->
                    navController.navigate("page_editor/$pageId")
                }
            )
        }
        composable("page_editor/{pageId}") { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId")
            if (pageId != null) {
                PageEditorScreen(
                    pageId = pageId,
                    pageViewModel = pageViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditPage = { targetPageId ->
                        navController.navigate("page_editor/$targetPageId")
                    }
                )
            }
        }
    }
}
