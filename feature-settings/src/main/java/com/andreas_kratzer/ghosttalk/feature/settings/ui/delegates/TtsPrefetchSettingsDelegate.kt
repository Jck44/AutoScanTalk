package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PrefetchStats
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class TtsPrefetchSettingsDelegate @Inject constructor(
    private val ttsHelper: TextToSpeechHelper
) {
    private val _selectedPagesForPrefetch = MutableStateFlow<Set<String>>(emptySet())
    val selectedPagesForPrefetch: StateFlow<Set<String>> = _selectedPagesForPrefetch.asStateFlow()

    private val _isPrefetching = MutableStateFlow(false)
    val isPrefetching: StateFlow<Boolean> = _isPrefetching.asStateFlow()

    private val _prefetchProgress = MutableStateFlow(0f)
    val prefetchProgress: StateFlow<Float> = _prefetchProgress.asStateFlow()

    private val _prefetchCurrentCount = MutableStateFlow(0)
    val prefetchCurrentCount: StateFlow<Int> = _prefetchCurrentCount.asStateFlow()

    private val _prefetchTotalCount = MutableStateFlow(0)
    val prefetchTotalCount: StateFlow<Int> = _prefetchTotalCount.asStateFlow()

    private val _currentPrefetchText = MutableStateFlow<String?>(null)
    val currentPrefetchText: StateFlow<String?> = _currentPrefetchText.asStateFlow()

    private val _prefetchStats = MutableStateFlow<PrefetchStats?>(null)
    val prefetchStats: StateFlow<PrefetchStats?> = _prefetchStats.asStateFlow()

    private var prefetchJob: Job? = null
    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    private fun viewModelScopeLaunch(block: suspend CoroutineScope.() -> Unit): Job {
        val activeScope = checkNotNull(scope) { "TtsPrefetchSettingsDelegate scope has not been initialized. Call initialize(scope) first." }
        return activeScope.launch {
            block()
        }
    }

    fun togglePageSelectionForPrefetch(pageId: String) {
        val current = _selectedPagesForPrefetch.value.toMutableSet()
        if (current.contains(pageId)) {
            current.remove(pageId)
        } else {
            current.add(pageId)
        }
        _selectedPagesForPrefetch.value = current
        _prefetchStats.value = null // Reset stats when selection changes
    }

    fun selectAllPagesForPrefetch(pages: List<Page>) {
        _selectedPagesForPrefetch.value = pages.map { it.id }.toSet()
        _prefetchStats.value = null
    }

    fun deselectAllPagesForPrefetch() {
        _selectedPagesForPrefetch.value = emptySet()
        _prefetchStats.value = null
    }

    fun calculatePrefetchStats(allPages: List<Page>): PrefetchStats {
        val selectedIds = _selectedPagesForPrefetch.value
        val selectedPages = allPages.filter { it.id in selectedIds }
        
        val allButtons = selectedPages.flatMap { it.buttonConfigs.filterNotNull() }
        val textsToSpeak = allButtons.mapNotNull { config ->
            val text = config.spokenText?.takeIf { it.isNotBlank() }
                ?: if (config.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction) config.label else null
            
            text?.takeIf { it.isNotBlank() }
        }

        val totalButtons = textsToSpeak.size
        val uniqueTexts = textsToSpeak.distinct()
        val uniqueStringsCount = uniqueTexts.size
        val duplicateCount = totalButtons - uniqueStringsCount
        
        val totalWords = uniqueTexts.sumOf { it.split(Regex("\\s+")).filter { s -> s.isNotBlank() }.size }
        val totalCharacters = uniqueTexts.sumOf { it.length }
        
        val alreadyCached = uniqueTexts.count { ttsHelper.isCached(it) }

        val stats = PrefetchStats(
            totalButtons = totalButtons,
            uniqueStrings = uniqueStringsCount,
            duplicateStrings = duplicateCount,
            totalWords = totalWords,
            totalCharacters = totalCharacters,
            alreadyCached = alreadyCached
        )
        
        _prefetchStats.value = stats
        return stats
    }

    fun startPrefetch(allPages: List<Page>) {
        val stats = _prefetchStats.value ?: calculatePrefetchStats(allPages)
        val selectedIds = _selectedPagesForPrefetch.value
        val selectedPages = allPages.filter { it.id in selectedIds }
        
        val uniqueTexts = selectedPages.flatMap { it.buttonConfigs.filterNotNull() }
            .mapNotNull { config ->
                config.spokenText?.takeIf { it.isNotBlank() }
                    ?: if (config.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction) config.label else null
            }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { !ttsHelper.isCached(it) }

        if (uniqueTexts.isEmpty()) {
            _prefetchProgress.value = 1f
            _prefetchCurrentCount.value = 0
            _prefetchTotalCount.value = 0
            return
        }

        prefetchJob?.cancel()
        prefetchJob = viewModelScopeLaunch {
            _isPrefetching.value = true
            _prefetchProgress.value = 0f
            _prefetchCurrentCount.value = 0
            _prefetchTotalCount.value = uniqueTexts.size
            
            uniqueTexts.forEachIndexed { index, text ->
                _currentPrefetchText.value = text
                ttsHelper.prefetch(text) // This now suspends until finished
                _prefetchCurrentCount.value = index + 1
                _prefetchProgress.value = (index + 1).toFloat() / uniqueTexts.size
                delay(100) // Small delay to allow UI to breathe
            }
            
            _isPrefetching.value = false
            _currentPrefetchText.value = null
            // Refresh stats to show everything is cached now
            calculatePrefetchStats(allPages)
        }
    }

    fun cancelPrefetch() {
        prefetchJob?.cancel()
        _isPrefetching.value = false
        _currentPrefetchText.value = null
    }
}
