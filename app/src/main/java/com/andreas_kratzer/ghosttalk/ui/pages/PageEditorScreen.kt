package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: ((String) -> Unit)? = null
) {
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = unfilteredPages.find { it.id == pageId }
    val dimensions = LocalDimensions.current

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    OutlinedTextField(
                        value = page.name,
                        onValueChange = { newName ->
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                newName = newName,
                                newScanPattern = page.scanPattern,
                                newRowNames = page.rowNames,
                                newRows = page.rows,
                                newColumns = page.columns
                            )
                        },
                        label = { Text(stringResource(R.string.page_name_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(end = dimensions.paddingLarge)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button_content_description))
                    }
                }
            )
        }
    ) { paddingValues ->
        val templates by pageViewModel.templates.collectAsState()
        
        GridEditorContent(
            item = page,
            actions = pageViewModel,
            availablePages = unfilteredPages,
            templates = templates,
            featureGuard = pageViewModel.featureGuard,
            bookDefaultScanPattern = bookDefaultScanPattern,
            paddingValues = paddingValues,
            onEditPage = onEditPage
        )
    }
}

@Composable
fun RowNameEditor(initialName: String, onNameChanged: (String) -> Unit) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    val dimensions = LocalDimensions.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.page_editor_row_name_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { 
            if (text != initialName) onNameChanged(text) 
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && text != initialName) {
                    onNameChanged(text)
                }
            }
    )
}
