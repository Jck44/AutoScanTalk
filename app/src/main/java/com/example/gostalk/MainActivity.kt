package com.example.gostalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Import für by viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gostalk.data.PageRepository
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.Page
import com.example.gostalk.ui.PageScreen
import com.example.gostalk.ui.StartScreen
import com.example.gostalk.ui.PageListScreen
import com.example.gostalk.ui.PageEditorScreen
import com.example.gostalk.ui.PageViewModel // Import für PageViewModel
import com.example.gostalk.ui.PageViewModelFactory
import com.example.gostalk.ui.SettingsScreen
import com.example.gostalk.ui.SettingsViewModel
import com.example.gostalk.ui.SettingsViewModelFactory
import com.example.gostalk.ui.theme.GoSTalkTheme
import com.example.gostalk.model.Book
import com.example.gostalk.ui.BookViewModel
import com.example.gostalk.ui.BookViewModelFactory
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.example.gostalk.tts.VoiceDebugger(this).start()
        
        settingsRepository = SettingsRepository(applicationContext)
        val defaultBookId = "book-default"

        // Zweite Seite erstellen
        val secondPage = Page(
            id = "page2",
            bookId = defaultBookId,
            name = "Zweite Seite",
            columns = 2,
            rows = 2,
            buttonConfigs = listOf(
                ButtonConfig(
                    id = "p2_btn0",
                    label = "Aktion 1",
                    buttonAction = SpeakTextButtonAction("Zweite Seite Aktion 1"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 1")
                ),
                ButtonConfig(
                    id = "p2_btn1",
                    label = "Aktion 2",
                    buttonAction = SpeakTextButtonAction("Zweite Seite Aktion 2"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 2")
                ),
                ButtonConfig(
                    id = "p2_btn2",
                    label = "Aktion 3",
                    buttonAction = SpeakTextButtonAction("Zweite Seite Aktion 3"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Aktion 3")
                ),
                ButtonConfig(
                    id = "p2_btn3",
                    label = "Zurück",
                    buttonAction = NavigateToPageButtonAction(pageId = "page1", ttsFeedback = "Zurück zur Hauptseite"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Hauptseite navigieren")
                )
            )
        )

        // Hauptseite erstellen
        val samplePage = Page(
            id = "page1",
            bookId = defaultBookId,
            name = "Hauptseite",
            columns = 4,
            rows = 4,
            buttonConfigs = List(16) { index ->
                when {
                    index % 5 == 0 -> null // Jeden 5. Button leer lassen für Testzwecke
                    index == 2 -> ButtonConfig( // 3. Button (Index 2) als Navigation zur zweiten Seite
                        id = "btn_nav_page2",
                        label = "Zur Seite 2",
                        buttonAction = NavigateToPageButtonAction(pageId = "page2", ttsFeedback = "Zur zweiten Seite"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Zur zweiten Seite navigieren")
                    )
                    else -> ButtonConfig(
                        id = "btn$index",
                        label = "Button ${index + 1}",
                        buttonAction = SpeakTextButtonAction("Aktion für Button ${index + 1}"),
                        auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Button ${index + 1}")
                    )
                }
            }
        )

        // Database Initialization
        val db = AppDatabase.getDatabase(applicationContext)
        val pageDao = db.pageDao()
        val pageRepository = PageRepository(pageDao)
        val bookDao = db.bookDao()

        // Populate Database if empty
        CoroutineScope(Dispatchers.IO).launch {
            if (bookDao.getBookById(defaultBookId) == null) {
                bookDao.insertBook(Book(id = defaultBookId, name = "Standardbuch"))
            }

            if (pageDao.getAllPages().isEmpty()) {
                pageDao.insertPage(samplePage)
                pageDao.insertPage(secondPage)
            }
        }

        // ViewModels manuell initialisieren, da wir Repository durchreichen
        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(application, bookDao)
        }

        val pageViewModel: PageViewModel by viewModels {
            PageViewModelFactory(application, pageRepository, settingsRepository)
        }
        pageViewModel.setActiveBookId(defaultBookId)

        val settingsViewModel: SettingsViewModel by viewModels {
            SettingsViewModelFactory(application, settingsRepository)
        }

        setContent {
            GoSTalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "book_list") {
                        composable("book_list") {
                            com.example.gostalk.ui.BookListScreen(
                                bookViewModel = bookViewModel,
                                onBookSelected = { selectedBookId ->
                                    // 1. Set the active book globally
                                    pageViewModel.setActiveBookId(selectedBookId)
                                    // 2. Navigate to Mode Selection (StartScreen)
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
                                        
                                        // Wir müssen sicherstellen, dass die gefundene Seite auch zum aktuellen Buch gehört!
                                        val finalPage = startPage ?: pageRepository.getPagesForBook(pageViewModel.activeBookId.value ?: "book-default").firstOrNull() ?: samplePage
                                        
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            pageViewModel.loadPage(finalPage)
                                            navController.navigate("main")
                                        }
                                    }
                                },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToBooks = { navController.navigate("book_list") }
                            )
                        }
                        composable("main") {
                            // PageViewModel an PageScreen übergeben
                            PageScreen(
                                pageViewModel = pageViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                pageViewModel = pageViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPageManager = { navController.navigate("page_list") }
                            )
                        }
                        composable("page_list") {
                            PageListScreen(
                                pageViewModel = pageViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onEditPage = { pageId ->
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
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
