package com.andreas_kratzer.ghosttalk.core.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UseCase zur Kategorisierung von Button-Labels einer großen Seite in sinnvolle Unterseiten.
 * Unterstützt sowohl automatische Cloud-Anfragen als auch manuelle Prompts für Copy & Paste.
 */
@Singleton
class SplitPageUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase
) {
    data class CategoryProposal(val name: String, val buttonLabels: List<String>)
    data class PageSplitProposal(val categories: List<CategoryProposal>)

    /**
     * Generiert den Prompt, der an die KI gesendet wird (oder vom Nutzer kopiert werden kann).
     */
    fun generatePrompt(buttonLabels: List<String>): String {
        val listStr = buttonLabels.joinToString(separator = ", ") { "\"$it\"" }
        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC). Deine Aufgabe ist es, eine Liste von Tastenbeschriftungen einer einzigen großen Seite in 3 bis 6 logische Kategorien (Unterseiten) zu unterteilen, um die Navigation und das Scanning für den Benutzer zu vereinfachen.
            
            Hier sind die Tastenbeschriftungen der großen Seite:
            [$listStr]
            
            Regeln für die Aufteilung:
            1. Ordne JEDEN übergebenen Begriff genau einer Kategorie zu.
            2. Erfinde KEINE neuen Begriffe. Verwende exakt die übergebenen Bezeichnungen.
            3. Wähle kurze, prägnante Namen für die Kategorien (z. B. "Essen", "Gefühle", "Aktionen", "Personen", "Orte").
            
            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im folgenden Format (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "categories": [
                {
                  "name": "Name der Kategorie",
                  "buttonLabels": ["Begriff1", "Begriff2"]
                }
              ]
            }
        """.trimIndent()
    }

    /**
     * Parst die JSON-Antwort (entweder aus der API oder vom Benutzer eingefügt) in ein strukturiertes Ergebnis.
     */
    fun parseResponse(jsonResponse: String): PageSplitProposal {
        // Bereinige eventuelle Markdown-Fences, falls vorhanden
        val cleanedJson = jsonResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()
            
        val root = JSONObject(cleanedJson)
        val categoriesArray = root.getJSONArray("categories")
        val categories = mutableListOf<CategoryProposal>()
        
        for (i in 0 until categoriesArray.length()) {
            val catObj = categoriesArray.getJSONObject(i)
            val name = catObj.getString("name")
            val labelsArray = catObj.getJSONArray("buttonLabels")
            val labels = mutableListOf<String>()
            for (j in 0 until labelsArray.length()) {
                labels.add(labelsArray.getString(j))
            }
            categories.add(CategoryProposal(name, labels))
        }
        return PageSplitProposal(categories)
    }

    /**
     * Führt die automatische Kategorisierung über die Gemini Cloud API aus.
     */
    suspend fun execute(buttonLabels: List<String>): PageSplitProposal {
        val prompt = generatePrompt(buttonLabels)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }
}
