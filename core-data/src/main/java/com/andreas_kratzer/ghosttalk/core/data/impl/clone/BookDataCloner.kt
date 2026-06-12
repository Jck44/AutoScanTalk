package com.andreas_kratzer.ghosttalk.core.data.impl.clone

import android.content.SharedPreferences
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookDataCloner @Inject constructor(
    private val appDatabase: AppDatabase,
    private val prefs: SharedPreferences
) {
    suspend fun cloneSessions(
        sourceBookId: String,
        targetBookId: String
    ): Map<Long, Long> {
        val oldSessions = appDatabase.userModeSessionDao().getSessionsForBookList(sourceBookId)
        val sessionIdMap = mutableMapOf<Long, Long>()
        oldSessions.forEach { oldSession ->
            val clonedSession = UserModeSessionEntity(
                bookId = targetBookId,
                startTime = oldSession.startTime,
                endTime = oldSession.endTime
            )
            val newSessionId = appDatabase.userModeSessionDao().insertSession(clonedSession)
            sessionIdMap[oldSession.id] = newSessionId
        }
        return sessionIdMap
    }

    suspend fun cloneStats(
        sourceBookId: String,
        targetBookId: String,
        buttonIdMap: Map<String, String>,
        mapPageId: (String) -> String,
        skipUnmappedButton: Boolean
    ) {
        val oldStats = appDatabase.buttonUsageDao().getAllStatsForBook(sourceBookId)
        oldStats.forEach { oldStat ->
            val mappedPageId = mapPageId(oldStat.pageId)
            val mappedButtonId = buttonIdMap[oldStat.buttonConfigId] ?: ""
            if (!skipUnmappedButton || mappedButtonId.isNotEmpty()) {
                val clonedStat = oldStat.copy(
                    bookId = targetBookId,
                    buttonConfigId = mappedButtonId,
                    pageId = mappedPageId
                )
                appDatabase.buttonUsageDao().upsert(clonedStat)
            }
        }
    }

    suspend fun cloneHistory(
        sourceBookId: String,
        targetBookId: String,
        buttonIdMap: Map<String, String>,
        sessionIdMap: Map<Long, Long>,
        mapPageId: (String?) -> String?,
        skipUnmappedButton: Boolean
    ) {
        val oldHistory = appDatabase.buttonUsageDao().getRecentHistoryEvents(sourceBookId, 1000)
        oldHistory.forEach { oldEvent ->
            val mappedPageId = mapPageId(oldEvent.pageId)
            val mappedButtonId = buttonIdMap[oldEvent.buttonId]
            val mappedSessionId = oldEvent.sessionId?.let { sessionIdMap[it] }
            
            if (!skipUnmappedButton || mappedButtonId != null) {
                val clonedEvent = oldEvent.copy(
                    id = 0,
                    bookId = targetBookId,
                    buttonId = mappedButtonId ?: "",
                    pageId = mappedPageId,
                    sessionId = mappedSessionId
                )
                appDatabase.buttonUsageDao().insertHistoryEvent(clonedEvent)
            }
        }
    }

    fun cloneBookPrefs(
        sourceBookId: String,
        targetBookId: String,
        mapStartPageId: (String) -> String
    ) {
        val allPrefs = prefs.all
        prefs.edit {
            allPrefs.forEach { (key, value) ->
                if (key.startsWith("${sourceBookId}_")) {
                    val newKey = key.replaceFirst("${sourceBookId}_", "${targetBookId}_")
                    when (value) {
                        is String -> {
                            if (key.endsWith(SettingsConstants.KEY_DEFAULT_START_PAGE_ID)) {
                                putString(newKey, mapStartPageId(value))
                            } else {
                                putString(newKey, value)
                            }
                        }
                        is Boolean -> putBoolean(newKey, value)
                        is Int -> putInt(newKey, value)
                        is Long -> putLong(newKey, value)
                        is Float -> putFloat(newKey, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            putStringSet(newKey, value as Set<String>)
                        }
                    }
                }
            }
        }
    }
}
