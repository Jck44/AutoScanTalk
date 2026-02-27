package com.example.gostalk.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gostalk.model.ButtonConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val page = allPages.find { it.id == pageId }

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Seite nicht gefunden oder wird geladen...")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor: ${page.name}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Tippe auf ein Feld, um den Button zu konfigurieren.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Button Grid for editing
            LazyVerticalGrid(
                columns = GridCells.Fixed(page.columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(page.buttonConfigs) { globalIndex, buttonConfig ->
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                selectedButtonIndex = globalIndex
                                showDialog = true
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (buttonConfig != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = buttonConfig?.label ?: "+",
                                textAlign = TextAlign.Center,
                                style = if (buttonConfig != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displayMedium,
                                color = if (buttonConfig != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog && selectedButtonIndex != null) {
        val editingIndex = selectedButtonIndex!!
        val currentConfig = page.buttonConfigs.getOrNull(editingIndex)
        val buttonId = currentConfig?.id ?: UUID.randomUUID().toString()

        ButtonConfigDialog(
            initialConfig = currentConfig,
            buttonId = buttonId,
            availablePages = allPages,
            onDismiss = {
                showDialog = false
                selectedButtonIndex = null
            },
            onSave = { newConfig ->
                pageViewModel.updateButtonConfig(page.id, editingIndex, newConfig)
                showDialog = false
                selectedButtonIndex = null
            }
        )
    }
}
