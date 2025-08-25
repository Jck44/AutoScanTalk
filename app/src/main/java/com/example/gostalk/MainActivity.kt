package com.example.gostalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Import für by viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.gostalk.model.Action
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.Page
import com.example.gostalk.ui.PageScreen
import com.example.gostalk.ui.PageViewModel // Import für PageViewModel
import com.example.gostalk.ui.theme.GoSTalkTheme

class MainActivity : ComponentActivity() {

    // ViewModel Instanz holen
    private val pageViewModel: PageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    action = Action.SpeakTextAction("Aktion für Button ${index + 1}"),
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
                    // PageViewModel an PageScreen übergeben
                    PageScreen(
                        pageViewModel = pageViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
