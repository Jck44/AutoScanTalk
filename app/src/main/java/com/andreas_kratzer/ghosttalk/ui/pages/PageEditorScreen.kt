package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import kotlinx.coroutines.delay
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    initialButtonId: String? = null,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: ((String) -> Unit)? = null
) {
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = unfilteredPages.find { it.id == pageId }
    val dimensions = LocalDimensions.current

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { 
                    var localName by remember(page.name) { mutableStateOf(page.name) }
                    
                    LaunchedEffect(localName) {
                        if (localName != page.name && localName.isNotBlank()) {
                            delay(500)
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                newName = localName
                            )
                        }
                    }

                    OutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        placeholder = { Text(stringResource(R.string.page_name_label)) },
                        singleLine = true,
                        isError = localName.isBlank(),
                        supportingText = if (localName.isBlank()) {
                            { Text(stringResource(R.string.error_page_name_required)) }
                        } else null,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = dimensions.paddingLarge)
                            .padding(vertical = 4.dp) // Reduce vertical impact
                            .testTag("page_editor_name_field")
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    if (localName.isNotBlank() && localName != page.name) {
                                        pageViewModel.updatePageSettings(
                                            pageId = page.id,
                                            newName = localName
                                        )
                                    } else if (localName.isBlank()) {
                                        localName = page.name // Revert to saved name if left blank
                                    }
                                }
                            }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreR.string.back_button_content_description))
                    }
                }
            )
        }
    ) { paddingValues ->
        val templates by pageViewModel.templates.collectAsState()
        val googleHomeProjectId by pageViewModel.googleHomeProjectId.collectAsState()
        
        GridEditorContent(
            item = page,
            actions = pageViewModel,
            availablePages = unfilteredPages,
            templates = templates,
            featureGuard = pageViewModel.featureGuard,
            bookDefaultScanPattern = bookDefaultScanPattern,
            paddingValues = paddingValues,
            onEditPage = onEditPage,
            googleHomeManager = pageViewModel.googleHomeManager,
            googleHomeProjectId = googleHomeProjectId,
            initialButtonId = initialButtonId
        )
    }
}
