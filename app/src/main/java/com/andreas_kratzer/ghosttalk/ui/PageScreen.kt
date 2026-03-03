package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.andreas_kratzer.ghosttalk.model.Page
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.dp

import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    pageViewModel: PageViewModel, // ViewModel als Parameter
    modifier: Modifier = Modifier
) {
    // States aus dem ViewModel beobachten
    val currentPage by pageViewModel.currentPage.collectAsState()
    val focusedButtonIndex by pageViewModel.focusedButtonIndex.collectAsState()
    val focusedRowIndex by pageViewModel.focusedRowIndex.collectAsState()
    val lastActions by pageViewModel.lastActions.collectAsState()
    val showTestButtons by pageViewModel.showTestButtons.collectAsState()

    val page = currentPage // Zur einfacheren Verwendung

    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_loading))
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
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left side: Button Grid
                Box(modifier = Modifier.weight(0.7f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        pageViewModel = pageViewModel
                    )
                }

                // Right side: Controls and Logs
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (showTestButtons) {
                        ControlButtons(pageViewModel, focusedButtonIndex, focusedRowIndex)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    ActionLogCard(lastActions) { pageViewModel.clearActionLogs() }
                }
            }
        } else {
            Column(
                modifier = modifier
                    .padding(paddingValues)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        pageViewModel = pageViewModel
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (showTestButtons) {
                    ControlButtons(pageViewModel, focusedButtonIndex, focusedRowIndex)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                ActionLogCard(lastActions) { pageViewModel.clearActionLogs() }
            }
        }
    }
}

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    pageViewModel: PageViewModel
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(page.columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(page.buttonConfigs) { globalIndex: Int, buttonConfig: com.andreas_kratzer.ghosttalk.model.ButtonConfig? ->
            val isFocused = globalIndex == focusedButtonIndex
            val isRowFocused = focusedRowIndex != null && (globalIndex / page.columns) == focusedRowIndex

            if (buttonConfig != null && buttonConfig.isActive) {
                GridButton(
                    buttonConfig = buttonConfig,
                    isFocused = isFocused,
                    isRowFocused = isRowFocused,
                    isEditorMode = false,
                    onClick = { pageViewModel.activateButtonAtIndex(globalIndex) }
                )
            } else {
                Spacer(modifier = Modifier.aspectRatio(1f).fillMaxSize())
            }
        }
    }
}

@Composable
fun ControlButtons(
    pageViewModel: PageViewModel,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { pageViewModel.startScanning() }) {
            Text(stringResource(R.string.page_action_start_scan))
        }
        Button(
            onClick = { pageViewModel.activateFocusedButton() },
            enabled = focusedButtonIndex != null || focusedRowIndex != null
        ) {
            Text(stringResource(R.string.page_action_activate_focused))
        }
        Button(onClick = { pageViewModel.stopScanning() }) {
            Text(stringResource(R.string.page_action_stop_scan))
        }
    }
}

@Composable
fun ActionLogCard(
    lastActions: List<String>,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.page_last_actions_title), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClearLogs) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.page_clear_logs_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (lastActions.isEmpty()) {
                Text(stringResource(R.string.page_no_actions_yet))
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
