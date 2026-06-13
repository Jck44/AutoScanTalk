package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.OptionalProperty
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalFilter
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageSplitDelegate @Inject constructor(
    private val application: Application,
    private val splitPageUseCase: SplitPageUseCase,
    private val settingsRepository: SettingsRepository,
    private val pageLayoutOptimizer: PageLayoutOptimizer,
    private val pageManagementDelegate: PageManagementDelegate,
    private val createPageUseCase: CreatePageUseCase
) {
    private lateinit var scope: CoroutineScope

    private val _pageSplitProposal = MutableStateFlow<SplitPageUseCase.PageSplitProposal?>(null)
    val pageSplitProposal: StateFlow<SplitPageUseCase.PageSplitProposal?> = _pageSplitProposal.asStateFlow()

    private val _isPageSplitLoading = MutableStateFlow(false)
    val isPageSplitLoading: StateFlow<Boolean> = _isPageSplitLoading.asStateFlow()

    private val _currentProposalFilter = MutableStateFlow(ProposalFilter.ALL)
    val currentProposalFilter: StateFlow<ProposalFilter> = _currentProposalFilter.asStateFlow()

    private val _currentProposalSort = MutableStateFlow(ProposalSort.TIME_SAVED_DESC)
    val currentProposalSort: StateFlow<ProposalSort> = _currentProposalSort.asStateFlow()

    private val _layoutOptimizationProposals = MutableStateFlow<List<PageLayoutOptimizer.LayoutOptimizationProposal>>(emptyList())
    val layoutOptimizationProposals: StateFlow<List<PageLayoutOptimizer.LayoutOptimizationProposal>> = _layoutOptimizationProposals.asStateFlow()

    fun init(
        coroutineScope: CoroutineScope,
        unfilteredPages: StateFlow<List<Page>>,
        activeBookId: StateFlow<String?>,
        buttonHistory: Flow<List<com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent>>
    ) {
        this.scope = coroutineScope

        scope.launch {
            combine(
                unfilteredPages,
                activeBookId,
                currentProposalFilter,
                currentProposalSort,
                buttonHistory
            ) { allPages, bookId, filter, sort, historyEvents ->
                if (bookId != null && allPages.isNotEmpty()) {
                    val delay = settingsRepository.scanDelayMillis
                    val pattern = settingsRepository.defaultScanPattern
                    val startPageId = settingsRepository.defaultStartPageId
                    val raw = pageLayoutOptimizer.analyzePages(allPages, startPageId, delay, pattern, historyEvents)
                    val filtered = raw.filter { proposal ->
                        when (filter) {
                            ProposalFilter.ALL -> true
                            ProposalFilter.SPLIT_ONLY -> proposal is PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal
                            ProposalFilter.PATTERN_ONLY -> proposal is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal
                            ProposalFilter.START_PAGE_ONLY -> proposal.pageId == startPageId
                            ProposalFilter.CRITICAL_ONLY -> {
                                val currentScanTime = when (proposal) {
                                    is PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.currentAverageScanTimeSec
                                    is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.currentAverageScanTimeSec
                                    is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> (proposal.currentScanDelayMs / 1000.0) * 4.0
                                    is PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 6.0
                                }
                                currentScanTime > 8.0
                            }
                        }
                    }
                    when (sort) {
                        ProposalSort.TIME_SAVED_DESC -> filtered.sortedByDescending { proposal ->
                            when (proposal) {
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal ->
                                    proposal.currentAverageScanTimeSec - proposal.estimatedNewAverageScanTimeSec
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal ->
                                    proposal.currentAverageScanTimeSec - proposal.estimatedNewAverageScanTimeSec
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal ->
                                    1.5
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal ->
                                    proposal.accidentalClickCount * 0.5
                            }
                        }
                        ProposalSort.CURRENT_TIME_DESC -> filtered.sortedByDescending { proposal ->
                            when (proposal) {
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.currentAverageScanTimeSec
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.currentAverageScanTimeSec
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> proposal.currentScanDelayMs / 1000.0
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 0.0
                            }
                        }
                        ProposalSort.PAGE_NAME_ASC -> filtered.sortedBy { it.pageName.lowercase() }
                        ProposalSort.BUTTONS_COUNT_DESC -> filtered.sortedByDescending { proposal ->
                            when (proposal) {
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.activeButtonsCount.toDouble()
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.activeButtonsCount.toDouble()
                                is PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> 0.0
                                is PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 0.0
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            }
            .flowOn(Dispatchers.Default)
            .collect {
                _layoutOptimizationProposals.value = it
            }
        }
    }

    fun generatePageSplitPrompt(buttonLabels: List<String>): String {
        return splitPageUseCase.generatePrompt(buttonLabels)
    }

    fun parsePageSplitProposal(response: String) {
        try {
            val parsed = splitPageUseCase.parseResponse(response)
            _pageSplitProposal.value = parsed
        } catch (e: Exception) {
            Log.e("PageSplitDelegate", "Error parsing page split proposal", e)
            throw e
        }
    }

    fun clearPageSplitProposal() {
        _pageSplitProposal.value = null
    }

    fun setProposalFilter(filter: ProposalFilter) {
        _currentProposalFilter.value = filter
    }

    fun setProposalSort(sort: ProposalSort) {
        _currentProposalSort.value = sort
    }

    fun changePageScanPattern(pageId: String, pattern: String) {
        scope.launch {
            try {
                pageManagementDelegate.updatePageSettings(
                    pageId = pageId,
                    update = GridSettingsUpdate(
                        scanPattern = OptionalProperty(pattern)
                    )
                )
            } catch (e: Exception) {
                Log.e("PageSplitDelegate", "Failed to update page scan pattern", e)
            }
        }
    }

    fun changeScanDelay(delayMs: Long) {
        scope.launch {
            try {
                settingsRepository.scanDelayMillis = delayMs
            } catch (e: Exception) {
                Log.e("PageSplitDelegate", "Failed to update scan delay", e)
            }
        }
    }

    fun applySpacerRelocate(pageId: String, buttonId: String, intendedButtonId: String) {
        scope.launch {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val fromIndex = page.buttonConfigs.indexOfFirst { it?.id == buttonId }
                val toIndex = page.buttonConfigs.indexOfFirst { it?.id == intendedButtonId }
                if (fromIndex != -1 && toIndex != -1) {
                    pageManagementDelegate.moveButton(pageId, fromIndex, toIndex)
                }
            } catch (e: Exception) {
                Log.e("PageSplitDelegate", "Failed to swap buttons for spacer relocate proposal", e)
            }
        }
    }

    fun shouldFilterButtonFromSplit(buttonConfig: ButtonConfig?, defaultStartPageId: String?, currentPageId: String?): Boolean {
        if (buttonConfig == null || !buttonConfig.isActive || buttonConfig.label.isBlank()) return true
        val action = buttonConfig.buttonAction
        if (action is NavigateToPageButtonAction) {
            // Filter out circular back-to-start navigation
            if (action.pageId == defaultStartPageId) return true
            // Filter out circular navigation pointing to this page itself
            if (action.pageId == currentPageId) return true
            // Filter out typical back/exit navigation labels
            val labelLower = buttonConfig.label.lowercase().trim()
            if (labelLower == "zurück zum start" || labelLower == "zurück" || labelLower == "back") return true
        }
        return false
    }

    fun generatePageSplitProposal(pageId: String) {
        scope.launch {
            _isPageSplitLoading.value = true
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val defaultStartPageId = settingsRepository.defaultStartPageId
                val labels = page.buttonConfigs
                    .filter { !shouldFilterButtonFromSplit(it, defaultStartPageId, pageId) }
                    .map { it!!.label }
                
                if (labels.isEmpty()) {
                    _isPageSplitLoading.value = false
                    return@launch
                }
                
                val result = splitPageUseCase.execute(labels)
                _pageSplitProposal.value = result
            } catch (e: Exception) {
                Log.e("PageSplitDelegate", "Error generating page split proposal", e)
                Toast.makeText(application, "Fehler beim Erstellen des Vorschlags: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isPageSplitLoading.value = false
            }
        }
    }

    fun applyPageSplit(pageId: String, proposal: SplitPageUseCase.PageSplitProposal) {
        scope.launch(Dispatchers.IO) {
            try {
                _isPageSplitLoading.value = true
                val sourcePageVal = pageManagementDelegate.getPageById(pageId) ?: throw IllegalArgumentException("Source page not found")
                val bookIdVal = sourcePageVal.bookId
                val preSplitPages = pageManagementDelegate.pageRepository.getPagesForBook(bookIdVal)

                val finalSourcePage = pageManagementDelegate.pageRepository.runInTransaction {
                    val sourcePage = pageManagementDelegate.getPageById(pageId) ?: throw IllegalArgumentException("Source page not found")
                    val bookId = sourcePage.bookId
                    val currentPages = pageManagementDelegate.unfilteredPages.value

                    // 1. Neue Seiten erstellen für jede Kategorie
                    val categoryPageIds = mutableMapOf<String, String>()
                    proposal.categories.forEach { category ->
                        val buttonCount = category.buttonLabels.size
                        val (rows, cols) = when {
                            buttonCount <= 4 -> 2 to 2
                            buttonCount <= 9 -> 3 to 3
                            buttonCount <= 16 -> 4 to 4
                            else -> 5 to 5
                        }
                        val newId = createPageUseCase.execute(
                            name = category.name,
                            rows = rows,
                            columns = cols,
                            bookId = bookId,
                            currentPages = currentPages
                        )
                        categoryPageIds[category.name] = newId
                    }

                    // 2. Buttons von Quellseite auf Zielseiten verschieben
                    val sourcePageUpdated = pageManagementDelegate.getPageById(pageId) ?: throw IllegalArgumentException("Source page not found after creation")
                    val sourceConfigs = sourcePageUpdated.buttonConfigs.toMutableList()

                    proposal.categories.forEach { category ->
                        val targetPageId = categoryPageIds[category.name] ?: return@forEach
                        val targetPage = pageManagementDelegate.getPageById(targetPageId) ?: return@forEach
                        val targetConfigs = targetPage.buttonConfigs.toMutableList()

                        category.buttonLabels.forEach { label ->
                            val sourceIndex = sourceConfigs.indexOfFirst { it?.label == label }
                            if (sourceIndex != -1) {
                                val buttonToMove = sourceConfigs[sourceIndex] ?: return@forEach
                                val targetIndex = targetConfigs.indexOfFirst { it == null }
                                if (targetIndex != -1) {
                                    targetConfigs[targetIndex] = buttonToMove
                                    sourceConfigs[sourceIndex] = null
                                }
                            }
                        }
                        pageManagementDelegate.pageRepository.updatePage(targetPage.copy(buttonConfigs = targetConfigs))
                    }

                    // 3. Quellseite neu sortieren (freie Plätze entfernen) und schrumpfen
                    val remainingConfigs = sourceConfigs.filterNotNull()
                    
                    // Neue Navigation-Buttons vorbereiten
                    val newCategoryButtons = proposal.categories.mapNotNull { category ->
                        val targetPageId = categoryPageIds[category.name] ?: return@mapNotNull null
                        ButtonConfig(
                            id = java.util.UUID.randomUUID().toString(),
                            label = category.name,
                            spokenText = "Öffne ${category.name}",
                            buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Öffne ${category.name}")
                        )
                    }
                    
                    val finalSourceButtons = remainingConfigs + newCategoryButtons
                    val finalCount = finalSourceButtons.size
                    
                    // Bestimme die optimal geschrumpfte Grid-Größe
                    val (sourceRows, sourceCols) = when {
                        finalCount <= 4 -> 2 to 2
                        finalCount <= 9 -> 3 to 3
                        finalCount <= 16 -> 4 to 4
                        finalCount <= 25 -> 5 to 5
                        finalCount <= 36 -> 6 to 6
                        else -> 7 to 7
                    }
                    
                    // Fülle die Knöpfe lückenlos in die sichtbaren Slots des neuen Grids
                    val finalConfigs = MutableList<ButtonConfig?>(com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) { null }
                    var buttonIndex = 0
                    for (r in 0 until sourceRows) {
                        for (c in 0 until sourceCols) {
                            if (buttonIndex < finalSourceButtons.size) {
                                val globalPos = r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c
                                finalConfigs[globalPos] = finalSourceButtons[buttonIndex]
                                buttonIndex++
                            }
                        }
                    }
                    
                    // Quellseite speichern
                    val updatedPage = sourcePageUpdated.copy(
                        buttonConfigs = finalConfigs,
                        rows = sourceRows,
                        columns = sourceCols
                    )
                    pageManagementDelegate.pageRepository.updatePage(updatedPage)
                    updatedPage
                }

                val command = com.andreas_kratzer.ghosttalk.ui.pages.history.PageSplitCommand(
                    delegate = pageManagementDelegate,
                    bookId = bookIdVal,
                    sourcePageId = pageId,
                    preSplitPages = preSplitPages,
                    label = com.andreas_kratzer.ghosttalk.ui.pages.history.EditLabel(
                        com.andreas_kratzer.ghosttalk.R.string.history_page_split,
                        listOf(sourcePageVal.name)
                    )
                )
                pageManagementDelegate.history.mutex.withLock {
                    pageManagementDelegate.history.execute(command)
                }

                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(finalSourcePage)
                    Toast.makeText(application, "Seite erfolgreich aufgeteilt!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PageSplitDelegate", "Error applying page split", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Fehler beim Anwenden: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isPageSplitLoading.value = false
                _pageSplitProposal.value = null
            }
        }
    }
}
