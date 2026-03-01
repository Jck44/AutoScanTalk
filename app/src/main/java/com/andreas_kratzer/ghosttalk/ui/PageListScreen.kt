import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R

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
    
    // String resources for Toasts (need to be accessed outside Composable for the launcher)
    val importSuccessMsg = stringResource(R.string.page_import_success)
    val exportSuccessMsg = stringResource(R.string.page_export_success)
    val exportErrorMsgTemplate = stringResource(R.string.page_export_error)

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
                            android.widget.Toast.makeText(context, importSuccessMsg, android.widget.Toast.LENGTH_SHORT).show()
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
                    android.widget.Toast.makeText(context, exportSuccessMsg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, exportErrorMsgTemplate.format(e.message), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_content_description)
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
                            Text(stringResource(R.string.action_export_json))
                        }
                        Button(onClick = { importLauncher.launch("application/json") }) {
                            Text(stringResource(R.string.action_import_json))
                        }
                    } else {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_export_json)) }, onClick = { showMenu = false; exportLauncher.launch("GhosTTalk_Export.json") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_import_json)) }, onClick = { showMenu = false; importLauncher.launch("application/json") })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.page_add_description))
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
                                text = stringResource(R.string.page_grid_info, page.rows, page.columns),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { pageToEdit = page }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.page_rename_description),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { pageToDelete = page }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.page_delete_description),
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
                title = { Text(stringResource(R.string.page_dialog_delete_title)) },
                text = { Text(stringResource(R.string.page_dialog_delete_confirm, page.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            pageViewModel.deletePage(page)
                            pageToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToDelete = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
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
            val gridRowLabelTemplate = stringResource(R.string.page_row_label)
            val mutableRowNames = remember { 
                androidx.compose.runtime.mutableStateListOf<String>().apply {
                    val initialNames = page.rowNames
                    for (i in 0 until page.rows) {
                        add(initialNames.getOrNull(i) ?: gridRowLabelTemplate.format(i + 1))
                    }
                }
            }
            var expandedPattern by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { pageToEdit = null },
                title = { Text(stringResource(R.string.page_dialog_settings_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = editPageName,
                            onValueChange = { editPageName = it },
                            label = { Text(stringResource(R.string.page_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val currentPatternLabel = when (editScanPattern) {
                            "linear" -> stringResource(R.string.settings_pattern_linear)
                            "row_by_row" -> stringResource(R.string.settings_pattern_row_by_row)
                            else -> stringResource(R.string.page_pattern_default)
                        }

                        Box {
                            OutlinedTextField(
                                value = currentPatternLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.page_scan_pattern_override)) },
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
                                    text = { Text(stringResource(R.string.page_pattern_default)) },
                                    onClick = { editScanPattern = null; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_pattern_linear)) },
                                    onClick = { editScanPattern = "linear"; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_pattern_row_by_row)) },
                                    onClick = { editScanPattern = "row_by_row"; expandedPattern = false }
                                )
                            }
                        }

                        val effectiveScanPattern = editScanPattern ?: bookDefaultScanPattern
                        if (effectiveScanPattern == "row_by_row") {
                            Text(stringResource(R.string.page_row_announcement_config), style = MaterialTheme.typography.titleSmall)
                            for (i in 0 until page.rows) {
                                OutlinedTextField(
                                    value = mutableRowNames[i],
                                    onValueChange = { mutableRowNames[i] = it },
                                    label = { Text(gridRowLabelTemplate.format(i + 1)) },
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
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToEdit = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
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
        title = { Text(stringResource(R.string.page_dialog_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.page_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rowsStr,
                        onValueChange = { rowsStr = it },
                        label = { Text(stringResource(R.string.page_rows_field)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = columnsStr,
                        onValueChange = { columnsStr = it },
                        label = { Text(stringResource(R.string.page_cols_field)) },
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
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors()
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
