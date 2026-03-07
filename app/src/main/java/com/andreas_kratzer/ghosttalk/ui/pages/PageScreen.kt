package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ActionLogCard
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ButtonGrid
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ControlButtons
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    pageViewModel: PageViewModel,
    modifier: Modifier = Modifier
) {
    val currentPage by pageViewModel.resolvedPage.collectAsState()
    val focusedButtonIndex by pageViewModel.focusedButtonIndex.collectAsState()
    val focusedRowIndex by pageViewModel.focusedRowIndex.collectAsState()
    val lastActions by pageViewModel.lastActions.collectAsState()
    val smartPredictions by pageViewModel.smartPredictions.collectAsState()
    val showTestButtons by pageViewModel.showTestButtons.collectAsState()
    val dimensions = LocalDimensions.current

    val page = currentPage

    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_loading))
        }
        return
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        pageViewModel.setUserModeActive(true)

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                pageViewModel.setUserModeActive(false)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                pageViewModel.setUserModeActive(true)
                pageViewModel.resumeScanningIfEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        pageViewModel.resumeScanningIfEnabled()

        onDispose {
            pageViewModel.setUserModeActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            pageViewModel.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = page.name,
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                }
            )
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = modifier
                    .padding(paddingValues)
                    .padding(dimensions.paddingLarge)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
            ) {
                Box(modifier = Modifier.weight(0.7f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        smartPredictions = smartPredictions ?: emptyList(),
                        pageViewModel = pageViewModel
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (showTestButtons) {
                        ControlButtons(
                            onStartScanning = { pageViewModel.startScanning() },
                            onActivateFocused = { pageViewModel.activateFocusedButton() },
                            onStopScanning = { pageViewModel.stopScanning() },
                            isFocused = focusedButtonIndex != null || focusedRowIndex != null
                        )
                        Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                    }
                    ActionLogCard(lastActions) { pageViewModel.clearActionLogs() }
                }
            }
        } else {
            Column(
                modifier = modifier
                    .padding(paddingValues)
                    .padding(dimensions.paddingLarge)
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        smartPredictions = smartPredictions ?: emptyList(),
                        pageViewModel = pageViewModel
                    )
                }
                Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                if (showTestButtons) {
                    ControlButtons(
                        onStartScanning = { pageViewModel.startScanning() },
                        onActivateFocused = { pageViewModel.activateFocusedButton() },
                        onStopScanning = { pageViewModel.stopScanning() },
                        isFocused = focusedButtonIndex != null || focusedRowIndex != null
                    )
                    Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                }
                ActionLogCard(lastActions) { pageViewModel.clearActionLogs() }
            }
        }
    }
}
