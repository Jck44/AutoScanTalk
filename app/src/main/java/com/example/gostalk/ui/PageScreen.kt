package com.example.gostalk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gostalk.model.ButtonConfig // Behalten, falls direkt verwendet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    pageViewModel: PageViewModel, // ViewModel als Parameter
    modifier: Modifier = Modifier
) {
    // States aus dem ViewModel beobachten
    val currentPage by pageViewModel.currentPage.collectAsState()
    val focusedButtonIndex by pageViewModel.focusedButtonIndex.collectAsState()
    val lastActions by pageViewModel.lastActions.collectAsState()
    // Optional: val ttsReady by pageViewModel.ttsReady.collectAsState()

    val page = currentPage // Zur einfacheren Verwendung

    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Seite wird geladen...")
        }
        return
    }

    DisposableEffect(page) {
        pageViewModel.resumeScanningIfEnabled()
        
        onDispose {
            pageViewModel.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page.name) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Button Grid
            LazyVerticalGrid(
            columns = GridCells.Fixed(page.columns),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Nimmt den meisten Platz ein
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(page.buttonConfigs) { globalIndex, buttonConfig ->
                val isFocused = globalIndex == focusedButtonIndex

                if (buttonConfig != null) {
                    GridButton(
                        buttonConfig = buttonConfig,
                        isFocused = isFocused,
                        isEditorMode = false,
                        onClick = { pageViewModel.activateButtonAtIndex(globalIndex) }
                    )
                } else {
                    // Leerer Platzhalter für null ButtonConfig
                    Spacer(modifier = Modifier
                        .aspectRatio(1f)
                        .fillMaxSize())
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Kontroll-Buttons für Testzwecke
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { pageViewModel.startScanning() }) {
                Text("Start Scan")
            }
            Button(
                onClick = { pageViewModel.activateFocusedButton() },
                enabled = focusedButtonIndex != null // Nur aktivieren, wenn ein Button fokussiert ist
            ) {
                Text("Activate Focused")
            }
            Button(onClick = { pageViewModel.stopScanning() }) {
                Text("Stop Scan")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bereich für letzte Aktionen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp), // Feste Größe für den Scrollbereich
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Letzte Aktionen:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (lastActions.isEmpty()) {
                    Text("Noch keine Aktionen ausgeführt.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(lastActions) { actionText ->
                            Text("- $actionText")
                        }
                    }
                }
            }
        }
    }
}
}
