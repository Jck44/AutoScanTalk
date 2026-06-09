package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.ai.domain.BookHierarchyProposalUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.PageLayoutProposalUseCase
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.CloneBookUseCase
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AiRestructureDelegate @Inject constructor(
    private val application: Application,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val settingsRepository: SettingsRepository,
    private val cloneBookUseCase: CloneBookUseCase,
    private val bookHierarchyProposalUseCase: BookHierarchyProposalUseCase,
    private val pageLayoutProposalUseCase: PageLayoutProposalUseCase
) {
    private val _selectedPageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPageIds: StateFlow<Set<String>> = _selectedPageIds.asStateFlow()

    private val _aiRestructureProposal = MutableStateFlow<BookRestructureProposal?>(null)
    val aiRestructureProposal: StateFlow<BookRestructureProposal?> = _aiRestructureProposal.asStateFlow()

    private val _aiHierarchyProposal = MutableStateFlow<BookHierarchyProposal?>(null)
    val aiHierarchyProposal: StateFlow<BookHierarchyProposal?> = _aiHierarchyProposal.asStateFlow()

    private val _aiPageLayoutProposals = MutableStateFlow<Map<String, PageLayoutProposal>>(emptyMap())
    val aiPageLayoutProposals: StateFlow<Map<String, PageLayoutProposal>> = _aiPageLayoutProposals.asStateFlow()

    private val _isAiRestructureLoading = MutableStateFlow(false)
    val isAiRestructureLoading: StateFlow<Boolean> = _isAiRestructureLoading.asStateFlow()

    private val _isAiHierarchyLoading = MutableStateFlow(false)
    val isAiHierarchyLoading: StateFlow<Boolean> = _isAiHierarchyLoading.asStateFlow()

    private val _isLoadingPageLayout = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isLoadingPageLayout: StateFlow<Map<String, Boolean>> = _isLoadingPageLayout.asStateFlow()

    private val _aiRestructureScope = MutableStateFlow("detailed")
    val aiRestructureScope: StateFlow<String> = _aiRestructureScope.asStateFlow()

    private val _aiRestructureError = MutableStateFlow<String?>(null)
    val aiRestructureError: StateFlow<String?> = _aiRestructureError.asStateFlow()

    fun togglePageSelection(pageId: String) {
        val current = _selectedPageIds.value
        _selectedPageIds.value = if (current.contains(pageId)) {
            current - pageId
        } else {
            current + pageId
        }
    }

    fun selectAllPages(pages: List<Page>) {
        _selectedPageIds.value = pages.map { it.id }.toSet()
    }

    fun selectActivePagesOnly(pages: List<Page>, activeTargetPageIds: Set<String>) {
        _selectedPageIds.value = pages
            .filter { activeTargetPageIds.contains(it.id) }
            .map { it.id }
            .toSet()
    }

    fun setSelectedPageIds(ids: Set<String>) {
        _selectedPageIds.value = ids
    }

    fun setAiRestructureScope(scope: String) {
        _aiRestructureScope.value = scope
    }

    fun clearAiRestructureError() {
        _aiRestructureError.value = null
    }

    fun setAiRestructureProposal(proposal: BookRestructureProposal?) {
        _aiRestructureProposal.value = proposal
    }

    fun setAiHierarchyProposal(proposal: BookHierarchyProposal?) {
        _aiHierarchyProposal.value = proposal
    }

    fun loadProposalFromCache(bookId: String): BookRestructureProposal? {
        return try {
            val cacheDir = application.cacheDir ?: return null
            val file = java.io.File(cacheDir, "ai_restructure_proposal_${bookId}.json")
            if (file.exists()) {
                val json = file.readText()
                kotlinx.serialization.json.Json.decodeFromString(BookRestructureProposal.serializer(), json)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AiRestructureDelegate", "Error loading proposal from cache", e)
            null
        }
    }

    fun deleteRestructureCache(scope: CoroutineScope, bookId: String) {
        _aiRestructureProposal.value = null
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = application.cacheDir ?: return@launch
                val file = java.io.File(cacheDir, "ai_restructure_proposal_${bookId}.json")
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("AiRestructureDelegate", "Error deleting proposal cache", e)
            }
        }
    }

    fun generateAiHierarchyProposal(
        scope: CoroutineScope,
        bookId: String,
        pageManagementDelegate: PageManagementDelegate,
        feedback: String? = null
    ) {
        scope.launch(Dispatchers.Default) {
            _isAiHierarchyLoading.value = true
            _aiRestructureError.value = null
            try {
                val pagesJsonString = buildRestructureSnapshotJson(bookId, _selectedPageIds.value, pageManagementDelegate)
                
                val currentHierarchy = _aiHierarchyProposal.value
                val manualEditsJson = if (currentHierarchy != null) {
                    val editsArray = org.json.JSONArray()
                    currentHierarchy.pages.forEach { node ->
                        val nodeObj = org.json.JSONObject()
                        nodeObj.put("name", node.name)
                        nodeObj.put("description", node.description)
                        nodeObj.put("subpages", org.json.JSONArray(node.subpages))
                        node.sourcePageName?.let { nodeObj.put("sourcePageName", it) }
                        nodeObj.put("buttonIds", org.json.JSONArray(node.buttonIds))
                        editsArray.put(nodeObj)
                    }
                    org.json.JSONObject().put("pages", editsArray).toString()
                } else null

                val proposal = bookHierarchyProposalUseCase.execute(pagesJsonString, feedback, manualEditsJson)
                _aiHierarchyProposal.value = proposal
                _aiPageLayoutProposals.value = emptyMap()
            } catch (e: Exception) {
                Log.e("AiRestructureDelegate", "Error generating AI hierarchy proposal", e)
                _aiRestructureError.value = e.localizedMessage
            } finally {
                _isAiHierarchyLoading.value = false
            }
        }
    }

    fun loadPageLayoutProposal(
        scope: CoroutineScope,
        bookId: String,
        pageManagementDelegate: PageManagementDelegate,
        pageName: String
    ) {
        val currentHierarchy = _aiHierarchyProposal.value ?: return
        val node = currentHierarchy.pages.find { it.name == pageName } ?: return

        scope.launch(Dispatchers.Default) {
            _isLoadingPageLayout.value = _isLoadingPageLayout.value + (pageName to true)
            try {
                val pagesJsonString = buildRestructureSnapshotJson(bookId, _selectedPageIds.value, pageManagementDelegate)

                val layout = pageLayoutProposalUseCase.execute(
                    targetPageName = node.name,
                    description = node.description,
                    subpages = node.subpages,
                    buttonsJsonString = pagesJsonString
                )

                _aiPageLayoutProposals.value = _aiPageLayoutProposals.value + (pageName to layout)
            } catch (e: Exception) {
                Log.e("AiRestructureDelegate", "Error loading layout for $pageName", e)
            } finally {
                _isLoadingPageLayout.value = _isLoadingPageLayout.value - pageName
            }
        }
    }

    fun applyHierarchyProposal(
        scope: CoroutineScope,
        currentBookId: String,
        pageManagementDelegate: PageManagementDelegate,
        setActiveBookId: (String) -> Unit,
        loadPage: (com.andreas_kratzer.ghosttalk.core.model.Page) -> Unit,
        onResult: (String) -> Unit
    ) {
        val proposal = _aiHierarchyProposal.value ?: return
        val layouts = _aiPageLayoutProposals.value

        scope.launch(Dispatchers.IO) {
            _isAiHierarchyLoading.value = true
            try {
                val pagesJsonString = buildRestructureSnapshotJson(currentBookId, _selectedPageIds.value, pageManagementDelegate)
                
                val missingPages = proposal.pages.filter { !layouts.containsKey(it.name) }
                val resolvedLayouts = layouts.toMutableMap()
                
                for (node in missingPages) {
                    try {
                        val layout = pageLayoutProposalUseCase.execute(
                            targetPageName = node.name,
                            description = node.description,
                            subpages = node.subpages,
                            buttonsJsonString = pagesJsonString
                        )
                        resolvedLayouts[node.name] = layout
                    } catch (e: Exception) {
                        Log.e("AiRestructureDelegate", "Failed to resolve layout for ${node.name} during save", e)
                    }
                }
                
                val originalButtonIds = extractAllButtonIds(pagesJsonString)
                val mappedButtonIds = resolvedLayouts.values
                    .flatMap { it.actions }
                    .filter { it.type == "MOVE_BUTTON" }
                    .mapNotNull { it.buttonId }
                    .toSet()
                
                val missingButtonIds = originalButtonIds - mappedButtonIds
                
                var finalProposal = proposal
                if (missingButtonIds.isNotEmpty()) {
                    Log.w("AiRestructureDelegate", "Safety belt active! ${missingButtonIds.size} unplaced buttons will be routed to Backup page.")
                    val backupPageName = "Umsortierte Reste (Automatisch)"
                    val backupPageDescription = "Automatisch vom System gesicherte Knöpfe, die von der KI unvollständig zugeordnet wurden."
                    
                    val updatedPages = proposal.pages.toMutableList().apply {
                        add(HierarchyPageNode(backupPageName, backupPageDescription, emptyList(), "Backup", missingButtonIds.toList()))
                    }
                    finalProposal = proposal.copy(pages = updatedPages)
                    
                    val backupActions = missingButtonIds.map { id ->
                        PageButtonAction(type = "MOVE_BUTTON", buttonLabel = "ID: $id", rationale = "Automatische Systemrettung.", buttonId = id)
                    }
                    resolvedLayouts[backupPageName] = PageLayoutProposal(backupPageName, backupActions)
                }

                val newBookId = cloneBookUseCase.applyHierarchyRestructure(currentBookId, finalProposal, resolvedLayouts)
                val startId = settingsRepository.getDefaultStartPageIdForBook(newBookId)
                val pages = pageManagementDelegate.pageRepository.getPagesForBook(newBookId)
                val startPage = pages.find { it.id == startId } ?: pages.firstOrNull()

                withContext(Dispatchers.Main) {
                    setActiveBookId(newBookId)
                    if (startPage != null) {
                        loadPage(startPage)
                    }
                    onResult(newBookId)
                }
            } catch (e: Exception) {
                Log.e("AiRestructureDelegate", "Error applying hierarchy restructure", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Fehler beim Anwenden: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isAiHierarchyLoading.value = false
            }
        }
    }

    suspend fun buildRestructureSnapshotJson(
        bookId: String,
        selectedPageIds: Set<String>,
        pageManagementDelegate: PageManagementDelegate
    ): String {
        val pages = pageManagementDelegate.unfilteredPages.value
        val historyEvents = buttonUsageRepository.getHistoryEventsForBook(bookId)
        val totalClicks = historyEvents.size
        val buttonHistoryEvents = historyEvents.filter { it.buttonId != null }.groupBy { it.buttonId!! }
        
        val rootJson = org.json.JSONObject()
        rootJson.put("bookId", bookId)
        rootJson.put("totalClicksInBook", totalClicks)
        
        val pagesArray = org.json.JSONArray()
        pages.forEach { page ->
            if (selectedPageIds.contains(page.id)) {
                val pageObj = org.json.JSONObject()
                pageObj.put("pageId", page.id)
                pageObj.put("pageName", page.name)
                
                val buttonsArray = org.json.JSONArray()
                page.buttonConfigs.forEach { btn ->
                    if (btn != null && btn.isActive && btn.label.isNotBlank()) {
                        val btnObj = org.json.JSONObject()
                        btnObj.put("id", btn.id)
                        btnObj.put("label", btn.label)
                        
                        val btnEvents = buttonHistoryEvents[btn.id] ?: emptyList()
                        val btnClicks = btnEvents.size
                        btnObj.put("clicks", btnClicks)
                        
                        val hourlyObj = org.json.JSONObject()
                        if (btnClicks > 0) {
                            var morning = 0
                            var lunch = 0
                            var evening = 0
                            var night = 0
                            val cal = java.util.Calendar.getInstance()
                            btnEvents.forEach { ev ->
                                cal.timeInMillis = ev.timestamp
                                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                when (hour) {
                                    in 5..11 -> morning++
                                    in 12..16 -> lunch++
                                    in 17..21 -> evening++
                                    else -> night++
                                }
                            }
                            hourlyObj.put("morning", morning.toDouble() / btnClicks)
                            hourlyObj.put("lunch", lunch.toDouble() / btnClicks)
                            hourlyObj.put("evening", evening.toDouble() / btnClicks)
                            hourlyObj.put("night", night.toDouble() / btnClicks)
                        } else {
                            hourlyObj.put("morning", 0.25)
                            hourlyObj.put("lunch", 0.25)
                            hourlyObj.put("evening", 0.25)
                            hourlyObj.put("night", 0.25)
                        }
                        btnObj.put("hourlyDistribution", hourlyObj)
                        
                        val successors = buttonUsageRepository.getMarkovSuccessors(bookId, btn.id, limit = 5)
                        val totalSuccessorCount = successors.sumOf { it.second }
                        val transitionsArray = org.json.JSONArray()
                        successors.forEach { (successorId, count) ->
                            val transitionObj = org.json.JSONObject()
                            transitionObj.put("targetButtonId", successorId)
                            val probability = if (totalSuccessorCount > 0) count.toDouble() / totalSuccessorCount else 0.0
                            transitionObj.put("probability", probability)
                            transitionsArray.put(transitionObj)
                        }
                        btnObj.put("nextButtonTransitions", transitionsArray)
                        
                        val action = btn.buttonAction
                        if (action is NavigateToPageButtonAction) {
                            val targetPageName = pages.find { it.id == action.pageId }?.name ?: ""
                            btnObj.put("destinationPage", targetPageName)
                        }
                        buttonsArray.put(btnObj)
                    }
                }
                pageObj.put("buttons", buttonsArray)
                pagesArray.put(pageObj)
            }
        }
        rootJson.put("pages", pagesArray)
        return rootJson.toString()
    }

    private fun extractAllButtonIds(pagesJsonString: String): Set<String> {
        val ids = mutableSetOf<String>()
        return try {
            val root = org.json.JSONObject(pagesJsonString)
            val pagesArray = root.optJSONArray("pages") ?: return emptySet()
            for (i in 0 until pagesArray.length()) {
                val pageObj = pagesArray.getJSONObject(i)
                val buttonsArray = pageObj.optJSONArray("buttons") ?: continue
                for (j in 0 until buttonsArray.length()) {
                    val btnObj = buttonsArray.getJSONObject(j)
                    if (btnObj.has("id")) ids.add(btnObj.getString("id"))
                }
            }
            ids
        } catch (_: Exception) { emptySet() }
    }
}
