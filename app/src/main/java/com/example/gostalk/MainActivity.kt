package com.example.gostalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.gostalk.model.Action
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.Page
import com.example.gostalk.ui.PageScreen // Import für PageScreen
import com.example.gostalk.ui.theme.GoSTalkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoSTalkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Erstelle eine Beispiel-Page für die Vorschau
                    val examplePage = Page(
                        id = "p1",
                        name = "Startseite",
                        rows = 4,
                        columns = 4,
                        buttonConfigs = List(16) { index ->
                            if (index % 5 == 0) null // Jeder 5. Button ist leer
                            else ButtonConfig(
                                id = "btn$index",
                                label = "Button ${index + 1}",
                                auditoryCue = AuditoryCue.TextToSpeechCue("Hinweis für Button ${index + 1}"),
                                action = Action.SpeakTextAction("Aktion für Button ${index + 1}")
                            )
                        }
                    )
                    PageScreen(
                        page = examplePage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() { // Umbenannt von GreetingPreview zu DefaultPreview
    GoSTalkTheme {
        val examplePage = Page(
            id = "p1",
            name = "Vorschau Seite",
            rows = 2,
            columns = 2,
            buttonConfigs = listOf(
                ButtonConfig("btn1", "B1", AuditoryCue.TextToSpeechCue("H1"), Action.SpeakTextAction("A1")),
                null,
                ButtonConfig("btn3", "B3", AuditoryCue.TextToSpeechCue("H3"), Action.SpeakTextAction("A3")),
                ButtonConfig("btn4", "B4", AuditoryCue.TextToSpeechCue("H4"), Action.SpeakTextAction("A4"))
            )
        )
        PageScreen(page = examplePage)
    }
}

