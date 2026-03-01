package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.compose.material.icons.filled.Edit
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: (String) -> Unit
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pageToEdit by remember { mutableStateOf<Page?>(null) }
    var pageToDelete by remember { mutableStateOf<Page?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonContent = reader.readText()
                    val targetBookId = activeBookId ?: "book-default"
                    pageViewModel.importFromJson(
                        jsonString = jsonContent,
                        bookId = targetBookId,
                        onSuccess = {
                            android.widget.Toast.makeText(context, "Import erfolgreich!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onError = { errorMsg ->
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val jsonContent = pageViewModel.exportToJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            val writer = OutputStreamWriter(outputStream)
                            writer.write(jsonContent)
                            writer.close()
                        }
                    }
                    android.widget.Toast.makeText(context, "Export erfolgreich!", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Fehler beim Export: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seiten verwalten") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                actions = {
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    if (isLandscape) {
                        Button(
                            onClick = { exportLauncher.launch("GhosTTalk_Export.json") },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Export JSON")
                        }
                        Button(onClick = { importLauncher.launch("application/json") }) {
                            Text("Import JSON")
                        }
                    } else {
                        // In portrait, maybe use a dropdown or just icons if it's too crowded
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Mehr")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Export JSON") }, onClick = { showMenu = false; exportLauncher.launch("GhosTTalk_Export.json") })
                            DropdownMenuItem(text = { Text("Import JSON") }, onClick = { showMenu = false; importLauncher.launch("application/json") })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Neue Seite hinzufügen")
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(allPages) { page ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditPage(page.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = page.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Raster: ${page.rows} Zeilen x ${page.columns} Spalten",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { pageToEdit = page }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Seite umbenennen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { pageToDelete = page }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Seite löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        pageToDelete?.let { page ->
            AlertDialog(
                onDismissRequest = { pageToDelete = null },
                title = { Text("Seite löschen?") },
                text = { Text("Möchtest du die Seite \"${page.name}\" wirklich löschen?") },
                confirmButton = {
                    Button(
                        onClick = {
                            pageViewModel.deletePage(page)
                            pageToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToDelete = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        if (showAddDialog) {
            AddPageDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, rows, cols ->
                    val targetBookId = activeBookId ?: "book-default"
                    val newId = pageViewModel.createNewPage(name, rows, cols, targetBookId)
                    showAddDialog = false
                    onEditPage(newId)
                }
            )
        }

        pageToEdit?.let { page ->
            var editPageName by remember { mutableStateOf(page.name) }
            var editScanPattern by remember { mutableStateOf(page.scanPattern) }
            val mutableRowNames = remember { 
                androidx.compose.runtime.mutableStateListOf<String>().apply {
                    val initialNames = page.rowNames
                    for (i in 0 until page.rows) {
                        add(initialNames.getOrNull(i) ?: "Zeile ${i + 1}")
                    }
                }
            }
            var expandedPattern by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { pageToEdit = null },
                title = { Text("Seiteneinstellungen") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = editPageName,
                            onValueChange = { editPageName = it },
                            label = { Text("Name der Seite") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val currentPatternLabel = when (editScanPattern) {
                            "linear" -> "Button für Button"
                            "row_by_row" -> "Zeilenweise"
                            else -> "Standard (Buch)"
                        }

                        Box {
                            OutlinedTextField(
                                value = currentPatternLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Scanmuster (Überschreiben)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedPattern = true }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { expandedPattern = true })
                            DropdownMenu(
                                expanded = expandedPattern,
                                onDismissRequest = { expandedPattern = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Standard (Buch)") },
                                    onClick = { editScanPattern = null; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Button für Button") },
                                    onClick = { editScanPattern = "linear"; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Zeilenweise") },
                                    onClick = { editScanPattern = "row_by_row"; expandedPattern = false }
                                )
                            }
                        }

                        val effectiveScanPattern = editScanPattern ?: bookDefaultScanPattern
                        if (effectiveScanPattern == "row_by_row") {
                            Text("Zeilen-Ansage konfigurieren:", style = MaterialTheme.typography.titleSmall)
                            for (i in 0 until page.rows) {
                                OutlinedTextField(
                                    value = mutableRowNames[i],
                                    onValueChange = { mutableRowNames[i] = it },
                                    label = { Text("Zeile ${i + 1}") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editPageName.isNotBlank()) {
                                pageViewModel.updatePageSettings(page.id, editPageName, editScanPattern, mutableRowNames.toList())
                                pageToEdit = null
                            }
                        }
                    ) {
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToEdit = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}

@Composable
fun AddPageDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rows: Int, columns: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rowsStr by remember { mutableStateOf("4") }
    var columnsStr by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Seite erstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Seitenname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rowsStr,
                        onValueChange = { rowsStr = it },
                        label = { Text("Zeilen") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = columnsStr,
                        onValueChange = { columnsStr = it },
                        label = { Text("Spalten") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rows = rowsStr.toIntOrNull() ?: 4
                    val cols = columnsStr.toIntOrNull() ?: 4
                    if (name.isNotBlank() && rows > 0 && cols > 0) {
                        onConfirm(name, rows, cols)
                    }
                }
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors()
            ) {
                Text("Abbrechen")
            }
        }
    )
}
