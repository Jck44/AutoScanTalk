package com.andreas_kratzer.ghosttalk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.andreas_kratzer.ghosttalk.core.KeyEventCoordinator
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.UpdateManager
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.data.impl.UserModeSessionTracker
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkTheme
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalActiveBookId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalCurrentPageId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.main.GhostTalkNavHost
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var isDbInitialized by mutableStateOf(false)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var pageRepository: PageRepository
    @Inject lateinit var bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository
    @Inject lateinit var sampleDataInitializer: SampleDataInitializer
    @Inject lateinit var keyEventCoordinator: KeyEventCoordinator
    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var userModeSessionTracker: UserModeSessionTracker
    @Inject lateinit var spotifyManager: SpotifyManager
    @Inject lateinit var backgroundScheduler: com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler

    private val bookViewModel: BookViewModel by viewModels()
    private val pageViewModel: PageViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    var navControllerForTesting: androidx.navigation.NavHostController? = null

    private lateinit var globalPageViewModel: PageViewModel
    @Inject lateinit var updateManager: UpdateManager

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                securityManager.lock()
            }
        }
    }

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.e("MainActivity", "Update flow failed! Result code: ${result.resultCode}")
        }
    }

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Auth consent granted, Gemini should work now.")
        }
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permission request results: $permissions")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.andreas_kratzer.ghosttalk.core.tts.VoiceDebugger(applicationContext).start()
        userModeSessionTracker.start()
        
        // Skip automatic update check if the app starts directly in User Mode,
        // because the update dialog takes over the screen and
        // cannot be dismissed by the user easily, making the app unusable.
        if (settingsRepository.startupBehavior != "USER_MODE") {
            updateManager.checkForUpdates(updateLauncher)
        }

        // Auto-install update if user mode is exited and update is ready
        lifecycleScope.launch {
            pageViewModel.isUserModeActive.collect { isUserModeActive ->
                if (!isUserModeActive && updateManager.updateState.value is com.andreas_kratzer.ghosttalk.core.UpdateState.ReadyToInstall) {
                    updateManager.installDownloadedUpdate()
                }
            }
        }

        // Android 14+ requires export flags for receivers
        registerReceiver(
            screenOffReceiver, 
            IntentFilter(Intent.ACTION_SCREEN_OFF), 
            RECEIVER_NOT_EXPORTED
        )

        globalPageViewModel = pageViewModel

        val defaultBookId = "book-default"

        lifecycleScope.launch {
            // Check if there is already user data (books) before running the initializer
            val hasExistingData = withContext(Dispatchers.IO) {
                bookRepository.getAllBooksList().isNotEmpty()
            }
            if (hasExistingData && !settingsRepository.isSetupCompleted) {
                settingsRepository.isSetupCompleted = true
            }

            // 1. Ensure at least one book exists. returns either default or first existing.
            val initializedBookId = sampleDataInitializer.initializeIfNeeded(defaultBookId)
            
            // 2. Load the user's last active book preference
            val persistedActiveBookId = settingsRepository.activeBookId
            
            // 3. Verify it still exists in the DB
            val finalActiveBookId = if (bookRepository.getBookById(persistedActiveBookId) != null) {
                persistedActiveBookId
            } else {
                // Fallback to the one guaranteed to exist by SampleDataInitializer
                initializedBookId
            }

            // 4. Set the final active book
            settingsRepository.activeBookId = finalActiveBookId
            pageViewModel.setActiveBookId(finalActiveBookId)
            backgroundScheduler.scheduleLocationUpdate()
            backgroundScheduler.scheduleWeatherUpdate()
            isDbInitialized = true
        }

        // Observe Auth Consent Intent
        lifecycleScope.launch {
            pageViewModel.authRecoverIntent.collect { intent ->
                authLauncher.launch(intent)
            }
        }

        // Observe Permission Requests
        lifecycleScope.launch {
            pageViewModel.permissionRequestFlow.collect { permissions ->
                permissionLauncher.launch(permissions)
            }
        }

        // Observe Manual Update Check
        lifecycleScope.launch {
            settingsViewModel.manualUpdateCheckTrigger.collect {
                updateManager.checkManualUpdate(
                    updateLauncher = updateLauncher,
                    onUpdateFound = {
                        settingsViewModel.setUpdateCheckStatus(null) // Reset on success/found
                    },
                    onUpToDate = {
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.UpToDate)
                    },
                    onError = { error ->
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.Error(error))
                    }
                )
            }
        }

        // --- Screen Behavior Management ---
        // We only "apply" the state here. The logic (decision making) resides in the ScreenManagementDelegate.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageViewModel.screenState.collect { state ->
                    if (state.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    val params = window.attributes
                    params.screenBrightness = state.dimAmount ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = params
                }
            }
        }

        // --- Lockscreen Wake Management for Calls ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageViewModel.callState.collect { callState ->
                    val isInCall = callState != com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
                    setShowWhenLocked(isInCall)
                    setTurnScreenOn(isInCall)
                }
            }
        }

        // Timeout check loop
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    securityManager.checkTimeout()
                    delay(60000) // Check every minute
                }
            }
        }


        setContent {
            if (!isDbInitialized) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {}
                return@setContent
            }
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val screenState by pageViewModel.screenState.collectAsState()
            val isUserModeActive by pageViewModel.isUserModeActive.collectAsState()
            val activeBookId by pageViewModel.activeBookId.collectAsState()
            val currentPageId by pageViewModel.currentPageId.collectAsState()

            val callState by pageViewModel.callState.collectAsState()
            val callerName by pageViewModel.callerName.collectAsState()
            val callerPhone by pageViewModel.callerPhone.collectAsState()
            val callDurationSeconds by pageViewModel.callDurationSeconds.collectAsState()
            val isOutgoing by pageViewModel.isOutgoing.collectAsState()
            val isHangUpButtonFocused by pageViewModel.isHangUpButtonFocused.collectAsState()
            val focusedCallScreenButton by pageViewModel.focusedCallScreenButton.collectAsState()
            val isSimulatedCall by pageViewModel.isSimulatedCall.collectAsState()
            
            GhostTalkTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalIsUserModeActive provides isUserModeActive,
                    LocalActiveBookId provides activeBookId,
                    LocalCurrentPageId provides currentPageId
                ) {
                    androidx.activity.compose.BackHandler(enabled = callState != com.andreas_kratzer.ghosttalk.core.call.CallState.NONE) {
                        // Block back key action during call
                    }

                    Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    navControllerForTesting = navController

                    Box(modifier = Modifier.fillMaxSize()) {
                        GhostTalkNavHost(
                            navController = navController,
                            bookViewModel = bookViewModel,
                            pageViewModel = pageViewModel,
                            settingsViewModel = settingsViewModel,
                            settingsRepository = settingsRepository,
                            pageRepository = pageRepository,
                            securityManager = securityManager
                        )

                        if (callState != com.andreas_kratzer.ghosttalk.core.call.CallState.NONE) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                when (callState) {
                                    com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING -> {
                                        com.andreas_kratzer.ghosttalk.ui.pages.sections.IncomingCallOverlay(
                                            callerName = callerName,
                                            callerPhone = callerPhone,
                                            focusedButton = focusedCallScreenButton,
                                            onAnswer = { pageViewModel.systemCallManager.answerCall() },
                                            onReject = { pageViewModel.systemCallManager.hangUp() },
                                            isSimulated = isSimulatedCall
                                        )
                                    }
                                    com.andreas_kratzer.ghosttalk.core.call.CallState.DIALING, 
                                    com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE -> {
                                        com.andreas_kratzer.ghosttalk.ui.pages.sections.ActiveCallOverlay(
                                            callerName = callerName,
                                            callerPhone = callerPhone,
                                            durationSeconds = callDurationSeconds,
                                            isDialing = callState == com.andreas_kratzer.ghosttalk.core.call.CallState.DIALING,
                                            isOutgoing = isOutgoing,
                                            isHangUpFocused = isHangUpButtonFocused,
                                            onHangUp = { pageViewModel.systemCallManager.hangUp() },
                                            isSimulated = isSimulatedCall
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }

                        // The "Black Mode" overlay. 
                        // It stays interactive in terms of hardware/switch events because dispatchKeyEvent 
                        // is handled at the Activity level.
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
        
        handleDeepLink(intent)
    }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
            // Ignore
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        securityManager.updateActivity()
        val isCallActive = if (::globalPageViewModel.isInitialized) {
            globalPageViewModel.systemCallManager.callState.value != com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        } else {
            false
        }
        val isUserMode = if (::globalPageViewModel.isInitialized) {
            globalPageViewModel.isUserModeActive.value || isCallActive
        } else {
            false
        }

        if (keyEventCoordinator.shouldActivate(event, isUserMode)) {
            globalPageViewModel.activateFocusedButton()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        securityManager.updateActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return
        Log.d("MainActivity", "handleDeepLink: data = $data")
        if (data.host == "spotify-callback") {
            lifecycleScope.launch {
                val success = spotifyManager.handleAuthRedirect(data)
                if (success) {
                    Log.i("MainActivity", "Spotify OAuth success callback processed.")
                    settingsViewModel.loadSpotifyPlaylists()
                } else {
                    Log.e("MainActivity", "Spotify OAuth callback processing failed.")
                }
            }
        }
    }
}
