package com.andreas_kratzer.ghosttalk.domain.executors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.asDeferred
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: com.andreas_kratzer.ghosttalk.core.util.Logger
) {
    private val TAG = "LocationExecutor"
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private var cachedLocation: Location? = null

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(refresh: Boolean = false): Location? {
        if (!refresh && cachedLocation != null) {
            logger.d(TAG, "getCurrentLocation: Returning cached location")
            return cachedLocation
        }
        
        logger.d(TAG, "getCurrentLocation: Fetching fresh location (refresh=$refresh)")
        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).asDeferred().await()
            if (location != null) {
                cachedLocation = location
            }
            location
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get location", e)
            cachedLocation // Return stale cache if refresh fails
        }
    }

    suspend fun refreshLocation() {
        getCurrentLocation(refresh = true)
    }
}
