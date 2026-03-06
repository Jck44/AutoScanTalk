package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import java.time.LocalTime
import javax.inject.Inject

/**
 * UseCase to predict the next likely actions using Gemini Nano (on-device).
 * Uses Button IDs for robust action resolution.
 */
class PredictNextActionUseCase @Inject constructor(
    private val actionLogUseCase: ActionLogUseCase,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val settingsRepository: SettingsRepository,
    private val localIntentRouter: LocalIntentRouter
) {

    suspend fun predict(currentPage: Page, allPages: List<Page>, bookId: String): List<String> {
        if (!settingsRepository.useLocalGenerativeAi) {
            return emptyList()
        }

        val rawHistory = actionLogUseCase.loadSavedLogs().take(15)
        val showId = settingsRepository.showPageIdInLog
        
        val history = if (showId) {
            rawHistory
        } else {
            rawHistory.map { it.replace(Regex(" \\(ID: .*?\\)"), "") }
        }
        val frequentActions = buttonUsageRepository.getTopActions(bookId, 5)
        val timeNow = LocalTime.now().toString()
        
        // Map buttons and pages to IDs for the model. 
        // Filter out SmartPrediction buttons to prevent recursive predictions.
        val buttonContext = currentPage.buttonConfigs
            .mapIndexedNotNull { index, it ->
                if (it != null && 
                    it.isActive && 
                    it.label.isNotBlank() && 
                    it.buttonAction !is SmartPredictionButtonAction &&
                    com.andreas_kratzer.ghosttalk.ui.util.GridUtils.isVisibleInGrid(index, currentPage.rows, currentPage.columns)
                ) {
                    "- ${it.id}: ${it.label}"
                } else null
            }
            .joinToString("\n")

        val pageContext = allPages
            .filter { it.id != currentPage.id }
            .joinToString("\n") { "- ${it.id}: Navigation zu Seite ${it.name}" }

        val prompt = """
            Du bist ein Assistent für eine UK-App (Unterstützte Kommunikation). 
            Deine Aufgabe ist es, vorherzusagen, was der Nutzer als nächstes tun möchte.
            
            KONTEXT:
            - Aktuelle Seite: "${currentPage.name}"
            - Verfügbare Buttons (ID: Label):
            $buttonContext
            
            - Mögliche Navigationsziele (ID: Name):
            $pageContext
            
            - Letzte Aktionen: ${history.joinToString(" -> ")}
            - Häufigste Aktionen: ${frequentActions.joinToString(", ") { it.label }}
            - Uhrzeit: $timeNow
            
            AUFGABE:
            Nenne mir die IDs der 3 wahrscheinlichsten nächsten Aktionen oder Navigationsziele.
            
            WICHTIG:
            - Antworte NUR mit einer Liste der Top 3 IDs, getrennt durch Komma.
            - Keine Erklärungen, kein Text, NUR die IDs.
            - Die IDs müssen EXAKT aus der obigen Liste stammen.
        """.trimIndent()

        return try {
            val response = localIntentRouter.generateRawResponse(prompt)
            parseResponse(response)
        } catch (e: Exception) {
            android.util.Log.e("PredictNextAction", "Gemini Nano prediction failed", e)
            emptyList()
        }
    }

    private fun parseResponse(response: String): List<String> {
        return response.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
            .take(3)
    }
}
