package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.util.Log
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

    fun generatePrompt(pagesJsonString: String, scope: String = "detailed"): String {
        val scopeInstruction = when (scope) {
            "quick" -> "Erstelle eine kurze Liste von den 3 bis 5 wichtigsten, kritischsten Vorschlägen, um schnelle Verbesserungen zu erzielen."
            "detailed" -> "Erstelle eine ausgewogene, gründliche Liste von 10 bis 15 Vorschlägen, um das Buch strukturell aufzuwerten."
            "full" -> "Führe eine vollständige, tiefgehende Reorganisation des gesamten Buchs durch und liefere eine umfassende Liste aller sinnvollen Aktionen (20 oder mehr Vorschläge), um die Hierarchie komplett neu zu ordnen."
            else -> "Erstelle eine ausgewogene, gründliche Liste von 10 bis 15 Vorschlägen."
        }

        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC) und Ergotherapie.
            Deine Aufgabe ist es, die Struktur eines AAC-Kommunikationsbuchs vollständig zu optimieren, um die Scan-Zeiten, die Suchzeiten und die kognitive Belastung des Benutzers drastisch zu verringern.
            
            Hier ist die aktuelle Struktur des Buchs mit Klickstatistiken für jeden Knopf im JSON-Format. Jede Seite enthält ihre Dimensionen ("rows" und "columns"):
            $pagesJsonString
            
            Führe eine tiefgehende, ganzheitliche semantische Analyse aller Seiten und Knöpfe durch. Optimiere nicht nur lokale Hotspots, sondern ordne die gesamte Hierarchie semantisch neu nach folgenden Prinzipien:
            1. SEMANTISCHE COHÄRENZ: Knöpfe, die thematisch zusammenpassen (z. B. Essen, Aktivitäten, Gefühle, Personen, Orte, Verben), müssen auf gemeinsamen, thematisch reinen Seiten gruppiert werden. Identifiziere Knöpfe, die aktuell semantisch unpassend verstreut sind, und sortiere sie in die richtigen Seiten ein (Aktionstyp: "MOVE_BUTTON").
            2. STRUKTURELLE REORGANISATION: Wenn Seiten thematisch gemischt oder überfüllt sind (mehr als 8 Knöpfe), teile sie in klare, semantisch reine Unterseiten auf (Aktionstyp: "SPLIT_PAGE"). Benenne die Unterseiten sprechend (z. B. "Obst", "Getränke" statt nur allgemein "Essen").
            3. PRÄSENZ DER WICHTIGSTEN BEGRIFFE: Häufig geklickte Knöpfe (hohe Klickzahlen) müssen auf der Startseite ("Hauptseite") oder einer leicht erreichbaren Hauptebene platziert werden. Knöpfe auf tiefen Unterseiten mit vielen Klicks sollten nach oben verschoben werden (Aktionstyp: "MOVE_BUTTON").
            4. BEREINIGUNG: Knöpfe, die im Erfassungszeitraum kaum oder gar nicht (0 oder 1 Klicks) verwendet wurden, blockieren Platz und verlangsamen das Scannen. Deaktiviere/Archiviere diese (Aktionstyp: "DEACTIVATE_BUTTON").
            5. Steuerungs-Knöpfe wie "Zurück", "Startseite" oder "Lautstärke" dürfen nicht verschoben oder deaktiviert werden.
            
            KAPAZITÄTS- UND VERDRÄNGUNGSREGELUNG (WICHTIG):
            - Wenn du einen Knopf auf eine Zielseite verschieben möchtest ("MOVE_BUTTON") und diese Zielseite bereits voll oder sehr voll ist (die Anzahl der aktiven Knöpfe erreicht fast rows * columns, oder die Seite hat mehr als 8 Knöpfe und ist die Startseite "Hauptseite"), schlage vor, welcher andere Knopf (z.B. ein seltener geklickter Knopf) von der Zielseite verdrängt werden soll ("displaceButtonLabel") und wohin er verschoben werden soll ("displaceTargetPageName").
            - Wenn du einen Knopf verschiebst, gib optional an, an welche Stelle er kommen soll (z.B. "oben links", "Mitte" oder "anstelle von [displaceButtonLabel]") unter "targetPlacementDescription".
            
            $scopeInstruction
            
            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im folgenden Format (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "rationale": "Ausführliche Begründung auf Deutsch für den Betreuer, warum diese Verschiebung semantisch oder statistisch sinnvoll ist...",
                  "buttonLabel": "Knopfbeschriftung",
                  "sourcePageName": "Name der aktuellen Seite",
                  "targetPageName": "Name der Zielseite",
                  "displaceButtonLabel": "Knopfbeschriftung des Knopfes, der auf der Zielseite verdrängt/ersetzt werden soll (optional, falls Zielseite voll)",
                  "displaceTargetPageName": "Zielseite für den verdrängten Knopf (optional, falls displaceButtonLabel gesetzt)",
                  "targetPlacementDescription": "Beschreibung der Platzierung, z.B. 'Reihe 1 Spalte 2' oder 'anstelle von X' (optional)"
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

    fun generateLoadMorePrompt(pagesJsonString: String, existingProposalsJson: String): String {
        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC) und Ergotherapie.
            Hier ist die aktuelle Struktur des Buchs mit Klickstatistiken im JSON-Format. Jede Seite enthält ihre Dimensionen ("rows" und "columns"):
            $pagesJsonString
            
            Folgende Vorschläge wurden bereits generiert und dem Benutzer angezeigt:
            $existingProposalsJson
            
            Generiere 10 bis 15 ZUSÄTZLICHE, NEUE Vorschläge, die sich von den bereits generierten Vorschlägen unterscheiden. Wiederhole keinesfalls die Vorschläge, die bereits in der Liste enthalten sind.
            
            KAPAZITÄTS- UND VERDRÄNGUNGSREGELUNG (WICHTIG):
            - Wenn du einen Knopf auf eine Zielseite verschieben möchtest ("MOVE_BUTTON") und diese Zielseite bereits voll oder sehr voll ist (die Anzahl der aktiven Knöpfe erreicht fast rows * columns, oder die Seite hat mehr als 8 Knöpfe und ist die Startseite "Hauptseite"), schlage vor, welcher andere Knopf von der Zielseite verdrängt werden soll ("displaceButtonLabel") und wohin er verschoben werden soll ("displaceTargetPageName").
            - Wenn du einen Knopf verschiebst, gib optional an, an welche Stelle er kommen soll (z.B. "oben links", "Mitte" oder "anstelle von [displaceButtonLabel]") unter "targetPlacementDescription".
            
            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im folgenden Format (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "rationale": "Ausführliche Begründung auf Deutsch...",
                  "buttonLabel": "Knopfbeschriftung",
                  "sourcePageName": "Name der aktuellen Seite",
                  "targetPageName": "Name der Zielseite",
                  "displaceButtonLabel": "Knopfbeschriftung des Knopfes, der auf der Zielseite verdrängt/ersetzt werden soll (optional, falls Zielseite voll)",
                  "displaceTargetPageName": "Zielseite für den verdrängten Knopf (optional, falls displaceButtonLabel gesetzt)",
                  "targetPlacementDescription": "Beschreibung der Platzierung, z.B. 'Reihe 1 Spalte 2' oder 'anstelle von X' (optional)"
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

        val validTypes = setOf("MOVE_BUTTON", "DEACTIVATE_BUTTON", "SPLIT_PAGE")
        for (i in 0 until actionsArray.length()) {
            val actObj = actionsArray.getJSONObject(i)
            val type = actObj.optString("type", "").trim()
            if (type !in validTypes) {
                Log.w("BookRestructureProposalUseCase", "Skipping proposal action with invalid type: '$type'")
                continue
            }
            val rationale = actObj.optString("rationale", "").trim()
            if (rationale.isEmpty()) {
                Log.w("BookRestructureProposalUseCase", "Skipping proposal action with empty rationale")
                continue
            }
            val buttonLabel = actObj.optString("buttonLabel", "").trim().takeIf { it.isNotEmpty() }
            val sourcePageName = actObj.optString("sourcePageName", "").trim().takeIf { it.isNotEmpty() }
            val targetPageName = actObj.optString("targetPageName", "").trim().takeIf { it.isNotEmpty() }
            val displaceButtonLabel = actObj.optString("displaceButtonLabel", "").trim().takeIf { it.isNotEmpty() }
            val displaceTargetPageName = actObj.optString("displaceTargetPageName", "").trim().takeIf { it.isNotEmpty() }
            val targetPlacementDescription = actObj.optString("targetPlacementDescription", "").trim().takeIf { it.isNotEmpty() }

            if (type == "MOVE_BUTTON") {
                if (buttonLabel == null || sourcePageName == null || targetPageName == null) {
                    Log.w("BookRestructureProposalUseCase", "Skipping MOVE_BUTTON action due to missing buttonLabel ($buttonLabel), sourcePageName ($sourcePageName) or targetPageName ($targetPageName)")
                    continue
                }
            } else if (type == "DEACTIVATE_BUTTON") {
                if (buttonLabel == null || sourcePageName == null) {
                    Log.w("BookRestructureProposalUseCase", "Skipping DEACTIVATE_BUTTON action due to missing buttonLabel ($buttonLabel) or sourcePageName ($sourcePageName)")
                    continue
                }
            } else if (type == "SPLIT_PAGE") {
                if (sourcePageName == null) {
                    Log.w("BookRestructureProposalUseCase", "Skipping SPLIT_PAGE action due to missing sourcePageName")
                    continue
                }
            }

            val newCategoriesArray = actObj.optJSONArray("newCategories")
            val newCategories = if (newCategoriesArray != null) {
                val cats = mutableListOf<CategoryInfo>()
                for (j in 0 until newCategoriesArray.length()) {
                    val catObj = newCategoriesArray.getJSONObject(j)
                    val catName = catObj.optString("name", "").trim()
                    if (catName.isEmpty()) continue
                    val labelsArray = catObj.optJSONArray("buttonLabels")
                    val labels = mutableListOf<String>()
                    if (labelsArray != null) {
                        for (k in 0 until labelsArray.length()) {
                            val lbl = labelsArray.optString(k, "").trim()
                            if (lbl.isNotEmpty()) {
                                labels.add(lbl)
                            }
                        }
                    }
                    if (labels.isNotEmpty()) {
                        cats.add(CategoryInfo(catName, labels))
                    }
                }
                cats.takeIf { it.isNotEmpty() }
            } else null

            if (type == "SPLIT_PAGE" && newCategories.isNullOrEmpty()) {
                Log.w("BookRestructureProposalUseCase", "Skipping SPLIT_PAGE action because newCategories list is empty or invalid")
                continue
            }

            actions.add(
                RestructureAction(
                    type = type,
                    rationale = rationale,
                    buttonLabel = buttonLabel,
                    sourcePageName = sourcePageName,
                    targetPageName = targetPageName,
                    newCategories = newCategories,
                    displaceButtonLabel = displaceButtonLabel,
                    displaceTargetPageName = displaceTargetPageName,
                    targetPlacementDescription = targetPlacementDescription
                )
            )
        }

        return BookRestructureProposal(actions)
    }

    suspend fun execute(pagesJsonString: String, scope: String = "detailed"): BookRestructureProposal {
        val prompt = generatePrompt(pagesJsonString, scope)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }

    suspend fun executeLoadMore(pagesJsonString: String, existingProposalsJson: String): BookRestructureProposal {
        val prompt = generateLoadMorePrompt(pagesJsonString, existingProposalsJson)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }
}
