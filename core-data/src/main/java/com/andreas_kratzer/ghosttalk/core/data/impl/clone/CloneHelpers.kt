package com.andreas_kratzer.ghosttalk.core.data.impl.clone

import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import java.util.UUID

object CloneHelpers {

    fun optimalGridUpTo5(buttonCount: Int): Pair<Int, Int> {
        return when {
            buttonCount <= 4 -> 2 to 2
            buttonCount <= 9 -> 3 to 3
            buttonCount <= 16 -> 4 to 4
            else -> 5 to 5
        }
    }

    fun optimalGridUpTo7(buttonCount: Int): Pair<Int, Int> {
        return when {
            buttonCount <= 4 -> 2 to 2
            buttonCount <= 9 -> 3 to 3
            buttonCount <= 16 -> 4 to 4
            buttonCount <= 25 -> 5 to 5
            buttonCount <= 36 -> 6 to 6
            else -> 7 to 7
        }
    }

    fun expandGridByOne(rows: Int, cols: Int): Pair<Int, Int> {
        var r = rows
        var c = cols
        if (c < 7) {
            c++
        } else if (r < 7) {
            r++
        }
        return Pair(r, c)
    }

    fun expandGridToFit(rows: Int, cols: Int, totalButtons: Int): Pair<Int, Int> {
        var r = rows
        var c = cols
        while (r * c < totalButtons && (r < 7 || c < 7)) {
            if (c < r && c < 7) {
                c++
            } else if (r < 7) {
                r++
            } else {
                c++
            }
        }
        return Pair(r, c)
    }

    fun findFreeSlot(occupied: Set<Int>, excluded: Int? = null): Int {
        var slot = 0
        while (occupied.contains(slot) || slot == excluded) {
            slot++
        }
        return slot
    }

    fun createNavButton(
        id: String,
        pageId: String,
        targetPageId: String,
        label: String,
        slot: Int,
        isActive: Boolean,
        spokenText: String = "Öffne $label",
        auditoryCueText: String = "Öffne $label"
    ): ButtonEntity {
        return ButtonEntity(
            id = id,
            pageId = pageId,
            globalIndex = slot,
            label = label,
            spokenText = spokenText,
            auditoryCue = AuditoryCue.TextToSpeechCue(auditoryCueText),
            buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
            isActive = isActive
        )
    }

    fun chunkButtons(
        basePageName: String,
        buttons: List<ButtonEntity>,
        targetBookId: String,
        basePageId: String,
        templateId: String?,
        isActive: Boolean,
        gridStrategy: (Int) -> Pair<Int, Int>,
        navButtonCreator: (curPageId: String, nextPageId: String, pageIndex: Int) -> ButtonEntity
    ): List<MutablePageWithButtons> {
        if (buttons.isEmpty()) {
            val grid = gridStrategy(0)
            val page = Page(
                id = basePageId,
                bookId = targetBookId,
                name = basePageName,
                templateId = templateId,
                rows = grid.first,
                columns = grid.second,
                createdAt = System.currentTimeMillis()
            )
            return listOf(MutablePageWithButtons(page, mutableListOf()))
        }

        val maxButtonsPerPage = 48
        val chunkCount = (buttons.size + maxButtonsPerPage - 1) / maxButtonsPerPage
        val pageIds = mutableMapOf<Int, String>()
        pageIds[1] = basePageId
        for (p in 2..chunkCount) {
            pageIds[p] = UUID.randomUUID().toString()
        }

        val result = mutableListOf<MutablePageWithButtons>()
        var btnIdx = 0

        for (p in 1..chunkCount) {
            val curPageId = pageIds[p]!!
            val curPageName = if (p == 1) basePageName else "$basePageName $p"
            val limit = if (p < chunkCount) 48 else 49

            val pageButtons = mutableListOf<ButtonEntity>()
            var gIdx = 0
            while (gIdx < limit && btnIdx < buttons.size) {
                pageButtons.add(
                    buttons[btnIdx++].copy(
                        pageId = curPageId,
                        globalIndex = gIdx++
                    )
                )
            }

            if (p < chunkCount) {
                val nextPageId = pageIds[p + 1]!!
                val navBtn = navButtonCreator(curPageId, nextPageId, p)
                pageButtons.add(navBtn)
            }

            val (optimalRows, optimalCols) = gridStrategy(pageButtons.size)

            val page = Page(
                id = curPageId,
                bookId = targetBookId,
                name = curPageName,
                templateId = templateId,
                rows = optimalRows,
                columns = optimalCols,
                createdAt = System.currentTimeMillis()
            )
            result.add(MutablePageWithButtons(page, pageButtons))
        }

        return result
    }
}
