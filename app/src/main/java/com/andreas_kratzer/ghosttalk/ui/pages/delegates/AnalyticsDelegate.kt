package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.EfficiencyAnalyzer
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsDelegate @Inject constructor(
    private val buttonUsageRepository: ButtonUsageRepository,
    private val userModeSessionRepository: UserModeSessionRepository,
    private val pathAnalyzer: PathAnalyzer,
    private val settingsRepository: SettingsRepository,
    private val efficiencyAnalyzer: EfficiencyAnalyzer,
    private val pageManagementDelegate: PageManagementDelegate
) {
    private lateinit var scope: CoroutineScope

    val activeBookId = pageManagementDelegate.activeBookId
    val unfilteredPages = pageManagementDelegate.unfilteredPages

    val buttonHistory = buttonUsageRepository.buttonHistory

    private val _userModeSessions = MutableStateFlow<List<UserModeSession>>(emptyList())
    val userModeSessions: StateFlow<List<UserModeSession>> = _userModeSessions.asStateFlow()

    private val _isCalculatingRecommendations = MutableStateFlow(false)
    val isCalculatingRecommendations: StateFlow<Boolean> = _isCalculatingRecommendations.asStateFlow()

    private val _shortcutRecommendations = MutableStateFlow<List<PathAnalyzer.ShortcutRecommendation>>(emptyList())
    val shortcutRecommendations: StateFlow<List<PathAnalyzer.ShortcutRecommendation>> = _shortcutRecommendations.asStateFlow()

    private val _isAnalyticsOverlayEnabled = MutableStateFlow(false)
    val isAnalyticsOverlayEnabled = _isAnalyticsOverlayEnabled.asStateFlow()

    private val _pageMetrics = MutableStateFlow<Map<String, ButtonEffortMetrics>>(emptyMap())
    val pageMetrics: StateFlow<Map<String, ButtonEffortMetrics>> = _pageMetrics.asStateFlow()

    fun init(coroutineScope: CoroutineScope, resolvedPage: StateFlow<Page?>) {
        this.scope = coroutineScope

        scope.launch {
            activeBookId.flatMapLatest { bookId ->
                if (bookId == null) flowOf(emptyList())
                else userModeSessionRepository.getSessionsForBook(bookId)
            }.collect {
                _userModeSessions.value = it
            }
        }

        scope.launch {
            combine(
                buttonHistory,
                unfilteredPages,
                activeBookId
            ) { history, allPages, bookId ->
                _isCalculatingRecommendations.value = true
                try {
                    if (bookId != null && history.isNotEmpty() && allPages.isNotEmpty()) {
                        val delay = settingsRepository.scanDelayMillis
                        val startPageId = settingsRepository.defaultStartPageId
                        pathAnalyzer.analyzePaths(history, allPages, delay, startPageId)
                    } else {
                        emptyList()
                    }
                } finally {
                    _isCalculatingRecommendations.value = false
                }
            }
            .flowOn(Dispatchers.Default)
            .collect {
                _shortcutRecommendations.value = it
            }
        }

        scope.launch {
            combine(
                resolvedPage,
                activeBookId,
                buttonHistory
            ) { page, bookId, history ->
                Triple(page, bookId, history)
            }
            .flatMapLatest { (page, bookId, history) ->
                if (page == null || bookId == null) {
                    flowOf(emptyMap())
                } else {
                    kotlinx.coroutines.flow.flow {
                        try {
                            val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                            val clickCounts = stats.flatMap { it.children }
                                .associate { it.buttonConfigId to it.usageCount }
                            val delay = settingsRepository.scanDelayMillis
                            val pattern = settingsRepository.defaultScanPattern
                            val allPages = pageManagementDelegate.unfilteredPages.value
                            val startPageId = settingsRepository.defaultStartPageId
                            val metrics = efficiencyAnalyzer.calculatePageMetrics(
                                page = page,
                                allPages = allPages,
                                startPageId = startPageId,
                                clickCounts = clickCounts,
                                scanDelayMs = delay,
                                defaultScanPattern = pattern,
                                historyEvents = history
                            )
                            emit(metrics)
                        } catch (e: Exception) {
                            Log.e("AnalyticsDelegate", "Error analyzing page efficiency", e)
                            emit(emptyMap())
                        }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .collect {
                _pageMetrics.value = it
            }
        }
    }

    fun clearUserModeSessions() {
        scope.launch {
            val bookId = activeBookId.value
            if (bookId != null) {
                userModeSessionRepository.clearSessions(bookId)
            }
        }
    }

    fun toggleAnalyticsOverlay() {
        _isAnalyticsOverlayEnabled.value = !_isAnalyticsOverlayEnabled.value
        Log.d("AnalyticsDelegate", "toggleAnalyticsOverlay: enabled = ${_isAnalyticsOverlayEnabled.value}")
    }

    fun applyShortcutRecommendation(
        recommendation: PathAnalyzer.ShortcutRecommendation,
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                val page = pageManagementDelegate.getPageById(recommendation.sourcePageId)
                if (page == null) {
                    onResult(false, "Quellseite nicht gefunden.")
                    return@launch
                }
                
                val emptyIndex = page.buttonConfigs.indexOfFirst { it == null }
                if (emptyIndex == -1 || emptyIndex >= com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                    onResult(false, "Die Quellseite ist bereits voll (alle Kachel-Slots belegt).")
                    return@launch
                }
                
                val newConfig = recommendation.targetButtonConfig.copy(
                    id = java.util.UUID.randomUUID().toString()
                )
                
                pageManagementDelegate.insertButtonConfig(
                    pageId = recommendation.sourcePageId,
                    index = emptyIndex,
                    newConfig = newConfig,
                    forceShift = false
                ) { success ->
                    if (success) {
                        onResult(true, "Abkürzung erfolgreich auf Seite '${recommendation.sourcePageName}' erstellt.")
                    } else {
                        onResult(false, "Fehler beim Erstellen der Kachel.")
                    }
                }
            } catch (e: Exception) {
                Log.e("AnalyticsDelegate", "Error applying shortcut", e)
                onResult(false, "Fehler: ${e.localizedMessage}")
            }
        }
    }
}
