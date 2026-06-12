package com.andreas_kratzer.ghosttalk.core.data.impl

import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.clone.BookDataCloner
import com.andreas_kratzer.ghosttalk.core.data.impl.clone.CloneHelpers
import com.andreas_kratzer.ghosttalk.core.data.impl.clone.MutablePageWithButtons
import com.andreas_kratzer.ghosttalk.core.data.impl.clone.RestructureActionApplier
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloneBookUseCase @Inject constructor(
    private val appDatabase: AppDatabase,
    private val bookRepository: BookRepository,
    private val dataCloner: BookDataCloner,
    private val actionApplier: RestructureActionApplier
) {

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

        val oldPagesWithButtons = appDatabase.pageDao().getPagesForBookWithButtons(sourceBookId)

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

        if (proposal != null) {
            actionApplier.applyRestructureActions(proposal.actions, mutablePages, targetBookId)
        }

        bookRepository.insertBook(targetBook)
        
        mutablePages.forEach { pageWrapper ->
            appDatabase.pageDao().insertPageEntity(pageWrapper.page)
            appDatabase.buttonDao().insertButtons(pageWrapper.buttons)
        }

        val sessionIdMap = dataCloner.cloneSessions(sourceBookId, targetBookId)

        dataCloner.cloneStats(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            buttonIdMap = buttonIdMap,
            mapPageId = { pageIdMap[it] ?: "" },
            skipUnmappedButton = false
        )

        dataCloner.cloneHistory(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            buttonIdMap = buttonIdMap,
            sessionIdMap = sessionIdMap,
            mapPageId = { pageIdMap[it] },
            skipUnmappedButton = false
        )

        dataCloner.cloneBookPrefs(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            mapStartPageId = { pageIdMap[it] ?: it }
        )

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

        val oldPagesWithButtons = appDatabase.pageDao().getPagesForBookWithButtons(sourceBookId)
        val allOriginalButtons = oldPagesWithButtons.flatMap { it.buttons }

        val pageIdMap = mutableMapOf<String, String>()
        val originalPageIdMap = mutableMapOf<String, String>()
        
        val archivPageId = UUID.randomUUID().toString()
        pageIdMap["Archiv"] = archivPageId

        proposal.pages.forEach { node ->
            val newPageId = UUID.randomUUID().toString()
            pageIdMap[node.name] = newPageId
            
            val searchName = node.sourcePageName ?: node.name
            val origPage = oldPagesWithButtons.find { it.page.name.equals(searchName, ignoreCase = true) }?.page
            if (origPage != null) {
                originalPageIdMap[origPage.id] = newPageId
            }
        }

        val newPages = mutableListOf<MutablePageWithButtons>()
        
        proposal.pages.forEach { node ->
            val newPageId = pageIdMap[node.name]!!
            val origPage = oldPagesWithButtons.find { it.page.name.equals(node.sourcePageName ?: node.name, ignoreCase = true) }?.page
            val layout = layouts[node.name]
            val buttonCount = (layout?.actions?.size ?: 0)
            val (optimalRows, optimalCols) = CloneHelpers.optimalGridUpTo5(buttonCount)
            
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
                                globalIndex = 0,
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
                            val navBtn = CloneHelpers.createNavButton(
                                id = newBtnId,
                                pageId = pageWrapper.page.id,
                                targetPageId = targetPageId,
                                label = action.buttonLabel,
                                slot = 0,
                                isActive = true
                            )
                            allPageButtons.add(navBtn)
                        }
                    }
                }
            }
            
            val chunked = CloneHelpers.chunkButtons(
                basePageName = pageName,
                buttons = allPageButtons,
                targetBookId = targetBookId,
                basePageId = pageWrapper.page.id,
                templateId = pageWrapper.page.templateId,
                isActive = true,
                gridStrategy = { CloneHelpers.expandGridToFit(4, 4, it) },
                navButtonCreator = { curPageId, nextPageId, pageIndex ->
                    val nextPageName = "$pageName ${pageIndex + 1}"
                    CloneHelpers.createNavButton(
                        id = UUID.randomUUID().toString(),
                        pageId = curPageId,
                        targetPageId = nextPageId,
                        label = "Weiter",
                        slot = 48,
                        isActive = true,
                        spokenText = "Öffne Folgeseite $nextPageName",
                        auditoryCueText = "Öffne $nextPageName"
                    )
                }
            )

            chunked.forEachIndexed { index, chunk ->
                if (index == 0) {
                    pageWrapper.page = chunk.page
                    pageWrapper.buttons.addAll(chunk.buttons)
                } else {
                    extraPages.add(chunk)
                }
            }
        }
        newPages.addAll(extraPages)

        val selectedSourcePageIds = oldPagesWithButtons.map { it.page.id }.filter { !it.startsWith("static_row_") }.toSet()
        val unplacedButtons = allOriginalButtons.filter { btn ->
            selectedSourcePageIds.contains(btn.pageId) && !placedOriginalButtonIds.contains(btn.id) && btn.isActive
        }

        val archivPages = mutableListOf<MutablePageWithButtons>()
        if (unplacedButtons.isNotEmpty()) {
            val mappedUnplaced = unplacedButtons.map { origBtn ->
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
                origBtn.copy(
                    id = newBtnId,
                    buttonAction = mappedAction,
                    isActive = false
                )
            }

            val chunkedArchiv = CloneHelpers.chunkButtons(
                basePageName = "Archiv",
                buttons = mappedUnplaced,
                targetBookId = targetBookId,
                basePageId = archivPageId,
                templateId = null,
                isActive = false,
                gridStrategy = { CloneHelpers.optimalGridUpTo7(it) },
                navButtonCreator = { curPageId, nextPageId, pageIndex ->
                    val nextPageName = "Archiv ${pageIndex + 1}"
                    if (pageIndex >= 1) {
                        pageIdMap["Archiv ${pageIndex + 1}"] = nextPageId
                    }
                    CloneHelpers.createNavButton(
                        id = UUID.randomUUID().toString(),
                        pageId = curPageId,
                        targetPageId = nextPageId,
                        label = nextPageName,
                        slot = 48,
                        isActive = false
                    )
                }
            )
            archivPages.addAll(chunkedArchiv)
        } else {
            val page = Page(
                id = archivPageId,
                bookId = targetBookId,
                name = "Archiv",
                rows = 2,
                columns = 2,
                createdAt = System.currentTimeMillis()
            )
            archivPages.add(MutablePageWithButtons(page, mutableListOf()))
        }
        
        newPages.addAll(archivPages)

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

        bookRepository.insertBook(targetBook)
        
        newPages.forEach { pageWrapper ->
            appDatabase.pageDao().insertPageEntity(pageWrapper.page)
            appDatabase.buttonDao().insertButtons(pageWrapper.buttons)
        }

        val sessionIdMap = dataCloner.cloneSessions(sourceBookId, targetBookId)

        dataCloner.cloneStats(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            buttonIdMap = buttonIdMap,
            mapPageId = { pageId ->
                val origPageName = oldPagesWithButtons.find { it.page.id == pageId }?.page?.name
                if (origPageName != null) pageIdMap[origPageName] ?: "" else ""
            },
            skipUnmappedButton = true
        )

        dataCloner.cloneHistory(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            buttonIdMap = buttonIdMap,
            sessionIdMap = sessionIdMap,
            mapPageId = { pageId ->
                val origPageName = oldPagesWithButtons.find { it.page.id == pageId }?.page?.name
                if (origPageName != null) pageIdMap[origPageName] else null
            },
            skipUnmappedButton = true
        )

        dataCloner.cloneBookPrefs(
            sourceBookId = sourceBookId,
            targetBookId = targetBookId,
            mapStartPageId = { pageIdMap["Hauptseite"] ?: pageIdMap.values.firstOrNull() ?: it }
        )

        targetBookId
    }
}
