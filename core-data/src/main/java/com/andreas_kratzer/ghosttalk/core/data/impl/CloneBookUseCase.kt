package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionEntity
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloneBookUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val bookRepository: BookRepository,
    private val prefs: SharedPreferences
) {

    private data class MutablePageWithButtons(
        var page: Page,
        val buttons: MutableList<ButtonEntity>
    )

    suspend fun execute(
        sourceBookId: String,
        proposal: BookRestructureProposal? = null
    ): String = appDatabase.withTransaction {
        val sourceBook = bookRepository.getBookById(sourceBookId)
            ?: throw IllegalArgumentException("Source book $sourceBookId not found")

        val targetBookId = UUID.randomUUID().toString()
        val targetBookName = "[Vorschlag] ${sourceBook.name}"
        val targetBook = sourceBook.copy(
            id = targetBookId,
            name = targetBookName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Load all original pages and their button entities
        val oldPagesWithButtons = appDatabase.pageDao().getPagesForBookWithButtons(sourceBookId)

        // Generate mapping of oldPageId to newPageId
        val pageIdMap = oldPagesWithButtons.associate { oldP ->
            val oldId = oldP.page.id
            val newId = if (oldId.startsWith("static_row_")) {
                "static_row_$targetBookId"
            } else {
                UUID.randomUUID().toString()
            }
            oldId to newId
        }
        val buttonIdMap = mutableMapOf<String, String>()

        // 1. Initial deep copy in memory, keeping layout structure
        val mutablePages = oldPagesWithButtons.map { oldP ->
            val newPageId = pageIdMap[oldP.page.id] ?: UUID.randomUUID().toString()
            val newPage = oldP.page.copy(
                id = newPageId,
                bookId = targetBookId,
                createdAt = System.currentTimeMillis()
            )

            val newButtons = oldP.buttons.map { oldB ->
                val newButtonId = UUID.randomUUID().toString()
                buttonIdMap[oldB.id] = newButtonId
                oldB.copy(
                    id = newButtonId,
                    pageId = newPageId
                )
            }.toMutableList()

            MutablePageWithButtons(newPage, newButtons)
        }.toMutableList()

        // 2. Map NavigateToPageButtonAction target pageId to new mapped pageIds
        mutablePages.forEach { pageWithButtons ->
            pageWithButtons.buttons.replaceAll { buttonEntity ->
                val action = buttonEntity.buttonAction
                if (action is NavigateToPageButtonAction) {
                    val mappedTargetId = pageIdMap[action.pageId]
                    if (mappedTargetId != null) {
                        buttonEntity.copy(buttonAction = NavigateToPageButtonAction(mappedTargetId))
                    } else {
                        buttonEntity
                    }
                } else {
                    buttonEntity
                }
            }
        }

        // 3. Apply restructuring proposals if provided
        proposal?.actions?.forEach { action ->
            when (action.type) {
                "MOVE_BUTTON" -> {
                    val srcPageName = action.sourcePageName ?: return@forEach
                    val targetPageName = action.targetPageName ?: return@forEach
                    val btnLabel = action.buttonLabel ?: return@forEach

                    val srcPage = mutablePages.find { it.page.name.equals(srcPageName, ignoreCase = true) }
                    val destPage = mutablePages.find { it.page.name.equals(targetPageName, ignoreCase = true) }

                    if (srcPage != null && destPage != null) {
                        val btnIndex = srcPage.buttons.indexOfFirst { it.label.equals(btnLabel, ignoreCase = true) }
                        if (btnIndex != -1) {
                            val buttonToMove = srcPage.buttons.removeAt(btnIndex)

                            val displaceLabel = action.displaceButtonLabel
                            val displaceTargetName = action.displaceTargetPageName
                            var targetSlot = -1

                            if (!displaceLabel.isNullOrBlank()) {
                                val displaceIndex = destPage.buttons.indexOfFirst { it.label.equals(displaceLabel, ignoreCase = true) }
                                if (displaceIndex != -1) {
                                    val buttonToDisplace = destPage.buttons.removeAt(displaceIndex)
                                    targetSlot = buttonToDisplace.globalIndex

                                    val displaceDestPage = if (!displaceTargetName.isNullOrBlank()) {
                                        mutablePages.find { it.page.name.equals(displaceTargetName, ignoreCase = true) }
                                    } else null

                                    if (displaceDestPage != null) {
                                        val displaceOccupied = displaceDestPage.buttons.map { it.globalIndex }.toSet()
                                        var displaceSlot = 0
                                        while (displaceOccupied.contains(displaceSlot)) {
                                            displaceSlot++
                                        }
                                        val maxDisplaceSlots = displaceDestPage.page.rows * displaceDestPage.page.columns
                                        if (displaceSlot >= maxDisplaceSlots) {
                                            var newRows = displaceDestPage.page.rows
                                            var newCols = displaceDestPage.page.columns
                                            if (newCols < 7) newCols++
                                            else if (newRows < 7) newRows++
                                            displaceDestPage.page = displaceDestPage.page.copy(rows = newRows, columns = newCols)
                                        }
                                        displaceDestPage.buttons.add(buttonToDisplace.copy(
                                            pageId = displaceDestPage.page.id,
                                            globalIndex = displaceSlot
                                        ))
                                    } else {
                                        val destOccupied = destPage.buttons.map { it.globalIndex }.toSet()
                                        var freeSlot = 0
                                        while (destOccupied.contains(freeSlot) || freeSlot == targetSlot) {
                                            freeSlot++
                                        }
                                        val maxDestSlots = destPage.page.rows * destPage.page.columns
                                        if (freeSlot >= maxDestSlots) {
                                            var newRows = destPage.page.rows
                                            var newCols = destPage.page.columns
                                            if (newCols < 7) newCols++
                                            else if (newRows < 7) newRows++
                                            destPage.page = destPage.page.copy(rows = newRows, columns = newCols)
                                        }
                                        destPage.buttons.add(buttonToDisplace.copy(
                                            isActive = false,
                                            globalIndex = freeSlot
                                        ))
                                    }
                                }
                            }

                            if (targetSlot == -1) {
                                // Find first empty grid index in destination page
                                val destOccupiedIndices = destPage.buttons.map { it.globalIndex }.toSet()
                                targetSlot = 0
                                while (destOccupiedIndices.contains(targetSlot)) {
                                    targetSlot++
                                }
                            }

                            // If destination page is smaller than targetSlot, expand it up to 7x7
                            val maxSlots = destPage.page.rows * destPage.page.columns
                            if (targetSlot >= maxSlots) {
                                var newRows = destPage.page.rows
                                var newCols = destPage.page.columns
                                if (newCols < 7) {
                                    newCols++
                                } else if (newRows < 7) {
                                    newRows++
                                }
                                destPage.page = destPage.page.copy(rows = newRows, columns = newCols)
                            }

                            val movedButton = buttonToMove.copy(
                                pageId = destPage.page.id,
                                globalIndex = targetSlot
                            )
                            destPage.buttons.add(movedButton)
                        }
                    }
                }
                "DEACTIVATE_BUTTON" -> {
                    val srcPageName = action.sourcePageName ?: return@forEach
                    val btnLabel = action.buttonLabel ?: return@forEach

                    val srcPage = mutablePages.find { it.page.name.equals(srcPageName, ignoreCase = true) }
                    if (srcPage != null) {
                        val btnIndex = srcPage.buttons.indexOfFirst { it.label.equals(btnLabel, ignoreCase = true) }
                        if (btnIndex != -1) {
                            srcPage.buttons[btnIndex] = srcPage.buttons[btnIndex].copy(isActive = false)
                        }
                    }
                }
                "SPLIT_PAGE" -> {
                    val pageName = action.sourcePageName ?: return@forEach
                    val newCategories = action.newCategories ?: return@forEach

                    val parentPage = mutablePages.find { it.page.name.equals(pageName, ignoreCase = true) }
                    if (parentPage != null) {
                        newCategories.forEach { category ->
                            val catPageId = UUID.randomUUID().toString()
                            
                            // Determine grid size for subpage
                            val buttonCount = category.buttonLabels.size
                            val (catRows, catCols) = when {
                                buttonCount <= 4 -> 2 to 2
                                buttonCount <= 9 -> 3 to 3
                                buttonCount <= 16 -> 4 to 4
                                else -> 5 to 5
                            }

                            val newSubpage = Page(
                                id = catPageId,
                                bookId = targetBookId,
                                name = category.name,
                                rows = catRows,
                                columns = catCols,
                                createdAt = System.currentTimeMillis()
                            )
                            val subpageButtons = mutableListOf<ButtonEntity>()
                            val subpageWrapper = MutablePageWithButtons(newSubpage, subpageButtons)

                            // Move matching buttons from parent page to new subpage
                            category.buttonLabels.forEachIndexed { index, label ->
                                val parentBtnIndex = parentPage.buttons.indexOfFirst { it.label.equals(label, ignoreCase = true) }
                                if (parentBtnIndex != -1) {
                                    val buttonToMove = parentPage.buttons.removeAt(parentBtnIndex)
                                    subpageButtons.add(buttonToMove.copy(
                                        pageId = catPageId,
                                        globalIndex = index
                                    ))
                                }
                            }

                            // Add the newly created subpage to our cloned list
                            mutablePages.add(subpageWrapper)

                            // Add a navigation button on the parent page to the new subpage
                            val parentOccupiedIndices = parentPage.buttons.map { it.globalIndex }.toSet()
                            var targetNavSlot = 0
                            while (parentOccupiedIndices.contains(targetNavSlot)) {
                                targetNavSlot++
                            }

                            val navButtonId = UUID.randomUUID().toString()
                            val navButton = ButtonEntity(
                                id = navButtonId,
                                pageId = parentPage.page.id,
                                globalIndex = targetNavSlot,
                                label = category.name,
                                spokenText = "Öffne ${category.name}",
                                auditoryCue = AuditoryCue.TextToSpeechCue("Öffne ${category.name}"),
                                buttonAction = NavigateToPageButtonAction(pageId = catPageId),
                                isActive = true
                            )
                            parentPage.buttons.add(navButton)
                        }
                    }
                }
            }
        }

        // 4. Write Book, Pages, and Buttons to database
        bookRepository.insertBook(targetBook)
        
        mutablePages.forEach { pageWrapper ->
            appDatabase.pageDao().insertPageEntity(pageWrapper.page)
            appDatabase.buttonDao().insertButtons(pageWrapper.buttons)
        }

        // 5. Duplicate User Mode Sessions (generating new IDs)
        val oldSessions = appDatabase.userModeSessionDao().getSessionsForBookList(sourceBookId)
        val sessionIdMap = mutableMapOf<Long, Long>()

        oldSessions.forEach { oldSession ->
            val clonedSession = UserModeSessionEntity(
                bookId = targetBookId,
                startTime = oldSession.startTime,
                endTime = oldSession.endTime
            )
            val newSessionId = appDatabase.userModeSessionDao().insertSession(clonedSession)
            sessionIdMap[oldSession.id] = newSessionId
        }

        // 6. Duplicate Button Usage Stats
        val oldStats = appDatabase.buttonUsageDao().getAllStatsForBook(sourceBookId)
        oldStats.forEach { oldStat ->
            val mappedPageId = pageIdMap[oldStat.pageId] ?: ""
            val mappedButtonId = buttonIdMap[oldStat.buttonConfigId] ?: ""
            val clonedStat = oldStat.copy(
                bookId = targetBookId,
                buttonConfigId = mappedButtonId,
                pageId = mappedPageId
            )
            appDatabase.buttonUsageDao().upsert(clonedStat)
        }

        // 7. Duplicate Button Usage History
        val oldHistory = appDatabase.buttonUsageDao().getRecentHistoryEvents(sourceBookId, 1000)
        oldHistory.forEach { oldEvent ->
            val mappedPageId = pageIdMap[oldEvent.pageId]
            val mappedButtonId = buttonIdMap[oldEvent.buttonId]
            val mappedSessionId = oldEvent.sessionId?.let { sessionIdMap[it] }
            val clonedEvent = oldEvent.copy(
                id = 0, // autogenerate
                bookId = targetBookId,
                buttonId = mappedButtonId,
                pageId = mappedPageId,
                sessionId = mappedSessionId
            )
            appDatabase.buttonUsageDao().insertHistoryEvent(clonedEvent)
        }

        // 8. Clone SharedPreferences Settings
        val allPrefs = prefs.all
        val editor = prefs.edit()
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("${sourceBookId}_")) {
                val newKey = key.replaceFirst("${sourceBookId}_", "${targetBookId}_")
                when (value) {
                    is String -> {
                        if (key.endsWith(SettingsConstants.KEY_DEFAULT_START_PAGE_ID)) {
                            val newPageId = pageIdMap[value] ?: value
                            editor.putString(newKey, newPageId)
                        } else {
                            editor.putString(newKey, value)
                        }
                    }
                    is Boolean -> editor.putBoolean(newKey, value)
                    is Int -> editor.putInt(newKey, value)
                    is Long -> editor.putLong(newKey, value)
                    is Float -> editor.putFloat(newKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(newKey, value as Set<String>)
                    }
                }
            }
        }
        editor.apply()

        // Return the new targetBookId
        targetBookId
    }

    suspend fun applyHierarchyRestructure(
        sourceBookId: String,
        proposal: BookHierarchyProposal,
        layouts: Map<String, PageLayoutProposal>
    ): String = appDatabase.withTransaction {
        val sourceBook = bookRepository.getBookById(sourceBookId)
            ?: throw IllegalArgumentException("Source book ${sourceBookId} not found")

        val targetBookId = UUID.randomUUID().toString()
        val targetBookName = "[Vorschlag] ${sourceBook.name}"
        val targetBook = sourceBook.copy(
            id = targetBookId,
            name = targetBookName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Load all original pages and their button entities
        val oldPagesWithButtons = appDatabase.pageDao().getPagesForBookWithButtons(sourceBookId)
        val allOriginalButtons = oldPagesWithButtons.flatMap { it.buttons }

        // Determine hierarchy mapping of old pages to new pages
        val pageIdMap = mutableMapOf<String, String>() // proposed page name -> new page UUID
        val originalPageIdMap = mutableMapOf<String, String>() // old page UUID -> new page UUID
        
        // Add special "Archiv" page ID to preserve unassigned buttons
        val archivPageId = UUID.randomUUID().toString()
        pageIdMap["Archiv"] = archivPageId

        proposal.pages.forEach { node ->
            val newPageId = UUID.randomUUID().toString()
            pageIdMap[node.name] = newPageId
            
            if (!node.sourcePageName.isNullOrBlank()) {
                val origPage = oldPagesWithButtons.find { it.page.name.equals(node.sourcePageName, ignoreCase = true) }?.page
                if (origPage != null) {
                    originalPageIdMap[origPage.id] = newPageId
                }
            } else {
                val origPage = oldPagesWithButtons.find { it.page.name.equals(node.name, ignoreCase = true) }?.page
                if (origPage != null) {
                    originalPageIdMap[origPage.id] = newPageId
                }
            }
        }

        // Create the proposed Page entities
        val newPages = mutableListOf<MutablePageWithButtons>()
        
        proposal.pages.forEach { node ->
            val newPageId = pageIdMap[node.name]!!
            val origPage = oldPagesWithButtons.find { it.page.name.equals(node.sourcePageName ?: node.name, ignoreCase = true) }?.page
            val layout = layouts[node.name]
            val buttonCount = (layout?.actions?.size ?: 0)
            
            val (optimalRows, optimalCols) = when {
                buttonCount <= 4 -> 2 to 2
                buttonCount <= 9 -> 3 to 3
                buttonCount <= 16 -> 4 to 4
                else -> 5 to 5
            }
            
            val newPage = Page(
                id = newPageId,
                bookId = targetBookId,
                name = node.name,
                templateId = origPage?.templateId,
                rows = origPage?.rows ?: optimalRows,
                columns = origPage?.columns ?: optimalCols,
                scanPattern = origPage?.scanPattern,
                rowNames = origPage?.rowNames ?: emptyList(),
                createdAt = System.currentTimeMillis()
            )
            newPages.add(MutablePageWithButtons(newPage, mutableListOf()))
        }

        val placedOriginalButtonIds = mutableSetOf<String>()
        val buttonIdMap = mutableMapOf<String, String>()

        // Process layouts and place buttons
        val extraPages = mutableListOf<MutablePageWithButtons>()
        
        newPages.forEach { pageWrapper ->
            val pageName = pageWrapper.page.name
            if (pageName.startsWith("Archiv")) return@forEach
            
            val layout = layouts[pageName]
            val allPageButtons = mutableListOf<ButtonEntity>()
            
            layout?.actions?.forEach { action ->
                when (action.type) {
                    "MOVE_BUTTON" -> {
                        val origBtn = allOriginalButtons.find { btn ->
                            if (!action.buttonId.isNullOrBlank()) {
                                btn.id == action.buttonId
                            } else {
                                btn.label.equals(action.buttonLabel, ignoreCase = true) &&
                                (action.sourcePageName == null || oldPagesWithButtons.find { it.page.id == btn.pageId }?.page?.name?.equals(action.sourcePageName, ignoreCase = true) == true)
                            }
                        }
                        
                        if (origBtn != null && !placedOriginalButtonIds.contains(origBtn.id)) {
                            placedOriginalButtonIds.add(origBtn.id)
                            val newBtnId = UUID.randomUUID().toString()
                            buttonIdMap[origBtn.id] = newBtnId
                            
                            val mappedAction = when (val btnAct = origBtn.buttonAction) {
                                is NavigateToPageButtonAction -> {
                                    val targetPageName = oldPagesWithButtons.find { it.page.id == btnAct.pageId }?.page?.name
                                    val mappedTargetId = if (targetPageName != null) pageIdMap[targetPageName] else null
                                    if (mappedTargetId != null) NavigateToPageButtonAction(mappedTargetId) else btnAct
                                }
                                else -> btnAct
                            }
                            
                            val movedBtn = origBtn.copy(
                                id = newBtnId,
                                pageId = pageWrapper.page.id,
                                globalIndex = 0, // Will be set later
                                buttonAction = mappedAction,
                                isActive = true
                            )
                            allPageButtons.add(movedBtn)
                        }
                    }
                    "CREATE_NAV_BUTTON" -> {
                        val targetPageId = pageIdMap[action.targetPageName ?: ""]
                        if (targetPageId != null) {
                            val newBtnId = UUID.randomUUID().toString()
                            val navBtn = ButtonEntity(
                                id = newBtnId,
                                pageId = pageWrapper.page.id,
                                globalIndex = 0, // Will be set later
                                label = action.buttonLabel,
                                spokenText = "Öffne ${action.buttonLabel}",
                                auditoryCue = AuditoryCue.TextToSpeechCue("Öffne ${action.buttonLabel}"),
                                buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
                                isActive = true
                            )
                            allPageButtons.add(navBtn)
                        }
                    }
                }
            }
            
            if (allPageButtons.size <= 49) {
                val totalButtons = allPageButtons.size
                var r = pageWrapper.page.rows
                var c = pageWrapper.page.columns
                while (r * c < totalButtons && (r < 7 || c < 7)) {
                    if (c < r && c < 7) c++ else if (r < 7) r++ else c++
                }
                pageWrapper.page = pageWrapper.page.copy(rows = r, columns = c)
                
                allPageButtons.forEachIndexed { idx, btn ->
                    pageWrapper.buttons.add(btn.copy(globalIndex = idx))
                }
            } else {
                val maxButtons = 48
                val chunkCount = (allPageButtons.size + maxButtons - 1) / maxButtons
                val tempPageIds = mutableMapOf<Int, String>()
                tempPageIds[1] = pageWrapper.page.id
                for (p in 2..chunkCount) {
                    tempPageIds[p] = UUID.randomUUID().toString()
                }
                
                var btnIdx = 0
                for (p in 1..chunkCount) {
                    val curPageId = tempPageIds[p]!!
                    val curPageName = if (p == 1) pageName else "$pageName $p"
                    val limit = if (p < chunkCount) 48 else 49
                    
                    val pageButtons = mutableListOf<ButtonEntity>()
                    var gIdx = 0
                    while (gIdx < limit && btnIdx < allPageButtons.size) {
                        pageButtons.add(allPageButtons[btnIdx++].copy(
                            pageId = curPageId,
                            globalIndex = gIdx++
                        ))
                    }
                    
                    if (p < chunkCount) {
                        val nextPageName = "$pageName ${p + 1}"
                        val nextPageId = tempPageIds[p + 1]!!
                        pageButtons.add(ButtonEntity(
                            id = UUID.randomUUID().toString(),
                            pageId = curPageId,
                            globalIndex = 48,
                            label = "Weiter",
                            spokenText = "Öffne Folgeseite $nextPageName",
                            auditoryCue = AuditoryCue.TextToSpeechCue("Öffne $nextPageName"),
                            buttonAction = NavigateToPageButtonAction(pageId = nextPageId),
                            isActive = true
                        ))
                    }
                    
                    val totalButtons = pageButtons.size
                    var r = 4
                    var c = 4
                    while (r * c < totalButtons && (r < 7 || c < 7)) {
                        if (c < r && c < 7) c++ else if (r < 7) r++ else c++
                    }
                    
                    if (p == 1) {
                        pageWrapper.page = pageWrapper.page.copy(rows = r, columns = c)
                        pageWrapper.buttons.addAll(pageButtons)
                    } else {
                        val extraPage = Page(
                            id = curPageId,
                            bookId = targetBookId,
                            name = curPageName,
                            templateId = pageWrapper.page.templateId,
                            rows = r,
                            columns = c,
                            scanPattern = pageWrapper.page.scanPattern,
                            rowNames = pageWrapper.page.rowNames,
                            createdAt = System.currentTimeMillis()
                        )
                        extraPages.add(MutablePageWithButtons(extraPage, pageButtons))
                    }
                }
            }
        }
        newPages.addAll(extraPages)

        // Archive unplaced buttons from selected pages
        val selectedSourcePageIds = oldPagesWithButtons.map { it.page.id }.filter { !it.startsWith("static_row_") }.toSet()
        val unplacedButtons = allOriginalButtons.filter { btn ->
            selectedSourcePageIds.contains(btn.pageId) && !placedOriginalButtonIds.contains(btn.id) && btn.isActive
        }

        val archivPages = mutableListOf<MutablePageWithButtons>()
        if (unplacedButtons.isNotEmpty()) {
            val maxButtonsPerPage = 48 // Reserve slot 48 (index 48) for navigation if there are more pages
            val chunkCount = ((unplacedButtons.size + maxButtonsPerPage - 1) / maxButtonsPerPage).coerceAtLeast(1)
            val tempPageIdMap = mutableMapOf<Int, String>()
            tempPageIdMap[1] = archivPageId
            for (p in 2..chunkCount) {
                tempPageIdMap[p] = UUID.randomUUID().toString()
                pageIdMap["Archiv $p"] = tempPageIdMap[p]!!
            }
            
            var unplacedIdx = 0
            for (p in 1..chunkCount) {
                val curPageId = tempPageIdMap[p]!!
                val pageName = if (p == 1) "Archiv" else "Archiv $p"
                val buttons = mutableListOf<ButtonEntity>()
                val hasNext = p < chunkCount
                val limit = if (hasNext) 48 else 49
                
                var globalIndex = 0
                while (globalIndex < limit && unplacedIdx < unplacedButtons.size) {
                    val origBtn = unplacedButtons[unplacedIdx++]
                    val newBtnId = UUID.randomUUID().toString()
                    buttonIdMap[origBtn.id] = newBtnId
                    
                    val mappedAction = when (val btnAct = origBtn.buttonAction) {
                        is NavigateToPageButtonAction -> {
                            val targetPageName = oldPagesWithButtons.find { it.page.id == btnAct.pageId }?.page?.name
                            val mappedTargetId = if (targetPageName != null) pageIdMap[targetPageName] else null
                            if (mappedTargetId != null) NavigateToPageButtonAction(mappedTargetId) else btnAct
                        }
                        else -> btnAct
                    }
                    
                    buttons.add(origBtn.copy(
                        id = newBtnId,
                        pageId = curPageId,
                        globalIndex = globalIndex++,
                        buttonAction = mappedAction,
                        isActive = false
                    ))
                }
                
                if (hasNext) {
                    val nextPageName = "Archiv ${p + 1}"
                    val nextPageId = tempPageIdMap[p + 1]!!
                    buttons.add(ButtonEntity(
                        id = UUID.randomUUID().toString(),
                        pageId = curPageId,
                        globalIndex = 48,
                        label = nextPageName,
                        spokenText = "Öffne $nextPageName",
                        auditoryCue = AuditoryCue.TextToSpeechCue("Öffne $nextPageName"),
                        buttonAction = NavigateToPageButtonAction(pageId = nextPageId),
                        isActive = false
                    ))
                }
                
                val totalButtons = buttons.size
                val (optimalRows, optimalCols) = when {
                    totalButtons <= 4 -> 2 to 2
                    totalButtons <= 9 -> 3 to 3
                    totalButtons <= 16 -> 4 to 4
                    totalButtons <= 25 -> 5 to 5
                    totalButtons <= 36 -> 6 to 6
                    else -> 7 to 7
                }
                
                val page = Page(
                    id = curPageId,
                    bookId = targetBookId,
                    name = pageName,
                    templateId = null,
                    rows = optimalRows,
                    columns = optimalCols,
                    scanPattern = null,
                    rowNames = emptyList(),
                    createdAt = System.currentTimeMillis()
                )
                
                archivPages.add(MutablePageWithButtons(page, buttons))
            }
        } else {
            val page = Page(
                id = archivPageId,
                bookId = targetBookId,
                name = "Archiv",
                templateId = null,
                rows = 2,
                columns = 2,
                scanPattern = null,
                rowNames = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            archivPages.add(MutablePageWithButtons(page, mutableListOf()))
        }
        
        newPages.addAll(archivPages)

        // 3.9 Copy the static row page if it exists in the source book
        val originalStaticRow = oldPagesWithButtons.find { it.page.id.startsWith("static_row_") }
        if (originalStaticRow != null) {
            val newStaticRowId = "static_row_$targetBookId"
            val newStaticRow = originalStaticRow.page.copy(
                id = newStaticRowId,
                bookId = targetBookId,
                createdAt = System.currentTimeMillis()
            )
            val newButtons = originalStaticRow.buttons.map { oldB ->
                val newBtnId = UUID.randomUUID().toString()
                buttonIdMap[oldB.id] = newBtnId
                
                val mappedAction = when (val btnAct = oldB.buttonAction) {
                    is NavigateToPageButtonAction -> {
                        val targetPageName = oldPagesWithButtons.find { it.page.id == btnAct.pageId }?.page?.name
                        val mappedTargetId = if (targetPageName != null) pageIdMap[targetPageName] else null
                        if (mappedTargetId != null) NavigateToPageButtonAction(mappedTargetId) else btnAct
                    }
                    else -> btnAct
                }
                
                oldB.copy(
                    id = newBtnId,
                    pageId = newStaticRowId,
                    buttonAction = mappedAction
                )
            }
            newPages.add(MutablePageWithButtons(newStaticRow, newButtons.toMutableList()))
        }

        // Database inserts
        bookRepository.insertBook(targetBook)
        
        newPages.forEach { pageWrapper ->
            appDatabase.pageDao().insertPageEntity(pageWrapper.page)
            appDatabase.buttonDao().insertButtons(pageWrapper.buttons)
        }

        // Sessions
        val oldSessions = appDatabase.userModeSessionDao().getSessionsForBookList(sourceBookId)
        val sessionIdMap = mutableMapOf<Long, Long>()

        oldSessions.forEach { oldSession ->
            val clonedSession = UserModeSessionEntity(
                bookId = targetBookId,
                startTime = oldSession.startTime,
                endTime = oldSession.endTime
            )
            val newSessionId = appDatabase.userModeSessionDao().insertSession(clonedSession)
            sessionIdMap[oldSession.id] = newSessionId
        }

        // Stats
        val oldStats = appDatabase.buttonUsageDao().getAllStatsForBook(sourceBookId)
        oldStats.forEach { oldStat ->
            val mappedPageId = if (oldStat.pageId.isNotEmpty()) {
                val origPageName = oldPagesWithButtons.find { it.page.id == oldStat.pageId }?.page?.name
                if (origPageName != null) pageIdMap[origPageName] ?: "" else ""
            } else ""
            val mappedButtonId = buttonIdMap[oldStat.buttonConfigId] ?: ""
            if (mappedButtonId.isNotEmpty()) {
                val clonedStat = oldStat.copy(
                    bookId = targetBookId,
                    buttonConfigId = mappedButtonId,
                    pageId = mappedPageId
                )
                appDatabase.buttonUsageDao().upsert(clonedStat)
            }
        }

        // History
        val oldHistory = appDatabase.buttonUsageDao().getRecentHistoryEvents(sourceBookId, 1000)
        oldHistory.forEach { oldEvent ->
            val origPageName = oldPagesWithButtons.find { it.page.id == oldEvent.pageId }?.page?.name
            val mappedPageId = if (origPageName != null) pageIdMap[origPageName] else null
            val mappedButtonId = buttonIdMap[oldEvent.buttonId]
            val mappedSessionId = oldEvent.sessionId?.let { sessionIdMap[it] }
            
            if (mappedButtonId != null) {
                val clonedEvent = oldEvent.copy(
                    id = 0,
                    bookId = targetBookId,
                    buttonId = mappedButtonId,
                    pageId = mappedPageId,
                    sessionId = mappedSessionId
                )
                appDatabase.buttonUsageDao().insertHistoryEvent(clonedEvent)
            }
        }

        // SharedPrefs Settings
        val allPrefs = prefs.all
        val editor = prefs.edit()
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("${sourceBookId}_")) {
                val newKey = key.replaceFirst("${sourceBookId}_", "${targetBookId}_")
                when (value) {
                    is String -> {
                        if (key.endsWith(SettingsConstants.KEY_DEFAULT_START_PAGE_ID)) {
                            val newPageId = pageIdMap["Hauptseite"] ?: pageIdMap.values.firstOrNull() ?: value
                            editor.putString(newKey, newPageId)
                        } else {
                            editor.putString(newKey, value)
                        }
                    }
                    is Boolean -> editor.putBoolean(newKey, value)
                    is Int -> editor.putInt(newKey, value)
                    is Long -> editor.putLong(newKey, value)
                    is Float -> editor.putFloat(newKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(newKey, value as Set<String>)
                    }
                }
            }
        }
        editor.apply()

        targetBookId
    }
}
