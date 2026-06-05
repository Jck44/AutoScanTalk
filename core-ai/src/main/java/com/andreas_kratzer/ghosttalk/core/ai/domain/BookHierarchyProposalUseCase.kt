package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookHierarchyProposalUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase
) {

    fun generatePrompt(pagesJsonString: String, userFeedback: String?, manualEditsJsonString: String?): String {
        val feedbackSection = if (!userFeedback.isNullOrBlank()) "BENUTZER-FEEDBACK:\n\"$userFeedback\"\n" else ""
        val manualEditsSection = if (!manualEditsJsonString.isNullOrBlank()) "MANUELLE BASIS:\n$manualEditsJsonString\n" else ""

        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC) und Ergotherapie.
            Erstelle eine optimierte Seitenhierarchie ab der Startseite ("Hauptseite"), um Navigationswege und Suchzeiten drastisch zu verkürzen.

            Hier ist die aktuelle Struktur inklusive Klickstatistiken, Uhrzeitverteilungen und Klick-Reihenfolgen (Transitions):
            $pagesJsonString

            $manualEditsSection
            $feedbackSection

            STRUKTURIERUNGS-REGELN (AAC BEST PRACTICE):
            1. MOTOR PLANNING & SEQUENZEN: Analysiere das Array 'nextButtonTransitions'. Wenn zwei Knöpfe eine hohe Übergangswahrscheinlichkeit (probability) haben, halte den Folgeknopf maximal 1 Klick entfernt (entweder direkt daneben oder auf der verknüpften Unterseite).
            2. ZEITBASIERTE RELEVANZ: Nutze 'hourlyDistribution', um kontextuelle Unterseiten zu bilden (z. B. Frühstücks- vs. Abendbrot-Begriffe bündeln). Knöpfe mit gleichmäßiger Verteilung über den Tag hinweg sind Kernvokabular und gehören auf leicht erreichbare Ebenen.
            3. KAPAZITÄT: Eine Seite sollte idealerweise 4 bis 9 Knöpfe enthalten, maximal jedoch 12 bis 16 Knöpfe.
            4. SEMANTISCHE CLUSTERUNG:
               - Organisiere das Buch in klare, hierarchische Themenbereiche ab der Startseite ("Hauptseite").
               - Bündele Nahrungsmittel, Mahlzeiten und Getränke unter einem übergeordneten Cluster namens "Ernährung" (oder ähnlich) mit sinnvollen Unterseiten (z. B. "Getränke", "Mahlzeiten").
               - Bündele Wünsche, Gefühle und Handlungen unter einem Cluster namens "Ich möchte" (oder ähnlich) auf der Startseite mit entsprechenden Unterseiten.
               - Bündele Gerätefunktionen (wie Lautstärke, Helligkeit, Einstellungen) unter einem Cluster namens "Gerätefunktionen" oder "Einstellungen".

            CRITICAL SAFETY RULES:
            - Jede einzelne "id" eines Knopfs aus dem Eingabe-JSON MUSS im Rückgabe-JSON auftauchen (entweder in 'buttonIds' einer Seite oder im globalen 'unmappedButtonIds' Topf).
            - Ändere niemals die IDs (keine Tippfehler!).

            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im Format (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "pages": [
                {
                  "name": "Name der Seite",
                  "description": "Begründung auf Deutsch...",
                  "subpages": ["Name einer Unterseite"],
                  "sourcePageName": "Optionale Quellseite",
                  "buttonIds": ["btn_id_1"]
                }
              ],
              "unmappedButtonIds": []
            }
        """.trimIndent()
    }

    fun parseResponse(response: String): BookHierarchyProposal {
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || firstBrace > lastBrace) {
            throw IllegalArgumentException("Invalid JSON response from Gemini for Hierarchy Proposal")
        }
        val cleanedJson = response.substring(firstBrace, lastBrace + 1)
        val root = JSONObject(cleanedJson)
        
        val pagesArray = root.getJSONArray("pages")
        val pages = mutableListOf<HierarchyPageNode>()

        for (i in 0 until pagesArray.length()) {
            val pageObj = pagesArray.getJSONObject(i)
            val subpagesArray = pageObj.optJSONArray("subpages")
            val subpages = mutableListOf<String>()
            subpagesArray?.let { for (j in 0 until it.length()) subpages.add(it.getString(j)) }
            
            val buttonIdsArray = pageObj.optJSONArray("buttonIds")
            val buttonIds = mutableListOf<String>()
            buttonIdsArray?.let { for (k in 0 until it.length()) buttonIds.add(it.getString(k)) }

            pages.add(
                HierarchyPageNode(
                    name = pageObj.getString("name"),
                    description = pageObj.getString("description"),
                    subpages = subpages,
                    sourcePageName = pageObj.optString("sourcePageName", "").takeIf { it.isNotEmpty() },
                    buttonIds = buttonIds
                )
            )
        }

        val unmappedArray = root.optJSONArray("unmappedButtonIds")
        val unmappedButtonIds = mutableListOf<String>()
        unmappedArray?.let { for (i in 0 until it.length()) unmappedButtonIds.add(it.getString(i)) }

        return BookHierarchyProposal(pages, unmappedButtonIds)
    }

    suspend fun execute(
        pagesJsonString: String,
        userFeedback: String? = null,
        manualEditsJsonString: String? = null
    ): BookHierarchyProposal {
        val prompt = generatePrompt(pagesJsonString, userFeedback, manualEditsJsonString)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }
}
