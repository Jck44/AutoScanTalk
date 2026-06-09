package com.andreas_kratzer.ghosttalk.core.scanning

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanTimer @Inject constructor() {
    var scanDelayMillis: Long = 1000L

    suspend fun delayTick() {
        delay(scanDelayMillis)
    }
}
