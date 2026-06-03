package com.andreas_kratzer.ghosttalk.core.sync

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.actions.SyncActionProxy
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val performManualSyncUseCase: PerformManualSyncUseCase,
    private val settingsRepository: SettingsRepository
) : SyncActionProxy {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "SystemSyncManager"
    }

    override fun triggerSync() {
        Log.i(TAG, "triggerSync called")
        
        if (!settingsRepository.isCloudSyncEnabled) {
            Log.w(TAG, "Cloud sync is disabled in settings.")
            Toast.makeText(context, "Synchronisation ist in den Einstellungen deaktiviert.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Synchronisation gestartet...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val result = performManualSyncUseCase.execute(SyncMode.TWO_WAY)
            
            withContext(Dispatchers.Main) {
                when (result) {
                    is PerformManualSyncUseCase.Result.Success -> {
                        Log.i(TAG, "Sync finished successfully.")
                        Toast.makeText(context, "Synchronisation erfolgreich abgeschlossen.", Toast.LENGTH_SHORT).show()
                    }
                    is PerformManualSyncUseCase.Result.RecoverableAuth -> {
                        Log.w(TAG, "Sync failed: RecoverableAuth.")
                        Toast.makeText(context, "Sync fehlgeschlagen: Google-Authentifizierung erforderlich.", Toast.LENGTH_LONG).show()
                    }
                    is PerformManualSyncUseCase.Result.Error -> {
                        Log.e(TAG, "Sync error: ${result.message}")
                        Toast.makeText(context, "Sync-Fehler: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
