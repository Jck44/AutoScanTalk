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
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.Page
import com.example.gostalk.ui.PageScreen
import com.example.gostalk.ui.PageViewModel // Import für PageViewModel
import com.example.gostalk.ui.PageViewModelFactory
import com.example.gostalk.ui.SettingsScreen
import com.example.gostalk.ui.SettingsViewModel
import com.example.gostalk.ui.SettingsViewModelFactory
import com.example.gostalk.ui.theme.GoSTalkTheme
import com.example.gostalk.model.NavigateToPageButtonAction

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsRepository = SettingsRepository(applicationContext)

        // Zweite Seite erstellen
        val secondPage = Page(
            id = "page2",
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

        // ViewModels manuell initialisieren, da wir Repository durchreichen
        val pageMap = mapOf("page1" to samplePage, "page2" to secondPage)
        val pageViewModel: PageViewModel by viewModels {
            PageViewModelFactory(application, pageMap, settingsRepository)
        }

        val settingsViewModel: SettingsViewModel by viewModels {
            SettingsViewModelFactory(application, settingsRepository)
        }

        // Die Startseite ins ViewModel laden
        pageViewModel.loadPage(samplePage)

        setContent {
            GoSTalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            // PageViewModel an PageScreen übergeben
                            PageScreen(
                                pageViewModel = pageViewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
