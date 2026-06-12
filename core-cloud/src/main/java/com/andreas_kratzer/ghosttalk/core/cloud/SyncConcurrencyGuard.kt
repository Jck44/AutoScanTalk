package com.andreas_kratzer.ghosttalk.core.cloud

import android.util.Log
import kotlinx.coroutines.sync.Mutex

object SyncConcurrencyGuard {
    private val localMutex = Mutex()

    /**
     * Verhindert, dass der automatische Background-Worker und der manuelle 
     * Klick in der UI zeitgleich die Cloud-Pipeline manipulieren.
     */
    suspend fun <T> runExclusive(block: suspend () -> T): T? {
        if (!localMutex.tryLock()) {
            Log.w("SyncMutex", "Synchronisation läuft bereits. Aufruf übersprungen.")
            return null
        }
        return try {
            block()
        } finally {
            localMutex.unlock()
        }
    }
}
