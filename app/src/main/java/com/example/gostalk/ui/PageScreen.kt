package com.example.gostalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gostalk.model.Action
import com.example.gostalk.model.Page

@Composable
fun PageScreen(page: Page, modifier: Modifier = Modifier) {
    val lastActions = remember { mutableStateListOf<String>() }

    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(page.columns),
            modifier = Modifier
                .weight(1f) // Grid nimmt den meisten Platz ein
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(page.buttonConfigs) { index, buttonConfig ->
                if (buttonConfig != null) {
                    Button(
                        onClick = {
                            val actionText = when (val action = buttonConfig.action) {
                                is Action.SpeakTextAction -> "Gedrückt: ${action.textToSpeech}"
                                // Später hier weitere Action-Typen behandeln
                                else -> "Unbekannte Aktion für ${buttonConfig.label}"
                            }
                            if (lastActions.size >= 5) {
                                lastActions.removeAt(0) // Ältesten Eintrag entfernen
                            }
                            lastActions.add(actionText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // Sorgt für quadratische Buttons
                    ) {
                        Text(text = buttonConfig.label)
                    }
                } else {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) // Leerer Platz für einen null-ButtonConfig
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Letzte Aktionen:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Feste Höhe für den Log-Bereich, später anpassbar
                .padding(8.dp)
                .verticalScroll(rememberScrollState()) // Scrollbar machen
        ) {
            if (lastActions.isEmpty()) {
                Text("Noch keine Aktionen ausgeführt.")
            } else {
                lastActions.forEach { actionLog ->
                    Text(actionLog)
                }
            }
        }
    }
}

