package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.CategoryInfo
import com.andreas_kratzer.ghosttalk.core.model.RestructureAction
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRestructureProposalUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase
) {

    fun generatePrompt(pagesJsonString: String): String {
        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC) und Ergotherapie.
            Deine Aufgabe ist es, die Struktur eines AAC-Kommunikationsbuchs vollständig zu optimieren, um die Scan-Zeiten, die Suchzeiten und die kognitive Belastung des Benutzers drastisch zu verringern.
            
            Hier ist die aktuelle Struktur des Buchs mit Klickstatistiken für jeden Knopf im JSON-Format:
            $pagesJsonString
            
            Führe eine tiefgehende, ganzheitliche semantische Analyse aller Seiten und Knöpfe durch. Optimiere nicht nur lokale Hotspots, sondern ordne die gesamte Hierarchie semantisch neu nach folgenden Prinzipien:
            1. SEMANTISCHE COHÄRENZ: Knöpfe, die thematisch zusammenpassen (z. B. Essen, Aktivitäten, Gefühle, Personen, Orte, Verben), müssen auf gemeinsamen, thematisch reinen Seiten gruppiert werden. Identifiziere Knöpfe, die aktuell semantisch unpassend verstreut sind, und sortiere sie in die richtigen Seiten ein (Aktionstyp: "MOVE_BUTTON").
            2. STRUKTURELLE REORGANISATION: Wenn Seiten thematisch gemischt oder überfüllt sind (mehr als 8 Knöpfe), teile sie in klare, semantisch reine Unterseiten auf (Aktionstyp: "SPLIT_PAGE"). Benenne die Unterseiten sprechend (z. B. "Obst", "Getränke" statt nur allgemein "Essen").
            3. PRÄSENZ DER WICHTIGSTEN BEGRIFFE: Häufig geklickte Knöpfe (hohe Klickzahlen) müssen auf der Startseite ("Hauptseite") oder einer leicht erreichbaren Hauptebene platziert werden. Knöpfe auf tiefen Unterseiten mit vielen Klicks sollten nach oben verschoben werden (Aktionstyp: "MOVE_BUTTON").
            4. BEREINIGUNG: Knöpfe, die im Erfassungszeitraum kaum oder gar nicht (0 oder 1 Klicks) verwendet wurden, blockieren Platz und verlangsamen das Scannen. Deaktiviere/Archiviere diese (Aktionstyp: "DEACTIVATE_BUTTON").
            5. Steuerungs-Knöpfe wie "Zurück", "Startseite" oder "Lautstärke" dürfen nicht verschoben oder deaktiviert werden.
            
            Sei gründlich und erstelle eine umfassende, detaillierte Liste von Vorschlägen (gerne 10 bis 20 Aktionen), um das Buch strukturell aufzuwerten.
            
            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im folgenden Format (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "rationale": "Ausführliche Begründung auf Deutsch für den Betreuer, warum diese Verschiebung semantisch oder statistisch sinnvoll ist...",
                  "buttonLabel": "Knopfbeschriftung",
                  "sourcePageName": "Name der aktuellen Seite",
                  "targetPageName": "Name der Zielseite"
                },
                {
                  "type": "DEACTIVATE_BUTTON",
                  "rationale": "Begründung auf Deutsch...",
                  "buttonLabel": "Knopfbeschriftung",
                  "sourcePageName": "Name der Seite"
                },
                {
                  "type": "SPLIT_PAGE",
                  "rationale": "Begründung auf Deutsch...",
                  "sourcePageName": "Name der aufzuteilenden Seite",
                  "newCategories": [
                    {
                      "name": "Name der neuen Unterseite",
                      "buttonLabels": ["Knopf1", "Knopf2"]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    fun parseResponse(response: String): BookRestructureProposal {
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || firstBrace > lastBrace) {
            throw IllegalArgumentException("Invalid JSON response from Gemini")
        }
        val cleanedJson = response.substring(firstBrace, lastBrace + 1)
        val root = JSONObject(cleanedJson)
        val actionsArray = root.getJSONArray("actions")
        val actions = mutableListOf<RestructureAction>()

        for (i in 0 until actionsArray.length()) {
            val actObj = actionsArray.getJSONObject(i)
            val type = actObj.getString("type")
            val rationale = actObj.getString("rationale")
            val buttonLabel = actObj.optString("buttonLabel", null)
            val sourcePageName = actObj.optString("sourcePageName", null)
            val targetPageName = actObj.optString("targetPageName", null)

            val newCategoriesArray = actObj.optJSONArray("newCategories")
            val newCategories = if (newCategoriesArray != null) {
                val cats = mutableListOf<CategoryInfo>()
                for (j in 0 until newCategoriesArray.length()) {
                    val catObj = newCategoriesArray.getJSONObject(j)
                    val catName = catObj.getString("name")
                    val labelsArray = catObj.getJSONArray("buttonLabels")
                    val labels = mutableListOf<String>()
                    for (k in 0 until labelsArray.length()) {
                        labels.add(labelsArray.getString(k))
                    }
                    cats.add(CategoryInfo(catName, labels))
                }
                cats
            } else null

            actions.add(
                RestructureAction(
                    type = type,
                    rationale = rationale,
                    buttonLabel = buttonLabel,
                    sourcePageName = sourcePageName,
                    targetPageName = targetPageName,
                    newCategories = newCategories
                )
            )
        }

        return BookRestructureProposal(actions)
    }

    suspend fun execute(pagesJsonString: String): BookRestructureProposal {
        val prompt = generatePrompt(pagesJsonString)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }
}
