package com.andreas_kratzer.ghosttalk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.ExistingWorkPolicy
import com.andreas_kratzer.ghosttalk.core.KeyEventCoordinator
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.UpdateManager
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SyncWorkRequester
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleProfileSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.SampleDataInitializer
import com.andreas_kratzer.ghosttalk.core.data.impl.UserModeSessionTracker
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.main.AppStartupInitializer
import com.andreas_kratzer.ghosttalk.ui.main.ImportResult
import com.andreas_kratzer.ghosttalk.ui.main.MainAppContent
import com.andreas_kratzer.ghosttalk.ui.main.ScreenStateObserver
import com.andreas_kratzer.ghosttalk.ui.main.SharedZipImportHandler
import com.andreas_kratzer.ghosttalk.ui.pages.CallViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var isDbInitialized by mutableStateOf(false)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var pageRepository: PageRepository
    @Inject lateinit var bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository
    @Inject lateinit var sampleDataInitializer: SampleDataInitializer
    @Inject lateinit var keyEventCoordinator: KeyEventCoordinator
    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var userModeSessionTracker: UserModeSessionTracker
    @Inject lateinit var spotifyManager: SpotifyManager
    @Inject lateinit var rescheduleProfileSyncUseCase: RescheduleProfileSyncUseCase
    @Inject lateinit var backgroundScheduler: com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler
    @Inject lateinit var importExportManager: PageImportExportProvider

    private val bookViewModel: BookViewModel by viewModels()
    private val pageViewModel: PageViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val callViewModel: CallViewModel by viewModels()

    var navControllerForTesting: androidx.navigation.NavHostController? = null

    @Inject lateinit var updateManager: UpdateManager

    @Inject lateinit var sharedZipImportHandler: SharedZipImportHandler
    @Inject lateinit var syncWorkRequester: SyncWorkRequester
    @Inject lateinit var appStartupInitializer: AppStartupInitializer
    @Inject lateinit var authManager: AuthManager
    
    private lateinit var screenStateObserver: ScreenStateObserver

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

        screenStateObserver = ScreenStateObserver(
            activity = this,
            pageViewModel = pageViewModel,
            callViewModel = callViewModel
        )

        // Restore app language preference
        val savedLang = settingsRepository.appLanguage
        val appLocale = if (savedLang == null) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)

        
        com.andreas_kratzer.ghosttalk.core.tts.VoiceDebugger(applicationContext).start()
        userModeSessionTracker.start()

        // Register the launcher so the INSTALL_UPDATE action can trigger the Play Store
        // download dialog even when initiated from within User Mode.
        updateManager.registerLauncher(updateLauncher)

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

        // Manage VocalSwitchService foreground service lifecycle based on User Mode state
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                pageViewModel.isUserModeActive,
                settingsRepository.isVocalSwitchEnabledFlow
            ) { isUserModeActive, isVocalSwitchEnabled ->
                isUserModeActive && isVocalSwitchEnabled
            }.collect { shouldListen ->
                val serviceIntent = Intent(this@MainActivity, com.andreas_kratzer.ghosttalk.core.services.VocalSwitchService::class.java)
                if (shouldListen) {
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        startForegroundService(serviceIntent)
                    } else {
                        Log.w("MainActivity", "Vocal switch enabled but RECORD_AUDIO permission not granted.")
                    }
                } else {
                    stopService(serviceIntent)
                }
            }
        }

        // Android 14+ requires export flags for receivers
        registerReceiver(
            screenOffReceiver, 
            IntentFilter(Intent.ACTION_SCREEN_OFF), 
            RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            val activeBookId = appStartupInitializer.run()
            if (activeBookId != null) {
                pageViewModel.setActiveBookId(activeBookId)
            }
            // Show the UI as soon as the active book is known.
            isDbInitialized = true
            handleIntent(intent)
            // Non-critical maintenance (scheduling, sync, purge) runs after the
            // first frame so it doesn't delay startup.
            appStartupInitializer.runDeferredStartupWork()
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
                Log.d("MainActivity", "manualUpdateCheckTrigger: received, calling checkManualUpdate")
                updateManager.checkManualUpdate(
                    updateLauncher = updateLauncher,
                    onUpdateFound = {
                        Log.i("MainActivity", "checkManualUpdate: update found – download started")
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.UpdateFound)
                    },
                    onUpToDate = {
                        Log.d("MainActivity", "checkManualUpdate: app is up to date")
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.UpToDate)
                    },
                    onNotFromPlayStore = {
                        Log.d("MainActivity", "checkManualUpdate: app not from Play Store")
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.NotFromPlayStore)
                    },
                    onError = { error ->
                        Log.e("MainActivity", "checkManualUpdate: error – $error")
                        settingsViewModel.setUpdateCheckStatus(SettingsViewModel.UpdateCheckStatus.Error(error))
                    }
                )
            }
        }

        // --- Screen Behavior Management ---
        screenStateObserver.startObserving()

        // Timeout check loop
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    securityManager.checkTimeout()
                    delay(60000) // Check every minute
                }
            }
        }

        // Periodic foreground sync check loop
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(60000) // Check every 60 seconds (1 minute)
                    val lastSync = settingsRepository.lastSuccessfulSyncTime
                    val intervalMs = settingsRepository.foregroundSyncIntervalMinutes * 60 * 1000L
                    val now = System.currentTimeMillis()
                    if (now - lastSync >= intervalMs) {
                        if (settingsRepository.isDataCloudSyncEnabled) {
                            Log.d("MainActivity", "Foreground periodic sync check triggered: ${now - lastSync}ms elapsed since last sync (interval: ${intervalMs}ms)")
                            syncWorkRequester.enqueueOneTimeSync(ExistingWorkPolicy.KEEP)
                        } else if (authManager.userEmail.value != null) {
                            Log.d("MainActivity", "Foreground periodic profile sync check triggered: ${now - lastSync}ms elapsed since last sync (interval: ${intervalMs}ms)")
                            rescheduleProfileSyncUseCase.runOnceImmediately()
                        }
                    }
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
            MainAppContent(
                bookViewModel = bookViewModel,
                pageViewModel = pageViewModel,
                settingsViewModel = settingsViewModel,
                callViewModel = callViewModel,
                settingsRepository = settingsRepository,
                pageRepository = pageRepository,
                bookRepository = bookRepository,
                sampleDataInitializer = sampleDataInitializer,
                securityManager = securityManager,
                onNavControllerCreated = { navController ->
                    navControllerForTesting = navController
                }
            )
        }
        
        handleDeepLink(intent)
    }

    override fun onStop() {
        super.onStop()
        if (settingsRepository.isDataCloudSyncEnabled) {
            syncWorkRequester.enqueueOneTimeSync(ExistingWorkPolicy.REPLACE)
        } else if (authManager.userEmail.value != null) {
            rescheduleProfileSyncUseCase.runOnceImmediately()
        }
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
        val isCallActive = callViewModel.callState.value != com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        val isUserMode = pageViewModel.isUserModeActive.value || isCallActive

        if (settingsRepository.blockVolumeKeys && isUserMode) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }

        if (keyEventCoordinator.shouldActivate(event, isUserMode)) {
            if (isCallActive) {
                callViewModel.handleCallButtonPress()
            } else {
                pageViewModel.activateFocusedButton()
            }
            return true
        }
        com.andreas_kratzer.ghosttalk.core.util.InputSourceTracker.isHardwareTriggered = true
        try {
            return super.dispatchKeyEvent(event)
        } finally {
            com.andreas_kratzer.ghosttalk.core.util.InputSourceTracker.isHardwareTriggered = false
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        securityManager.updateActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleIntent(intent)
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

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type
        Log.d("MainActivity", "handleIntent: action = $action, type = $type")
        
        if (Intent.ACTION_SEND == action && type != null) {
            val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            if (uri != null) {
                sharedZipImportHandler.processSharedZip(uri, contentResolver, lifecycleScope) { result ->
                    when (result) {
                        is ImportResult.BookImported -> {
                            pageViewModel.setActiveBookId(result.bookId)
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.shared_import_book_success),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        ImportResult.TtsCacheImported -> {
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.shared_import_tts_success),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        ImportResult.InvalidZip -> {
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.shared_import_invalid_zip),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        is ImportResult.ReadError -> {
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.shared_import_read_error, result.message),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        is ImportResult.Error -> {
                            android.widget.Toast.makeText(
                                this,
                                getString(R.string.shared_import_book_error, result.message),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
