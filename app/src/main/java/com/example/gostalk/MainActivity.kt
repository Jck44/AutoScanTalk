package com.example.gostalk

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.example.gostalk.ui.PageViewModel
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
    private lateinit var globalPageViewModel: PageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.example.gostalk.tts.VoiceDebugger(applicationContext).start()
        
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
                    spokenText = "Zurück zur Hauptseite",
                    buttonAction = NavigateToPageButtonAction(pageId = "page1"),
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
                        spokenText = "Zur zweiten Seite",
                        buttonAction = NavigateToPageButtonAction(pageId = "page2"),
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

        // ViewModels mit Dependencies initialisieren
        val bookViewModel: BookViewModel by viewModels {
            BookViewModelFactory(application, bookDao)
        }

        val pageViewModel: PageViewModel by viewModels {
            PageViewModelFactory(application, pageRepository, settingsRepository)
        }
        globalPageViewModel = pageViewModel
        pageViewModel.setActiveBookId(defaultBookId)
        settingsRepository.activeBookId = defaultBookId

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
                                    // 1. Set the active book globally for Pages
                                    pageViewModel.setActiveBookId(selectedBookId)
                                    // 2. Set the active book globally for Settings and Refresh UI State
                                    settingsRepository.activeBookId = selectedBookId
                                    settingsViewModel.refresh()
                                    // 3. Navigate to Mode Selection (StartScreen)
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
                                onNavigateToPageManager = { navController.navigate("page_list") },
                                onNavigateToBooks = { navController.navigate("book_list") }
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
                                settingsViewModel = settingsViewModel,
                                pageViewModel = pageViewModel,
                                onNavigateBack = { navController.popBackStack() }
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

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::globalPageViewModel.isInitialized) {
            val volumeActivate = settingsRepository.volumeKeysActivate
            val switchKey = settingsRepository.switchActivationKey.trim()
            val keyCode = event.keyCode

            val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

            val isSwitchKey = when (switchKey.lowercase()) {
                "space", "leertaste" -> keyCode == KeyEvent.KEYCODE_SPACE
                "enter", "return" -> keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                else -> {
                    val pressedChar = event.displayLabel.toString()
                    switchKey.isNotEmpty() && pressedChar.equals(switchKey, ignoreCase = true)
                }
            }

            if ((volumeActivate && isVolumeKey) || isSwitchKey) {
                globalPageViewModel.activateFocusedButton()
                return true // Event konsumieren
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
