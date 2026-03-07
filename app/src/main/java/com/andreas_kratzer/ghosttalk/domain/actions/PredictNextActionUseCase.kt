package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import android.util.Log

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
                    it.buttonAction !is com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction &&
                    com.andreas_kratzer.ghosttalk.ui.util.GridUtils.isVisibleInGrid(index, currentPage.rows, currentPage.columns)
                ) {
                    "${it.id}:${it.label}"
                } else null
            }
            .take(30) // Reduced from 40 to 30 for even more speed
            .joinToString("\n")

        val pageContext = allPages
            .filter { it.id != currentPage.id }
            .take(15) // Reduced from 20 to 15
            .joinToString("\n") { "${it.id}:${it.name}" }

        val prompt = """
            UK-App Prediction. Output: 3 IDs, comma-separated.
            Context:
            Current Page: "${currentPage.name}"
            Buttons:
            $buttonContext
            Pages:
            $pageContext
            History: ${history.joinToString(">")}
            Frequent: ${frequentActions.joinToString(",") { it.label }}
            Time: $timeNow
            ONLY return IDs.
        """.trimIndent()

        return try {
            withTimeout(settingsRepository.geminiTimeout) {
                val response = localIntentRouter.generateRawResponse(prompt)
                parseResponse(response)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w("PredictNextActionUseCase", "Gemini prediction timed out after ${settingsRepository.geminiTimeout}ms")
            emptyList()
        } catch (e: Exception) {
            Log.e("PredictNextActionUseCase", "Gemini prediction failed", e)
            emptyList()
        }
    }

    private fun parseResponse(response: String): List<String> {
        return response.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            // If the model returns "id: label", extract only the part before the colon
            .map { it.split(":").first().trim() }
            .filter { it.isNotBlank() }
            .take(3)
    }
}
