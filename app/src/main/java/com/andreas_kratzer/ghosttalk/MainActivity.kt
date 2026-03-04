package com.andreas_kratzer.ghosttalk

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.andreas_kratzer.ghosttalk.ui.main.*
import com.andreas_kratzer.ghosttalk.ui.books.BookViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.KeyEventCoordinator
import com.andreas_kratzer.ghosttalk.core.UpdateManager
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.Book
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.data.SampleDataInitializer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var pageRepository: PageRepository
    @Inject lateinit var bookRepository: com.andreas_kratzer.ghosttalk.data.BookRepository
    @Inject lateinit var sampleDataInitializer: SampleDataInitializer
    @Inject lateinit var keyEventCoordinator: KeyEventCoordinator

    private val bookViewModel: BookViewModel by viewModels()
    private val pageViewModel: PageViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var globalPageViewModel: PageViewModel
    private lateinit var updateManager: UpdateManager

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.andreas_kratzer.ghosttalk.tts.VoiceDebugger(applicationContext).start()
        
        updateManager = UpdateManager(this)
        updateManager.checkForUpdates(updateLauncher)


        val defaultBookId = "book-default"

        lifecycleScope.launch {
            sampleDataInitializer.initializeIfNeeded(defaultBookId)
        }

        globalPageViewModel = pageViewModel
        pageViewModel.setActiveBookId(defaultBookId)
        settingsRepository.activeBookId = defaultBookId

        // Observe Auth Consent Intent
        lifecycleScope.launch {
            pageViewModel.authRecoverIntent.collect { intent ->
                authLauncher.launch(intent)
            }
        }


        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            
            GhosTTalkTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    GhosTTalkNavHost(
                        navController = navController,
                        bookViewModel = bookViewModel,
                        pageViewModel = pageViewModel,
                        settingsViewModel = settingsViewModel,
                        settingsRepository = settingsRepository,
                        pageRepository = pageRepository
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.resumeUpdateIfInProgress()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::globalPageViewModel.isInitialized && keyEventCoordinator.shouldActivate(event)) {
            globalPageViewModel.activateFocusedButton()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
