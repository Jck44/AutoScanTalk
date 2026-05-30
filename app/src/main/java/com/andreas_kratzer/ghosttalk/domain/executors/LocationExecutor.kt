package com.andreas_kratzer.ghosttalk.domain.executors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale
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
    
    private val prefs = context.getSharedPreferences("location_cache", Context.MODE_PRIVATE)

    private fun persistLocation(location: Location) {
        prefs.edit()
            .putFloat("latitude", location.latitude.toFloat())
            .putFloat("longitude", location.longitude.toFloat())
            .putFloat("accuracy", location.accuracy)
            .putLong("timestamp", location.time)
            .apply()
    }

    private fun getPersistedLocation(): Location? {
        if (!prefs.contains("latitude")) return null
        val lat = prefs.getFloat("latitude", 0f).toDouble()
        val lon = prefs.getFloat("longitude", 0f).toDouble()
        val acc = prefs.getFloat("accuracy", 0f)
        val time = prefs.getLong("timestamp", 0)
        
        return Location("persisted_cache").apply {
            latitude = lat
            longitude = lon
            accuracy = acc
            this.time = time
        }
    }

    fun getPersistedLocationName(): String? {
        return prefs.getString("location_name", null)
    }

    fun persistLocationName(name: String) {
        prefs.edit().putString("location_name", name).apply()
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(refresh: Boolean = false): Location? {
        if (!refresh) {
            val cached = getPersistedLocation()
            if (cached != null) {
                logger.d(TAG, "getCurrentLocation: Returning persisted location")
                return cached
            }
        }
        
        logger.d(TAG, "getCurrentLocation: Fetching fresh location (refresh=$refresh)")
        return try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).asDeferred().await()
            
            if (location != null) {
                persistLocation(location)
                
                // Fetch location name via Geocoder on IO dispatcher
                withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
                            if (name != null) {
                                persistLocationName(name)
                                logger.d(TAG, "Geocoded location name to: $name")
                            }
                        }
                    } catch (e: Exception) {
                        logger.e(TAG, "Failed to geocode location", e)
                    }
                }
            }
            location
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get location", e)
            getPersistedLocation() // Return stale persisted cache if refresh fails
        }
    }

    suspend fun refreshLocation() {
        getCurrentLocation(refresh = true)
    }
}
