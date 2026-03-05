package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.domain.SignOutUseCase
import com.andreas_kratzer.ghosttalk.domain.SyncMode
import com.google.api.services.drive.Drive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncSettingsDelegate @Inject constructor(
    private val application: Application,
    private val googleAuthManager: GoogleAuthManager,
    private val setCloudSyncEnabledUseCase: SetCloudSyncEnabledUseCase,
    private val performManualSyncUseCase: PerformManualSyncUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    private val _signInErrorMessage = MutableStateFlow<String?>(null)
    val signInErrorMessage: StateFlow<String?> = _signInErrorMessage.asStateFlow()

    val userEmail = googleAuthManager.userEmail

    fun signIn(context: Context, scope: CoroutineScope) {
        val activity = findActivity(context) ?: return
        scope.launch {
            _signInErrorMessage.value = null
            val result = signInUseCase.execute(activity)
            if (!result) {
                _signInErrorMessage.value = "Anmeldung fehlgeschlagen. SHA-1 korrekt?"
            }
        }
    }

    fun signOut(scope: CoroutineScope) {
        scope.launch {
            signOutUseCase.execute()
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        setCloudSyncEnabledUseCase(enabled)
    }

    fun performManualSync(mode: SyncMode, scope: CoroutineScope, driveOverride: Drive? = null) {
        scope.launch {
            _isSyncing.value = true
            when (val result = performManualSyncUseCase.execute(mode, driveOverride)) {
                is PerformManualSyncUseCase.Result.Success -> {
                    // Handled inside use case (repository update)
                }
                is PerformManualSyncUseCase.Result.RecoverableAuth -> {
                    _authIntentFlow.emit(result.intent)
                }
                is PerformManualSyncUseCase.Result.Error -> {
                    Toast.makeText(application, "Sync Fehler: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
            _isSyncing.value = false
        }
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }
}
