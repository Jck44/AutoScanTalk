package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.ui.components.AppBrandHeader
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ActionLogCard
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ButtonGrid
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ControlButtons
import com.andreas_kratzer.ghosttalk.ui.pages.sections.ActiveCallOverlay
import com.andreas_kratzer.ghosttalk.ui.pages.sections.IncomingCallOverlay
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPage by pageViewModel.resolvedPage.collectAsState()
    val focusedButtonIndex by pageViewModel.focusedButtonIndex.collectAsState()
    val focusedRowIndex by pageViewModel.focusedRowIndex.collectAsState()
    val lastActions by pageViewModel.lastActions.collectAsState()
    val showTestButtons by pageViewModel.showTestButtons.collectAsState()
    val isScanning by pageViewModel.isScanning.collectAsState()
    val dimensions = LocalDimensions.current

    // Call Screen States
    val callState by pageViewModel.callState.collectAsState()
    val callerName by pageViewModel.callerName.collectAsState()
    val callerPhone by pageViewModel.callerPhone.collectAsState()
    val callDurationSeconds by pageViewModel.callDurationSeconds.collectAsState()
    val isOutgoing by pageViewModel.isOutgoing.collectAsState()
    val isHangUpButtonFocused by pageViewModel.isHangUpButtonFocused.collectAsState()
    val focusedCallScreenButton by pageViewModel.focusedCallScreenButton.collectAsState()

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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            pageViewModel.setUserModeActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = callState == CallState.NONE) {
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    AppBrandHeader(
                        title = page.name,
                        subtitle = "", // No tagline in user mode
                        isLandscape = true, // Smaller version for TopAppBar
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("page_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreR.string.back_button_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingLarge)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
            ) {
                Box(modifier = Modifier.weight(0.65f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        isScanning = isScanning,
                        pageViewModel = pageViewModel
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
                ) {
                    if (showTestButtons) {
                        ControlButtons(
                            onStartScanning = { pageViewModel.startScanning() },
                            onActivateFocused = { pageViewModel.activateFocusedButton() },
                            onStopScanning = { pageViewModel.stopScanning() },
                            isFocused = focusedButtonIndex != null || focusedRowIndex != null
                        )
                    }
                    ActionLogCard(
                        lastActions = lastActions,
                        onClearLogs = { pageViewModel.clearActionLogs() },
                        actionLogUseCase = pageViewModel.interactionDelegate.actionLogUseCase,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingMedium)
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ButtonGrid(
                        page = page,
                        focusedButtonIndex = focusedButtonIndex,
                        focusedRowIndex = focusedRowIndex,
                        isScanning = isScanning,
                        pageViewModel = pageViewModel
                    )
                }
                Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                if (showTestButtons) {
                    ControlButtons(
                        onStartScanning = { pageViewModel.startScanning() },
                        onActivateFocused = { pageViewModel.activateFocusedButton() },
                        onStopScanning = { pageViewModel.stopScanning() },
                        isFocused = focusedButtonIndex != null || focusedRowIndex != null
                    )
                    Spacer(modifier = Modifier.height(dimensions.paddingLarge))
                }
                ActionLogCard(
                    lastActions = lastActions,
                    onClearLogs = { pageViewModel.clearActionLogs() },
                    actionLogUseCase = pageViewModel.interactionDelegate.actionLogUseCase
                )
            }
        }
    }
}
