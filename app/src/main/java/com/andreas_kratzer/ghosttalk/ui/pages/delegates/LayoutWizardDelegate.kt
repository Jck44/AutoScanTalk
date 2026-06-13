package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.pages.history.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LayoutWizardDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val geminiUseCase: GeminiUseCase,
    private val pageManagementDelegate: PageManagementDelegate,
    private val pageSplitDelegate: PageSplitDelegate
) {
    private lateinit var scope: CoroutineScope

    private val _magicCleanupProgress = MutableStateFlow<String?>(null)
    val magicCleanupProgress: StateFlow<String?> = _magicCleanupProgress.asStateFlow()

    fun init(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    private fun isHomeButton(config: ButtonConfig?, defaultStartPageId: String?): Boolean {
        if (config == null) return false
        val action = config.buttonAction
        return action is NavigateToStartPageButtonAction ||
                (action is NavigateToPageButtonAction && (action.pageId.isEmpty() || action.pageId == defaultStartPageId))
    }

    private fun packButtonsIntoGrid(buttons: List<ButtonConfig>, rows: Int, cols: Int): List<ButtonConfig?> {
        val finalConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }
        val now = System.currentTimeMillis()
        var buttonIndex = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (buttonIndex < buttons.size) {
                    val globalPos = r * GridUtils.MAX_GRID_SIZE + c
                    finalConfigs[globalPos] = buttons[buttonIndex].copy(updatedAt = now)
                    buttonIndex++
                }
            }
        }
        return finalConfigs
    }

    private fun calculateOptimalGridSize(buttonCount: Int): Pair<Int, Int> {
        return when {
            buttonCount <= 1 -> 1 to 1
            buttonCount <= 2 -> 1 to 2
            buttonCount <= 4 -> 2 to 2
            buttonCount <= 6 -> 2 to 3
            buttonCount <= 9 -> 3 to 3
            buttonCount <= 12 -> 3 to 4
            buttonCount <= 16 -> 4 to 4
            buttonCount <= 20 -> 4 to 5
            buttonCount <= 25 -> 5 to 5
            buttonCount <= 30 -> 5 to 6
            buttonCount <= 36 -> 6 to 6
            buttonCount <= 42 -> 6 to 7
            else -> 7 to 7
        }
    }

    fun reorderByClickStats(pageId: String, onComplete: () -> Unit = {}) {
        val bookId = pageManagementDelegate.activeBookId.value ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                val clickCounts = stats.flatMap { it.children }
                    .filter { it.pageId == pageId }
                    .associate { it.buttonConfigId to it.usageCount }

                val allButtons = page.buttonConfigs.filterNotNull()
                val activeButtons = allButtons.filter { it.isActive }
                val deactivatedButtons = allButtons.filter { !it.isActive }

                val sortedActive = activeButtons.sortedByDescending { clickCounts[it.id] ?: 0L }
                val combinedButtons = sortedActive + deactivatedButtons

                val packed = packButtonsIntoGrid(combinedButtons, page.rows, page.columns)
                val updatedPage = page.copy(buttonConfigs = packed)
                pageManagementDelegate.pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(updatedPage)
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LayoutWizardDelegate", "Error reordering buttons", e)
            }
        }
    }

    fun insertHomeNavigationEveryX(pageId: String, x: Int, onComplete: () -> Unit = {}) {
        val defaultStartPageId = settingsRepository.defaultStartPageId
        scope.launch(Dispatchers.IO) {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val nonHomeButtons = page.buttonConfigs.filterNotNull().filter { !isHomeButton(it, defaultStartPageId) }

                val result = mutableListOf<ButtonConfig>()
                var counter = 0
                for (btn in nonHomeButtons) {
                    if (counter > 0 && counter % x == 0) {
                        val homeButton = ButtonConfig(
                            id = java.util.UUID.randomUUID().toString(),
                            label = "Startseite",
                            spokenText = "Zurück zur Startseite",
                            buttonAction = NavigateToStartPageButtonAction(),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Startseite")
                        )
                        result.add(homeButton)
                    }
                    result.add(btn)
                    counter++
                }

                var finalRows = page.rows
                var finalCols = page.columns
                if (result.size > finalRows * finalCols) {
                    val (optRows, optCols) = calculateOptimalGridSize(result.size)
                    if (optRows * optCols > finalRows * finalCols) {
                        finalRows = maxOf(finalRows, optRows)
                        finalCols = maxOf(finalCols, optCols)
                    }
                }
                finalRows = finalRows.coerceIn(1, 7)
                finalCols = finalCols.coerceIn(1, 7)

                val packed = packButtonsIntoGrid(result, finalRows, finalCols)
                val updatedPage = page.copy(
                    rows = finalRows,
                    columns = finalCols,
                    buttonConfigs = packed
                )
                pageManagementDelegate.pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(updatedPage)
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LayoutWizardDelegate", "Error inserting home navigation", e)
            }
        }
    }

    fun shrinkGridToMinimum(pageId: String, onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val remainingButtons = page.buttonConfigs.filterNotNull()
                val count = remainingButtons.size
                val (newRows, newCols) = calculateOptimalGridSize(count)

                val packed = packButtonsIntoGrid(remainingButtons, newRows, newCols)
                val updatedPage = page.copy(
                    rows = newRows,
                    columns = newCols,
                    buttonConfigs = packed
                )
                pageManagementDelegate.pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(updatedPage)
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LayoutWizardDelegate", "Error shrinking grid", e)
            }
        }
    }

    fun deleteDeactivatedButtons(pageId: String, onComplete: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val remainingButtons = page.buttonConfigs.filterNotNull().filter { it.isActive }

                val packed = packButtonsIntoGrid(remainingButtons, page.rows, page.columns)
                val updatedPage = page.copy(buttonConfigs = packed)
                pageManagementDelegate.pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(updatedPage)
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LayoutWizardDelegate", "Error deleting deactivated buttons", e)
            }
        }
    }

    fun magicCleanup(pageId: String, onComplete: () -> Unit = {}) {
        val bookId = pageManagementDelegate.activeBookId.value ?: return
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Delete deactivated buttons
                _magicCleanupProgress.value = "Lösche deaktivierte Kacheln..."
                kotlinx.coroutines.delay(600)
                val page = pageManagementDelegate.getPageById(pageId) ?: run {
                    _magicCleanupProgress.value = null
                    return@launch
                }

                val activeButtonsOnly = page.buttonConfigs.filterNotNull().filter { it.isActive }

                // 2. Reorder buttons by usage statistics
                _magicCleanupProgress.value = "Sortiere nach Klicks..."
                kotlinx.coroutines.delay(600)
                val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                val clickCounts = stats.flatMap { it.children }
                    .filter { it.pageId == pageId }
                    .associate { it.buttonConfigId to it.usageCount }
                val sortedActive = activeButtonsOnly.sortedByDescending { clickCounts[it.id] ?: 0L }

                // 3. Insert home navigation every 5 buttons
                _magicCleanupProgress.value = "Verteile Startseite-Buttons..."
                kotlinx.coroutines.delay(600)
                val defaultStartPageId = settingsRepository.defaultStartPageId
                val cleanedHome = sortedActive.filter { !isHomeButton(it, defaultStartPageId) }
                val result = mutableListOf<ButtonConfig>()
                var counter = 0
                for (btn in cleanedHome) {
                    if (counter > 0 && counter % 5 == 0) {
                        val homeButton = ButtonConfig(
                            id = java.util.UUID.randomUUID().toString(),
                            label = "Startseite",
                            spokenText = "Zurück zur Startseite",
                            buttonAction = NavigateToStartPageButtonAction(),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Startseite")
                        )
                        result.add(homeButton)
                    }
                    result.add(btn)
                    counter++
                }

                // 4. Shrink grid to minimum
                _magicCleanupProgress.value = "Minimiere Rastergröße..."
                kotlinx.coroutines.delay(600)
                val (newRows, newCols) = calculateOptimalGridSize(result.size)
                val packed = packButtonsIntoGrid(result, newRows, newCols)

                // 5. If a proposal exists, change scan pattern to row_by_row and optionally generate row names
                _magicCleanupProgress.value = "Optimiere Scan-Muster..."
                kotlinx.coroutines.delay(600)
                val proposalsExist = pageSplitDelegate.layoutOptimizationProposals.value.any { it.pageId == pageId }

                val finalScanPattern = if (proposalsExist) "row_by_row" else page.scanPattern
                val rowNames = mutableListOf<String>()
                if (proposalsExist && settingsRepository.isGeminiEnabled) {
                    _magicCleanupProgress.value = "Generiere Zeilennamen via Gemini..."
                    kotlinx.coroutines.delay(600)
                    val rowsData = (0 until newRows).map { rowIndex ->
                        (0 until newCols).mapNotNull { c ->
                            val globalIndex = rowIndex * GridUtils.MAX_GRID_SIZE + c
                            val config = packed.getOrNull(globalIndex)
                            if (config != null && config.isActive && config.label.isNotBlank()) {
                                config.label
                            } else null
                        }
                    }

                    if (rowsData.any { it.isNotEmpty() }) {
                        try {
                            val promptBuilder = StringBuilder()
                            promptBuilder.append("Analysiere die folgenden Zeilen einer Kommunikations-Tafel für Unterstützte Kommunikation.\n")
                            promptBuilder.append("Schlage für jede Zeile eine kurze, prägnante Bezeichnung (maximal 2 Wörter, z. B. \"Schnelle Worte\" oder \"Smart Home\") vor, die als Name für diese Zeile dienen kann.\n")
                            promptBuilder.append("Antworte ausschließlich mit einer JSON-Liste von Strings, z. B. [\"Name1\", \"Name2\", ...], in der genauen Reihenfolge der Zeilen.\n")
                            promptBuilder.append("Keine Satzzeichen außerhalb des JSONs, kein Markdown-Format (keine ```json Blöcke), keine zusätzlichen Erklärungen.\n\n")

                            rowsData.forEachIndexed { index, labels ->
                                promptBuilder.append("Zeile ${index + 1}: ${labels.joinToString(", ")}\n")
                            }

                            val response = geminiUseCase.generateResponse(promptBuilder.toString()).trim()
                            val cleanResponse = response.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                            try {
                                val parsed = com.google.gson.Gson().fromJson(cleanResponse, Array<String>::class.java)
                                if (parsed != null) {
                                    for (name in parsed) {
                                        rowNames.add(name.trim())
                                    }
                                }
                            } catch (_: Exception) {
                                val jsonArray = org.json.JSONArray(cleanResponse)
                                for (i in 0 until jsonArray.length()) {
                                    rowNames.add(jsonArray.getString(i).trim())
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LayoutWizardDelegate", "Error generating row names batch in magic cleanup", e)
                        }
                    }

                    while (rowNames.size < newRows) {
                        rowNames.add("")
                    }
                }

                _magicCleanupProgress.value = "Speichere Layout..."
                kotlinx.coroutines.delay(500)

                val updatedPage = page.copy(
                    rows = newRows,
                    columns = newCols,
                    buttonConfigs = packed,
                    scanPattern = finalScanPattern,
                    rowNames = if (rowNames.isNotEmpty()) rowNames else page.rowNames
                )
                val command = PageFullSnapshotCommand(
                    delegate = pageManagementDelegate,
                    oldPage = page,
                    newPage = updatedPage,
                    label = EditLabel(R.string.history_reorder),
                    icon = EditIcon.REORDER
                )
                pageManagementDelegate.history.mutex.withLock {
                    pageManagementDelegate.history.execute(command)
                }

                withContext(Dispatchers.Main) {
                    _magicCleanupProgress.value = null
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("LayoutWizardDelegate", "Error in magic cleanup", e)
                _magicCleanupProgress.value = null
            }
        }
    }
}
