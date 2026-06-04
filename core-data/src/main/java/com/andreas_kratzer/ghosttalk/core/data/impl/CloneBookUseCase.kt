package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionEntity
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloneBookUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
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
        val pageIdMap = oldPagesWithButtons.associate { it.page.id to UUID.randomUUID().toString() }
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

                            // Find first empty grid index in destination page
                            val destOccupiedIndices = destPage.buttons.map { it.globalIndex }.toSet()
                            var targetSlot = 0
                            while (destOccupiedIndices.contains(targetSlot)) {
                                targetSlot++
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
}
