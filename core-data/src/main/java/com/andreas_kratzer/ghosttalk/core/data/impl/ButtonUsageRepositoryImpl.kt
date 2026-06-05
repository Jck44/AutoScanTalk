package com.andreas_kratzer.ghosttalk.core.data.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MissingPermission")
class ButtonUsageRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ButtonUsageDao,
    private val settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository,
    @param:com.andreas_kratzer.ghosttalk.core.di.ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope,
    private val appDatabase: AppDatabase,
    private val sessionTracker: UserModeSessionTracker
) : ButtonUsageRepository {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var currentWifiSsid: String? = null

    init {
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            
            connectivityManager?.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                        val rawSsid = wifiInfo?.ssid
                        currentWifiSsid = if (rawSsid != null && rawSsid != "<unknown ssid>") {
                            rawSsid.trim('"')
                        } else {
                            null
                        }
                    }

                    override fun onLost(network: Network) {
                        currentWifiSsid = null
                    }
                }
            )
        } catch (_: Exception) {
            // Safe fallback
        }

        scope.launch {
            settingsRepository.lateClickThresholdFlow.collect { threshold ->
                try {
                    dao.updateAccidentalFlags(threshold)
                } catch (_: Exception) {}
            }
        }
    }

    override val buttonHistory: StateFlow<List<ButtonUsageRepository.ButtonUsageEvent>> = 
        settingsRepository.activeBookIdFlow
            .flatMapLatest { bookId ->
                if (bookId == null) kotlinx.coroutines.flow.flowOf(emptyList())
                else dao.getHistoryForBook(bookId).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
            .stateIn(
                scope = scope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    @Suppress("MissingPermission")
    override suspend fun recordUsage(
        bookId: String,
        pageId: String,
        buttonConfig: ButtonConfig,
        rows: Int,
        columns: Int,
        indexInPage: Int,
        timestamp: Long,
        reactionTimeMs: Long?,
        isTouchIntervention: Boolean,
        isHardwareTriggered: Boolean,
        scanCyclesBeforeClick: Int?,
        isAccidental: Boolean,
        intendedButtonId: String?
    ) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val lastLocation = if (hasFine || hasCoarse) {
            try {
                fusedLocationClient.lastLocation.await()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val wifiSsid = if (hasFine || hasCoarse) {
            currentWifiSsid
        } else {
            null
        }

        appDatabase.withTransaction {
            val existing = dao.getStatForButton(bookId, buttonConfig.id)
            val stat = if (existing != null) {
                existing.copy(
                    label = buttonConfig.label,
                    actionJson = json.encodeToString(buttonConfig.buttonAction),
                    pageId = pageId, // Update pageId (last used location)
                    usageCount = existing.usageCount + 1,
                    lastUsedAt = timestamp
                )
            } else {
                ButtonUsageStat(
                    bookId = bookId,
                    buttonConfigId = buttonConfig.id,
                    pageId = pageId,
                    label = buttonConfig.label,
                    actionJson = json.encodeToString(buttonConfig.buttonAction),
                    usageCount = 1,
                    firstUsedAt = timestamp,
                    lastUsedAt = timestamp
                )
            }
            dao.upsert(stat)

            // Persistent history event
            val event = ButtonUsageHistoryEntity(
                bookId = bookId,
                timestamp = timestamp,
                label = buttonConfig.label,
                actionType = buttonConfig.buttonAction::class.simpleName ?: "Unknown",
                buttonId = buttonConfig.id,
                pageId = pageId,
                latitude = lastLocation?.latitude,
                longitude = lastLocation?.longitude,
                sessionId = sessionTracker.currentSessionId,
                reactionTimeMs = reactionTimeMs,
                isTouchIntervention = isTouchIntervention,
                wifiSsid = wifiSsid,
                isHardwareTriggered = isHardwareTriggered,
                scanCyclesBeforeClick = scanCyclesBeforeClick,
                isAccidental = isAccidental,
                intendedButtonId = intendedButtonId
            )
            dao.insertHistoryEvent(event)

            // Pruning based on active statistics retention days instead of strict event count limit.
            // This ensures we keep the full timeline needed for weekly caregivers dashboard and 90-day predictions.
            val retentionDays = settingsRepository.statsRetentionDays
            val threshold = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
            dao.pruneHistoryByTimestamp(threshold)
        }
    }

    override suspend fun markLastUsageAsAccidental(bookId: String): Boolean {
        return appDatabase.withTransaction {
            val lastEvent = dao.getLastHistoryEvent(bookId)
            if (lastEvent != null && !lastEvent.isAccidental) {
                // Find potential intended predecessor button:
                // Find the event preceding this one on the same page, if any.
                val precedingEvents = dao.getRecentHistoryEvents(bookId, 5)
                val currentIdx = precedingEvents.indexOfFirst { it.id == lastEvent.id }
                val intendedButtonId = if (currentIdx != -1 && currentIdx + 1 < precedingEvents.size) {
                    val prev = precedingEvents[currentIdx + 1]
                    if (prev.pageId == lastEvent.pageId) prev.buttonId else null
                } else null
                
                dao.markEventAsAccidental(lastEvent.id, intendedButtonId)
                true
            } else {
                false
            }
        }
    }


    override suspend fun updateLastEventImage(imagePath: String) {
        val bookId = settingsRepository.activeBookId
        val lastEvent = dao.getLastHistoryEvent(bookId)
        lastEvent?.let {
            dao.updateHistoryEvent(it.copy(imagePath = imagePath))
        }
    }

    override suspend fun updateLastEventDetails(details: String) {
        val bookId = settingsRepository.activeBookId
        val lastEvent = dao.getLastHistoryEvent(bookId)
        lastEvent?.let {
            dao.updateHistoryEvent(it.copy(geminiResponse = details))
        }
    }

    override suspend fun deleteUsageEvent(timestamp: Long) {
        dao.deleteHistoryEventByTimestamp(timestamp)
    }

    /**
     * Returns the top N most frequently used buttons for a book.
     */
    override suspend fun getTopActions(bookId: String, limit: Int): List<ButtonUsageStat> {
        return dao.getTopButtons(bookId, limit)
    }

    override suspend fun getGroupedUsageStats(bookId: String): List<GroupedButtonUsageStat> {
        val allStats = dao.getAllStatsForBook(bookId)
        
        return allStats
            .groupBy { it.label.lowercase() to it.actionJson }
            .map { (key, children) ->
                GroupedButtonUsageStat(
                    label = children.maxByOrNull { it.lastUsedAt }?.label ?: children.first().label,
                    actionJson = key.second,
                    totalCount = children.sumOf { it.usageCount },
                    lastUsedAt = children.maxOf { it.lastUsedAt },
                    children = children.sortedByDescending { it.usageCount }
                )
            }
            .sortedByDescending { it.totalCount }
    }

    /**
     * Clears all statistics for a book.
     */
    override suspend fun clearStats(bookId: String) {
        appDatabase.withTransaction {
            dao.clearHistoryForBook(bookId)
            dao.clearStatsForBook(bookId)
        }
    }

    override suspend fun cleanupOldStats(days: Int) {
        if (days <= 0) return
        val threshold = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
        appDatabase.withTransaction {
            dao.pruneHistoryByTimestamp(threshold)
            dao.pruneStatsByTimestamp(threshold)
        }
    }

    override suspend fun getMarkovSuccessors(bookId: String, buttonId: String, limit: Int): List<Pair<String, Int>> {
        val list = dao.getMostFrequentNextButtons(bookId, buttonId, 0L, limit)
        return list.map { item ->
            val label = dao.getStatForButton(bookId, item.buttonId)?.label ?: "Unbekannter Button"
            label to item.count
        }
    }

    private fun calculateDecayWeight(eventTimestamp: Long, now: Long): Double {
        val ageMs = (now - eventTimestamp).coerceAtLeast(0L)
        val ageDays = ageMs.toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
        // Halbwertszeit: 14 Tage
        // lambda = ln(2) / 14 = 0.04951051289
        val lambda = 0.04951051289
        return Math.exp(-lambda * ageDays)
    }

    @Suppress("MissingPermission")
    override suspend fun getPredictiveButtons(bookId: String, limit: Int): List<String> {
        val nowMs = System.currentTimeMillis()
        val recentEvents = dao.getRecentHistoryEvents(bookId, 1000)
        if (recentEvents.isEmpty()) {
            return getTopActions(bookId, limit).map { it.buttonConfigId }
        }

        val lastEvent = recentEvents.firstOrNull()
        val lastButtonId = lastEvent?.buttonId

        // Active Contexts
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val currentWifi = if (hasFine || hasCoarse) {
            currentWifiSsid
        } else {
            null
        }

        val nowDateTime = java.time.LocalDateTime.now()
        val currentHour = nowDateTime.hour
        val sqliteDayOfWeek = if (nowDateTime.dayOfWeek.value == 7) 0 else nowDateTime.dayOfWeek.value
        val activeTimeBin = currentHour / 6 // 4 bins of 6 hours

        val lastLocation = if (hasFine || hasCoarse) {
            try {
                fusedLocationClient.lastLocation.await()
            } catch (_: Exception) { null }
        } else null

        val activeLocKey = if (lastLocation != null) {
            val roundedLat = Math.round(lastLocation.latitude * 1000.0) / 1000.0
            val roundedLng = Math.round(lastLocation.longitude * 1000.0) / 1000.0
            "$roundedLat,$roundedLng"
        } else null

        // Grouping events for Inverted Index calculations
        // 1. WiFi grouping
        val wifiGroups = recentEvents.filter { it.wifiSsid != null }.groupBy { it.wifiSsid!! }
        // 2. Time grouping
        val getTimeBinKey = { timestamp: Long ->
            val dt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault())
            val day = if (dt.dayOfWeek.value == 7) 0 else dt.dayOfWeek.value
            val bin = dt.hour / 6
            "$day:$bin"
        }
        val timeGroups = recentEvents.groupBy { getTimeBinKey(it.timestamp) }
        // 3. Location grouping
        val getLocKey = { lat: Double?, lng: Double? ->
            if (lat != null && lng != null) {
                val roundedLat = Math.round(lat * 1000.0) / 1000.0
                val roundedLng = Math.round(lng * 1000.0) / 1000.0
                "$roundedLat,$roundedLng"
            } else "null"
        }
        val locGroups = recentEvents.groupBy { getLocKey(it.latitude, it.longitude) }
        // 4. Markov grouping (Transitions: Predecessor -> Successor)
        val transitions = recentEvents.zipWithNext().mapNotNull { (successor, predecessor) ->
            val predId = predecessor.buttonId
            val succId = successor.buttonId
            if (predId != null && succId != null) {
                predId to successor
            } else null
        }
        val markovGroups = transitions.groupBy({ it.first }, { it.second })

        // Get unique candidate button IDs from history
        val candidateButtonIds = recentEvents.mapNotNull { it.buttonId }.distinct()
        val scores = mutableMapOf<String, Double>()

        for (btnId in candidateButtonIds) {
            val btnEvents = recentEvents.filter { it.buttonId == btnId }
            val mostRecentEvent = btnEvents.maxByOrNull { it.timestamp } ?: continue
            val decay = calculateDecayWeight(mostRecentEvent.timestamp, nowMs)

            // Markov Score
            var markovScore = 0.0
            if (lastButtonId != null) {
                val activeMarkovEvents = markovGroups[lastButtonId] ?: emptyList()
                val tf = activeMarkovEvents.count { it.buttonId == btnId }
                if (tf > 0) {
                    val df = markovGroups.values.count { grp -> grp.any { it.buttonId == btnId } }
                    val idf = Math.log(1.0 + markovGroups.size.toDouble() / df.coerceAtLeast(1))
                    markovScore = tf * idf
                }
            }

            // WiFi Score
            var wifiScore = 0.0
            if (currentWifi != null) {
                val activeWifiEvents = wifiGroups[currentWifi] ?: emptyList()
                val tf = activeWifiEvents.count { it.buttonId == btnId }
                if (tf > 0) {
                    val df = wifiGroups.values.count { grp -> grp.any { it.buttonId == btnId } }
                    val idf = Math.log(1.0 + wifiGroups.size.toDouble() / df.coerceAtLeast(1))
                    wifiScore = tf * idf
                }
            }

            // Time Score
            var timeScore = 0.0
            val activeTimeKey = "$sqliteDayOfWeek:$activeTimeBin"
            val activeTimeEvents = timeGroups[activeTimeKey] ?: emptyList()
            val tfTime = activeTimeEvents.count { it.buttonId == btnId }
            if (tfTime > 0) {
                val df = timeGroups.values.count { grp -> grp.any { it.buttonId == btnId } }
                val idf = Math.log(1.0 + timeGroups.size.toDouble() / df.coerceAtLeast(1))
                timeScore = tfTime * idf
            }

            // Location Score
            var locScore = 0.0
            val activeLoc = activeLocKey ?: "null"
            val activeLocEvents = locGroups[activeLoc] ?: emptyList()
            val tfLoc = activeLocEvents.count { it.buttonId == btnId }
            if (tfLoc > 0) {
                val df = locGroups.values.count { grp -> grp.any { it.buttonId == btnId } }
                val idf = Math.log(1.0 + locGroups.size.toDouble() / df.coerceAtLeast(1))
                locScore = tfLoc * idf
            }

            // Base frequency score to prevent over-filtering.
            // Even if a button doesn't match current contexts, it gets a baseline score from its general usage frequency.
            val baseTf = btnEvents.size.toDouble()
            val baseScore = baseTf * 0.1 // Grundlegende Gewichtung für allgemeine Häufigkeit

            // Combine scores with custom category importance weights
            val combinedScore = (baseScore + markovScore * 1.5 + wifiScore * 1.3 + timeScore * 1.0 + locScore * 1.1) * decay
            
            scores[btnId] = combinedScore
        }

        // Sort candidates by score
        val predictions = scores.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()

        // Fallback Favorites
        if (predictions.size < limit) {
            // Hole genug Favoriten, um das Limit definitiv aufzufüllen,
            // auch wenn es Überschneidungen mit den Predictions gibt.
            val favorites = getTopActions(bookId, limit + 10)
            for (stat in favorites) {
                if (!predictions.contains(stat.buttonConfigId)) {
                    predictions.add(stat.buttonConfigId)
                }
            }
        }

        return predictions.take(limit)
    }


    override suspend fun getHistoryEventsForBook(bookId: String): List<ButtonUsageRepository.ButtonUsageEvent> {
        return dao.getHistoryEventsForBook(bookId).map { it.toDomain() }
    }


    private fun ButtonUsageHistoryEntity.toDomain() = ButtonUsageRepository.ButtonUsageEvent(

        timestamp = timestamp,
        label = label,
        actionType = actionType,
        imagePath = imagePath,
        buttonId = buttonId,
        pageId = pageId,
        geminiResponse = geminiResponse,
        sessionId = sessionId,
        reactionTimeMs = reactionTimeMs,
        isTouchIntervention = isTouchIntervention,
        wifiSsid = wifiSsid,
        isHardwareTriggered = isHardwareTriggered,
        scanCyclesBeforeClick = scanCyclesBeforeClick,
        isAccidental = isAccidental,
        intendedButtonId = intendedButtonId
    )
}

