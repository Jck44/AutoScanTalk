package com.andreas_kratzer.ghosttalk.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkTheme
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalActiveBookId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalCurrentPageId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.CallViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.sections.CallOverlayHost

@Composable
fun MainAppContent(
    bookViewModel: BookViewModel,
    pageViewModel: PageViewModel,
    settingsViewModel: SettingsViewModel,
    callViewModel: CallViewModel,
    settingsRepository: SettingsRepository,
    pageRepository: PageRepository,
    bookRepository: BookRepository,
    sampleDataInitializer: SampleDataInitializer,
    securityManager: SecurityManager,
    onNavControllerCreated: (NavHostController) -> Unit
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val screenState by pageViewModel.screenState.collectAsState()
    val isUserModeActive by pageViewModel.isUserModeActive.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    val currentPageId by pageViewModel.currentPageId.collectAsState()

    val callState by callViewModel.callState.collectAsState()
    val callerName by callViewModel.callerName.collectAsState()
    val callerPhone by callViewModel.callerPhone.collectAsState()
    val callDurationSeconds by callViewModel.callDurationSeconds.collectAsState()
    val isOutgoing by callViewModel.isOutgoing.collectAsState()
    val isHangUpButtonFocused by callViewModel.isHangUpButtonFocused.collectAsState()
    val focusedCallScreenButton by callViewModel.focusedCallScreenButton.collectAsState()
    val isSimulatedCall by callViewModel.isSimulatedCall.collectAsState()

    GhostTalkTheme(themeMode = themeMode) {
        CompositionLocalProvider(
            LocalIsUserModeActive provides isUserModeActive,
            LocalActiveBookId provides activeBookId,
            LocalCurrentPageId provides currentPageId
        ) {
            androidx.activity.compose.BackHandler(enabled = callState != CallState.NONE) {
                // Block back key action during call
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val navController = rememberNavController()
                onNavControllerCreated(navController)

                Box(modifier = Modifier.fillMaxSize()) {
                    GhostTalkNavHost(
                        navController = navController,
                        bookViewModel = bookViewModel,
                        pageViewModel = pageViewModel,
                        settingsViewModel = settingsViewModel,
                        settingsRepository = settingsRepository,
                        pageRepository = pageRepository,
                        bookRepository = bookRepository,
                        sampleDataInitializer = sampleDataInitializer,
                        securityManager = securityManager
                    )

                    if (callState != CallState.NONE) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            CallOverlayHost(
                                callState = callState,
                                callerName = callerName,
                                callerPhone = callerPhone,
                                focusedCallScreenButton = focusedCallScreenButton,
                                callDurationSeconds = callDurationSeconds,
                                isOutgoing = isOutgoing,
                                isHangUpButtonFocused = isHangUpButtonFocused,
                                isSimulatedCall = isSimulatedCall,
                                callViewModel = callViewModel
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = screenState.isBlackOverlayVisible,
                        enter = fadeIn(animationSpec = tween(3000)),
                        exit = fadeOut(animationSpec = tween(500))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_screen_black_overlay_text),
                                color = Color.White.copy(alpha = 0.15f), // Dimly visible
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
