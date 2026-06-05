package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.model.PageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageLayoutProposalUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase
) {

    fun generatePrompt(targetPageName: String, description: String, subpages: List<String>, buttonsJsonString: String): String {
        val subpagesStr = if (subpages.isNotEmpty()) subpages.joinToString(", ") { "„$it“" } else "keine"

        return """
            Du bist ein Experte für Unterstützte Kommunikation (AAC).
            Bestimme das Knopflayout für die vorgeschlagene Seite „$targetPageName“.
            BESCHREIBUNG: „$description“
            UNTERSEITEN (Als CREATE_NAV_BUTTON anlegen): $subpagesStr

            VERFÜGBARE KNÖPFE (Nutze die Klickübergänge 'nextButtonTransitions' für ergonomische Nähe!):
            $buttonsJsonString

            REGELN:
            1. MOVE_BUTTON: Wenn du einen Knopf auswählst, trage zwingend seine exakte "id" in das Feld "buttonId" ein.
            2. KAPAZITÄT: Die Summe aller Knöpfe (MOVE_BUTTON + CREATE_NAV_BUTTON) darf MAXIMAL 12 bis 16 betragen.
            3. Steuerungs-Knöpfe wie "Zurück" oder "Startseite" werden vom System automatisch hinzugefügt, schlage diese hier NICHT vor.

            Antworte AUSSCHLIESSLICH als JSON (ohne Markdown-Formatierung wie ```json ... ```):
            {
              "pageName": "$targetPageName",
              "actions": [
                {
                  "type": "MOVE_BUTTON",
                  "buttonId": "exakte_original_button_id",
                  "buttonLabel": "Knopfbeschriftung",
                  "sourcePageName": "Originalseite",
                  "rationale": "Begründung..."
                },
                {
                  "type": "CREATE_NAV_BUTTON",
                  "buttonLabel": "Name der Unterseite",
                  "targetPageName": "Name der Unterseite",
                  "rationale": "Navigationsverknüpfung zur Unterseite..."
                }
              ]
            }
        """.trimIndent()
    }

    fun parseResponse(response: String): PageLayoutProposal {
        val firstBrace = response.indexOf('{')
        val lastBrace = response.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || firstBrace > lastBrace) {
            throw IllegalArgumentException("Invalid JSON response from Gemini for Page Layout")
        }
        val cleanedJson = response.substring(firstBrace, lastBrace + 1)
        val root = JSONObject(cleanedJson)
        val actionsArray = root.getJSONArray("actions")
        val actions = mutableListOf<PageButtonAction>()

        for (i in 0 until actionsArray.length()) {
            val actObj = actionsArray.getJSONObject(i)
            actions.add(
                PageButtonAction(
                    type = actObj.getString("type"),
                    buttonLabel = actObj.getString("buttonLabel"),
                    rationale = actObj.getString("rationale"),
                    buttonId = actObj.optString("buttonId", "").takeIf { it.isNotEmpty() },
                    sourcePageName = actObj.optString("sourcePageName", "").takeIf { it.isNotEmpty() },
                    targetPageName = actObj.optString("targetPageName", "").takeIf { it.isNotEmpty() }
                )
            )
        }
        return PageLayoutProposal(root.getString("pageName"), actions)
    }

    suspend fun execute(
        targetPageName: String,
        description: String,
        subpages: List<String>,
        buttonsJsonString: String
    ): PageLayoutProposal {
        val prompt = generatePrompt(targetPageName, description, subpages, buttonsJsonString)
        val response = geminiUseCase.generateResponse(prompt)
        return parseResponse(response)
    }
}
