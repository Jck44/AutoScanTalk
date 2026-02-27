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

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsRepository = SettingsRepository(applicationContext)

        // ViewModels manuell initialisieren, da wir Repository durchreichen
        val pageViewModel: PageViewModel by viewModels {
            PageViewModelFactory(application, emptyMap(), settingsRepository)
        }

        val settingsViewModel: SettingsViewModel by viewModels {
            SettingsViewModelFactory(application, settingsRepository)
        }

        // Beispielseite erstellen (kann später aus einer Datenquelle geladen werden)
        val samplePage = Page(
            id = "page1",
            name = "Hauptseite",
            columns = 4,
            rows = 4, // Wird für LazyVerticalGrid nicht direkt verwendet, aber gut für die Logik
            buttonConfigs = List(16) { index ->
                if (index % 5 == 0) null // Jeden 5. Button leer lassen für Testzwecke
                else ButtonConfig(
                    id = "btn$index",
                    label = "Button ${index + 1}",
                    buttonAction = SpeakTextButtonAction("Aktion für Button ${index + 1}"),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis Button ${index + 1}")
                )
            }
        )

        // Die Seite in das ViewModel laden
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
