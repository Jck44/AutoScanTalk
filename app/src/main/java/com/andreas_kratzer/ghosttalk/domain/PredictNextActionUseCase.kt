package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Page
import java.time.LocalTime
import javax.inject.Inject

/**
 * UseCase to predict the next likely actions using Gemini AI.
 */
class PredictNextActionUseCase @Inject constructor(
    private val actionLogUseCase: ActionLogUseCase,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val settingsRepository: SettingsRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val geminiUseCaseFactory: GeminiUseCaseFactory
) {

    suspend fun predict(currentPage: Page, bookId: String): List<String> {
        val gemini = geminiUseCaseFactory.create { 
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                googleAuthManager.getGoogleCredential()?.token
            }
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
        
        val buttonLabels = currentPage.buttonConfigs
            .filter { it?.isActive == true }
            .map { it?.label }
            .filterNotNull()
            .distinct()

        val prompt = """
            Du bist ein Assistent für eine UK-App (Unterstützte Kommunikation). 
            Deine Aufgabe ist es, vorherzusagen, was der Nutzer als nächstes sagen oder tun möchte.
            
            KONTEXT:
            - Aktuelle Seite: "${currentPage.name}"
            - Verfügbare Buttons auf dieser Seite: ${buttonLabels.joinToString(", ")}
            - Letzte Aktionen des Nutzers: ${history.joinToString(" -> ")}
            - Häufigste Aktionen generell: ${frequentActions.joinToString(", ") { it.label }}
            - Aktuelle Uhrzeit: $timeNow
            
            AUFGABE:
            Nenne mir die 3 wahrscheinlichsten nächsten Aussagen oder Navigationsziele von DIESER Seite.
            Wenn eine Navigation zu einer anderen Seite (z.B. "Essen", "Gefühle") wahrscheinlich ist, nenne den Namen der Seite.
            Wenn ein konkreter Satz wahrscheinlich ist, nenne das Label des Buttons.
            
            WICHTIG:
            - Antworte NUR mit einer Liste der Top 3 Labels, getrennt durch Komma.
            - Keine Erklärungen. 
            - Wenn du weniger als 3 findest, nenne nur so viele wie möglich.
            - Die Antwort muss EXAKT Labels aus der Liste der verfügbaren Buttons oder Navigationsziele enthalten.
        """.trimIndent()

        return try {
            val response = gemini.generateResponse(prompt)
            parseResponse(response)
        } catch (e: Exception) {
            if (e.message?.contains("429") == true) {
                android.util.Log.w("PredictNextAction", "Gemini Quota reached (429)")
            }
            emptyList()
        }
    }

    private fun parseResponse(response: String): List<String> {
        // Simple comma separated list parsing
        return response.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
            .take(3)
    }
}
