package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton
import java.util.UUID

internal fun mapImportIndexToGrid(isGhostTalk: Boolean, index: Long, sourceColumns: Int): Int {
    return if (isGhostTalk) {
        index.toInt()
    } else {
        val sourceCols = sourceColumns.coerceAtLeast(1)
        val row = (index / sourceCols).toInt()
        val col = (index % sourceCols).toInt()
        row * 7 + col
    }
}

internal fun autoExpandGrid(rows: Int, cols: Int, buttons: List<ImportButton>): Pair<Int, Int> {
    var finalRows = rows
    var finalCols = cols
    buttons.forEach { button ->
        val maxIndex = button.index.toInt()
        while (finalRows < 7 && finalRows * finalCols <= maxIndex) {
            if (finalCols < 7) finalCols++ else finalRows++
        }
    }
    return Pair(finalRows, finalCols)
}

internal fun extractCloudBookId(bookId: String?, bookName: String?, cloudFileId: String?): String? {
    val extractedId = bookId?.takeIf { it.isNotBlank() } ?: run {
        val name = bookName ?: ""
        val regex = "\\[([a-fA-F0-9-]{36})]".toRegex()
        regex.find(name)?.groupValues?.get(1)
    }

    return (extractedId ?: run {
        val uuidRegex = "[a-fA-F0-9-]{36}".toRegex()
        cloudFileId?.let {
            uuidRegex.find(it)?.value ?: it.removePrefix("book_").removeSuffix(".json")
        }
    })?.trim()?.lowercase()
}

internal fun buildButtonConfigFromImport(
    button: ImportButton,
    forceRegeneration: Boolean,
    pageRegenerated: Boolean,
    finalAction: com.andreas_kratzer.ghosttalk.core.model.ButtonAction,
    warnings: MutableList<String>,
    contextDescription: String
): ButtonConfig {
    val modeString = button.spokenTextMode
    val spokenTextMode = if (modeString != null) {
        try {
            com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.valueOf(modeString)
        } catch (_: IllegalArgumentException) {
            warnings.add("Unbekannter spokenTextMode '$modeString' in $contextDescription – Fallback auf TTS.")
            com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
        }
    } else {
        com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
    }

    val buttonId = if (forceRegeneration || pageRegenerated) {
        UUID.randomUUID().toString()
    } else {
        button.id ?: UUID.randomUUID().toString()
    }

    return ButtonConfig(
        id = buttonId,
        label = button.label,
        spokenText = button.spokenText ?: button.action?.textToSpeech,
        spokenTextMode = spokenTextMode,
        audioFileName = button.audioFileName,
        auditoryCue = button.auditoryCueText?.let { AuditoryCue.TextToSpeechCue(it) },
        isActive = button.active ?: true,
        playActionAsAuditoryCue = button.playActionAsAuditoryCue ?: false,
        buttonAction = finalAction,
        updatedAt = button.updatedAt ?: System.currentTimeMillis()
    )
}
