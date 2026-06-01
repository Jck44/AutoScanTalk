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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
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

    LaunchedEffect(page) {
        if (page != null) {
            pageViewModel.loadPage(page)
        }
    }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    var localName by remember(page.name) { mutableStateOf(page.name) }

    val handleNavigateBack = {
        if (localName.isNotBlank()) {
            onNavigateBack()
        }
    }

    BackHandler {
        handleNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { 
                    LaunchedEffect(localName) {
                        if (localName != page.name && localName.isNotBlank()) {
                            delay(500)
                            pageViewModel.updatePageSettings(
                                pageId = page.id,
                                update = GridSettingsUpdate(name = localName)
                            )
                        }
                    }

                    ValidatedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        isRequired = true,
                        errorMessage = stringResource(R.string.error_page_name_required),
                        onFocusLost = {
                            if (it.isNotBlank() && it != page.name) {
                                pageViewModel.updatePageSettings(
                                    pageId = page.id,
                                    update = GridSettingsUpdate(name = it)
                                )
                            }
                        },
                        placeholder = { Text(stringResource(R.string.page_name_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = dimensions.paddingLarge)
                            .padding(vertical = 4.dp) // Reduce vertical impact
                            .testTag("page_editor_name_field")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreR.string.back_button_content_description))
                    }
                },
                actions = {
                    val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()
                    IconButton(
                        onClick = { pageViewModel.toggleAnalyticsOverlay() },
                        modifier = Modifier.testTag("page_editor_analytics_toggle")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.BarChart,
                            contentDescription = stringResource(R.string.page_editor_analytics_toggle),
                            tint = if (isAnalyticsEnabled) {
                                androidx.compose.material3.MaterialTheme.colorScheme.primary
                            } else {
                                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
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
            onEditPage = onEditPage,
            initialButtonId = initialButtonId,
            pageViewModel = pageViewModel
        )
    }
}
