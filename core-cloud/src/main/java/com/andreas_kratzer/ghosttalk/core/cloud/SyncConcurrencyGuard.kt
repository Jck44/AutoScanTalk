package com.andreas_kratzer.ghosttalk.core.cloud

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

object SyncConcurrencyGuard {
    private val localMutex = Mutex()

    /**
     * Verhindert, dass der automatische Background-Worker und der manuelle 
     * Klick in der UI zeitgleich die Cloud-Pipeline manipulieren.
     */
    suspend fun <T> runExclusive(block: suspend () -> T): T? {
        if (localMutex.isLocked) {
            Log.w("SyncMutex", "Synchronisation läuft bereits. Aufruf übersprungen.")
            return null
        }
        return localMutex.withLock { block() }
    }
}
