package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import com.andreas_kratzer.ghosttalk.core.model.PageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRestructureOrchestrator @Inject constructor(
    private val hierarchyUseCase: BookHierarchyProposalUseCase,
    private val layoutUseCase: PageLayoutProposalUseCase
) {

    data class FinalRestructureResult(
        val hierarchy: BookHierarchyProposal,
        val pageLayouts: List<PageLayoutProposal>,
        val wasReconciliationTriggered: Boolean
    )

    suspend fun executeRestructure(
        pagesJsonString: String,
        userFeedback: String? = null,
        manualEditsJsonString: String? = null
    ): FinalRestructureResult = withContext(Dispatchers.Default) {
        
        val originalButtonIds = extractAllButtonIds(pagesJsonString)
        
        // 1. Hierarchie generieren
        val hierarchyProposal = hierarchyUseCase.execute(pagesJsonString, userFeedback, manualEditsJsonString)

        // 2. Layouts für jede vorgeschlagene Seite generieren
        val generatedLayouts = mutableListOf<PageLayoutProposal>()
        for (proposedPage in hierarchyProposal.pages) {
            val layoutProposal = layoutUseCase.execute(
                targetPageName = proposedPage.name,
                description = proposedPage.description,
                subpages = proposedPage.subpages,
                buttonsJsonString = pagesJsonString
            )
            generatedLayouts.add(layoutProposal)
        }

        // 3. RECONCILIATION: Abgleich aller gemappten IDs
        val mappedButtonIds = generatedLayouts
            .flatMap { it.actions }
            .filter { it.type == "MOVE_BUTTON" }
            .mapNotNull { it.buttonId }
            .toSet()

        val missingButtonIds = originalButtonIds - mappedButtonIds

        if (missingButtonIds.isEmpty()) {
            return@withContext FinalRestructureResult(hierarchyProposal, generatedLayouts, false)
        }

        // Rettung der verlorenen Buttons
        Log.w("Orchestrator", "Sicherheitsgurt aktiv! ${missingButtonIds.size} Knöpfe wurden gerettet.")
        val backupPageName = "Umsortierte Reste (Automatisch)"
        val backupPageDescription = "Automatisch vom System gesicherte Knöpfe, die von der KI unvollständig zugeordnet wurden."

        val updatedPages = hierarchyProposal.pages.toMutableList().apply {
            add(HierarchyPageNode(backupPageName, backupPageDescription, emptyList(), "Backup", missingButtonIds.toList()))
        }
        
        val backupActions = missingButtonIds.map { id ->
            PageButtonAction(type = "MOVE_BUTTON", buttonLabel = "ID: $id", rationale = "Automatische Systemrettung.", buttonId = id)
        }
        val updatedLayouts = generatedLayouts + PageLayoutProposal(backupPageName, backupActions)

        return@withContext FinalRestructureResult(
            hierarchy = hierarchyProposal.copy(pages = updatedPages),
            pageLayouts = updatedLayouts,
            wasReconciliationTriggered = true
        )
    }

    private fun extractAllButtonIds(pagesJsonString: String): Set<String> {
        val ids = mutableSetOf<String>()
        return try {
            val root = JSONObject(pagesJsonString)
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
