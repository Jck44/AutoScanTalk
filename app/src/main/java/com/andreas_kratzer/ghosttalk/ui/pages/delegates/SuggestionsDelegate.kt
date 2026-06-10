package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionsDelegate @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val geminiUseCase: GeminiUseCase,
    private val pageManagementDelegate: PageManagementDelegate
) {
    private lateinit var scope: CoroutineScope

    fun init(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun suggestButtonLabel(config: ButtonConfig, onResult: (String) -> Unit) {
        if (!settingsRepository.isGeminiEnabled) {
            onResult("")
            return
        }
        scope.launch {
            try {
                val prompt = com.andreas_kratzer.ghosttalk.ui.util.generateSuggestButtonLabelPrompt(config) { pageId ->
                    pageManagementDelegate.unfilteredPages.value.find { it.id == pageId }?.name
                }
                val response = geminiUseCase.generateResponse(prompt)
                val cleaned = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                onResult(cleaned)
            } catch (e: Exception) {
                Log.e("SuggestionsDelegate", "Error generating button label suggestion", e)
                onResult("")
            }
        }
    }

    fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {
        val page = pageManagementDelegate.unfilteredPages.value.find { it.id == itemId }
        if (page == null) {
            onResult("")
            return
        }

        if (!settingsRepository.isGeminiEnabled) {
            Toast.makeText(application, "Gemini ist in den Einstellungen deaktiviert.", Toast.LENGTH_SHORT).show()
            onResult("")
            return
        }

        val columns = page.columns
        val labels = (0 until columns).mapNotNull { c ->
            val globalIndex = rowIndex * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c
            val config = page.buttonConfigs.getOrNull(globalIndex)
            if (config != null && config.isActive && config.label.isNotBlank()) {
                config.label
            } else null
        }

        if (labels.isEmpty()) {
            Toast.makeText(application, "Keine aktiven Buttons in dieser Zeile vorhanden.", Toast.LENGTH_SHORT).show()
            onResult("")
            return
        }

        scope.launch {
            try {
                val prompt = "Analysiere diese Liste von Begriffen, die sich in einer Zeile auf einer Kommunikations-Tafel für Unterstützte Kommunikation befinden: ${labels.joinToString(", ")}. Schlage eine kurze, prägnante Bezeichnung (maximal 2 Wörter, z. B. \"Schnelle Worte\" oder \"Smart Home\") vor, die als Name für diese Zeile dienen kann. Antworte NUR mit dieser Bezeichnung, ohne Satzzeichen, Anführungszeichen oder zusätzliche Erklärungen."
                val response = geminiUseCase.generateResponse(prompt)
                val cleaned = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                onResult(cleaned)
            } catch (e: Exception) {
                Log.e("SuggestionsDelegate", "Error generating row name suggestion", e)
                Toast.makeText(application, "Fehler bei der Generierung: ${e.message}", Toast.LENGTH_LONG).show()
                onResult("")
            }
        }
    }
}
