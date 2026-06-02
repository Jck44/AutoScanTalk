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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
        reactionTimeMs: Long?
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
                reactionTimeMs = reactionTimeMs
            )
            dao.insertHistoryEvent(event)

            // Pruning based on active book settings
            val limit = settingsRepository.actionLogLimit 
            dao.pruneHistory(bookId, limit)
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

    @Suppress("MissingPermission")
    override suspend fun getPredictiveButtons(bookId: String, limit: Int): List<String> {
        val lastEvent = dao.getLastHistoryEvent(bookId)
        val lastButtonId = lastEvent?.buttonId

        val predictions = mutableListOf<String>()

        // 1. Markov Chain
        if (lastButtonId != null) {
            val since90Days = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            val markov = dao.getMostFrequentNextButtons(bookId, lastButtonId, since90Days, limit)
            predictions.addAll(markov.map { it.buttonId })
        }

        // 2. Time Context
        if (predictions.size < limit) {
            val now = java.time.LocalDateTime.now()
            val sqliteDayOfWeek = if (now.dayOfWeek.value == 7) 0 else now.dayOfWeek.value
            val currentHour = now.hour
            val startHour = (currentHour - 1).coerceAtLeast(0)
            val endHour = (currentHour + 2).coerceAtMost(24)
            val since90Days = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)

            val timeStats = dao.getMostFrequentButtonsForContext(
                bookId,
                sqliteDayOfWeek,
                startHour,
                endHour,
                since90Days,
                limit
            )
            for (id in timeStats) {
                if (!predictions.contains(id)) {
                    predictions.add(id)
                }
            }
        }

        // 3. Location Context
        if (predictions.size < limit) {
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

            val since90Days = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            val locationStats = if (lastLocation != null) {
                dao.getMostFrequentButtonsAtLocation(
                    bookId,
                    lastLocation.latitude,
                    lastLocation.longitude,
                    0.005,
                    since90Days,
                    limit
                )
            } else {
                dao.getMostFrequentButtonsAtNullLocation(bookId, since90Days, limit)
            }

            for (id in locationStats) {
                if (!predictions.contains(id)) {
                    predictions.add(id)
                }
            }
        }

        // 4. Fallback Favorites
        if (predictions.size < limit) {
            val favorites = getTopActions(bookId, 5)
            for (stat in favorites) {
                if (!predictions.contains(stat.buttonConfigId)) {
                    predictions.add(stat.buttonConfigId)
                }
            }
        }

        return predictions.take(limit)
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
        reactionTimeMs = reactionTimeMs
    )
}
