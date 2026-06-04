package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.database.PageWithButtons
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionDao
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionEntity
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.RestructureAction
import com.andreas_kratzer.ghosttalk.core.model.CategoryInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CloneBookUseCaseTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabase = mockk<AppDatabase>(relaxed = true)
    private val mockBookRepository = mockk<BookRepository>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val mockPageDao = mockk<PageDao>(relaxed = true)
    private val mockButtonDao = mockk<ButtonDao>(relaxed = true)
    private val mockUserModeSessionDao = mockk<UserModeSessionDao>(relaxed = true)
    private val mockButtonUsageDao = mockk<ButtonUsageDao>(relaxed = true)

    private lateinit var cloneBookUseCase: CloneBookUseCase

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        every { mockDatabase.pageDao() } returns mockPageDao
        every { mockDatabase.buttonDao() } returns mockButtonDao
        every { mockDatabase.userModeSessionDao() } returns mockUserModeSessionDao
        every { mockDatabase.buttonUsageDao() } returns mockButtonUsageDao

        every { mockPrefs.edit() } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putInt(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putLong(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putFloat(any(), any()) } returns mockPrefsEditor

        cloneBookUseCase = CloneBookUseCase(
            context = mockContext,
            appDatabase = mockDatabase,
            bookRepository = mockBookRepository,
            prefs = mockPrefs
        )
    }

    @After
    fun teardown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `cloning book duplicates pages, buttons, sessions, stats, history and preferences correctly`() = runTest {
        val sourceBookId = "srcBookId"
        val sourceBook = Book(
            id = sourceBookId,
            name = "My Book",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val page1Id = "p1"
        val page2Id = "p2"
        val button1Id = "b1"
        val button2Id = "b2"

        val page1 = Page(id = page1Id, bookId = sourceBookId, name = "Page 1", rows = 3, columns = 3)
        val page2 = Page(id = page2Id, bookId = sourceBookId, name = "Page 2", rows = 3, columns = 3)

        val button1 = ButtonEntity(
            id = button1Id,
            pageId = page1Id,
            globalIndex = 0,
            label = "Say Hello",
            buttonAction = NavigateToPageButtonAction(page2Id),
            isActive = true
        )
        val button2 = ButtonEntity(
            id = button2Id,
            pageId = page2Id,
            globalIndex = 1,
            label = "Say Bye",
            buttonAction = NavigateToPageButtonAction(page1Id),
            isActive = true
        )

        val oldPagesWithButtons = listOf(
            PageWithButtons(page = page1, buttons = listOf(button1)),
            PageWithButtons(page = page2, buttons = listOf(button2))
        )

        val oldSessions = listOf(
            UserModeSessionEntity(id = 11L, bookId = sourceBookId, startTime = 5000L, endTime = 6000L)
        )

        val oldStats = listOf(
            ButtonUsageStat(
                bookId = sourceBookId,
                buttonConfigId = button1Id,
                pageId = page1Id,
                label = "Say Hello",
                actionJson = "{}",
                usageCount = 5
            )
        )

        val oldHistory = listOf(
            ButtonUsageHistoryEntity(
                id = 101L,
                bookId = sourceBookId,
                timestamp = 5500L,
                label = "Say Hello",
                actionType = "NAVIGATE",
                buttonId = button1Id,
                pageId = page1Id,
                sessionId = 11L
            )
        )

        val oldPrefs = mapOf(
            "${sourceBookId}_some_pref" to "value1",
            "${sourceBookId}_${SettingsConstants.KEY_DEFAULT_START_PAGE_ID}" to page1Id,
            "${sourceBookId}_int_pref" to 42,
            "${sourceBookId}_bool_pref" to true,
            "${sourceBookId}_long_pref" to 100L,
            "${sourceBookId}_float_pref" to 3.14f,
            "${sourceBookId}_set_pref" to setOf("a", "b"),
            "other_book_pref" to "should_not_copy"
        )

        coEvery { mockBookRepository.getBookById(sourceBookId) } returns sourceBook
        coEvery { mockPageDao.getPagesForBookWithButtons(sourceBookId) } returns oldPagesWithButtons
        coEvery { mockUserModeSessionDao.getSessionsForBookList(sourceBookId) } returns oldSessions
        coEvery { mockButtonUsageDao.getAllStatsForBook(sourceBookId) } returns oldStats
        coEvery { mockButtonUsageDao.getRecentHistoryEvents(sourceBookId, 1000) } returns oldHistory
        every { mockPrefs.all } returns oldPrefs
        coEvery { mockUserModeSessionDao.insertSession(any()) } returns 22L

        val targetBookId = cloneBookUseCase.execute(sourceBookId, proposal = null)

        assertNotEquals(sourceBookId, targetBookId)
        assertTrue(targetBookId.isNotBlank())

        val bookSlot = slot<Book>()
        coVerify { mockBookRepository.insertBook(capture(bookSlot)) }
        assertEquals(targetBookId, bookSlot.captured.id)
        assertEquals("[Vorschlag] My Book", bookSlot.captured.name)

        val pageSlots = mutableListOf<Page>()
        coVerify(exactly = 2) { mockPageDao.insertPageEntity(capture(pageSlots)) }
        assertEquals(2, pageSlots.size)
        val newPage1 = pageSlots.find { it.name == "Page 1" }
        val newPage2 = pageSlots.find { it.name == "Page 2" }
        assertNotNull(newPage1)
        assertNotNull(newPage2)
        assertNotEquals(page1Id, newPage1!!.id)
        assertNotEquals(page2Id, newPage2!!.id)
        assertEquals(targetBookId, newPage1.bookId)
        assertEquals(targetBookId, newPage2.bookId)

        val buttonSlots = mutableListOf<List<ButtonEntity>>()
        coVerify(exactly = 2) { mockButtonDao.insertButtons(capture(buttonSlots)) }
        val allInsertedButtons = buttonSlots.flatten()
        assertEquals(2, allInsertedButtons.size)

        val newButton1 = allInsertedButtons.find { it.label == "Say Hello" }
        val newButton2 = allInsertedButtons.find { it.label == "Say Bye" }
        assertNotNull(newButton1)
        assertNotNull(newButton2)
        assertNotEquals(button1Id, newButton1!!.id)
        assertNotEquals(button2Id, newButton2!!.id)
        assertEquals(newPage1.id, newButton1.pageId)
        assertEquals(newPage2.id, newButton2.pageId)

        // Verify action mappings: b1 navigated to p2, so newButton1 must navigate to newPage2.id
        val action1 = newButton1.buttonAction as NavigateToPageButtonAction
        assertEquals(newPage2.id, action1.pageId)

        // Verify session insertion
        val sessionSlot = slot<UserModeSessionEntity>()
        coVerify { mockUserModeSessionDao.insertSession(capture(sessionSlot)) }
        assertEquals(targetBookId, sessionSlot.captured.bookId)
        assertEquals(5000L, sessionSlot.captured.startTime)
        assertEquals(6000L, sessionSlot.captured.endTime)

        // Verify button usage stats
        val statSlot = slot<ButtonUsageStat>()
        coVerify { mockButtonUsageDao.upsert(capture(statSlot)) }
        assertEquals(targetBookId, statSlot.captured.bookId)
        assertEquals(newButton1.id, statSlot.captured.buttonConfigId)
        assertEquals(newPage1.id, statSlot.captured.pageId)
        assertEquals(5, statSlot.captured.usageCount)

        // Verify history events
        val historySlot = slot<ButtonUsageHistoryEntity>()
        coVerify { mockButtonUsageDao.insertHistoryEvent(capture(historySlot)) }
        assertEquals(targetBookId, historySlot.captured.bookId)
        assertEquals(newButton1.id, historySlot.captured.buttonId)
        assertEquals(newPage1.id, historySlot.captured.pageId)
        assertEquals(22L, historySlot.captured.sessionId)

        // Verify preferences rewrite
        coVerify { mockPrefsEditor.putString("${targetBookId}_some_pref", "value1") }
        coVerify { mockPrefsEditor.putString("${targetBookId}_${SettingsConstants.KEY_DEFAULT_START_PAGE_ID}", newPage1.id) }
        coVerify { mockPrefsEditor.putInt("${targetBookId}_int_pref", 42) }
        coVerify { mockPrefsEditor.putBoolean("${targetBookId}_bool_pref", true) }
        coVerify { mockPrefsEditor.putLong("${targetBookId}_long_pref", 100L) }
        coVerify { mockPrefsEditor.putFloat("${targetBookId}_float_pref", 3.14f) }
        coVerify { mockPrefsEditor.putStringSet("${targetBookId}_set_pref", setOf("a", "b")) }
        coVerify(exactly = 0) { mockPrefsEditor.putString("other_book_pref", any()) }
        coVerify { mockPrefsEditor.apply() }
    }

    @Test
    fun `cloning book with MOVE_BUTTON restructure proposal applies action`() = runTest {
        val sourceBookId = "srcBookId"
        val sourceBook = Book(id = sourceBookId, name = "My Book")

        val page1Id = "p1"
        val page2Id = "p2"

        val page1 = Page(id = page1Id, bookId = sourceBookId, name = "Hauptseite", rows = 3, columns = 3)
        val page2 = Page(id = page2Id, bookId = sourceBookId, name = "Kategorie 1", rows = 3, columns = 3)

        val btnToMoveEntity = ButtonEntity(
            id = "btnToMove",
            pageId = page2Id,
            globalIndex = 0,
            label = "Apple",
            buttonAction = NavigateToPageButtonAction("some_page"),
            isActive = true
        )

        val oldPagesWithButtons = listOf(
            PageWithButtons(page = page1, buttons = emptyList()),
            PageWithButtons(page = page2, buttons = listOf(btnToMoveEntity))
        )

        coEvery { mockBookRepository.getBookById(sourceBookId) } returns sourceBook
        coEvery { mockPageDao.getPagesForBookWithButtons(sourceBookId) } returns oldPagesWithButtons

        val proposal = BookRestructureProposal(
            actions = listOf(
                RestructureAction(
                    type = "MOVE_BUTTON",
                    rationale = "Apple wird sehr oft geklickt, sollte auf die Hauptseite.",
                    buttonLabel = "Apple",
                    sourcePageName = "Kategorie 1",
                    targetPageName = "Hauptseite"
                )
            )
        )

        val targetBookId = cloneBookUseCase.execute(sourceBookId, proposal)

        val pageSlots = mutableListOf<Page>()
        coVerify(exactly = 2) { mockPageDao.insertPageEntity(capture(pageSlots)) }
        val newHauptseite = pageSlots.find { it.name == "Hauptseite" }
        val newKategorie1 = pageSlots.find { it.name == "Kategorie 1" }
        assertNotNull(newHauptseite)
        assertNotNull(newKategorie1)

        val buttonSlots = mutableListOf<List<ButtonEntity>>()
        coVerify(exactly = 2) { mockButtonDao.insertButtons(capture(buttonSlots)) }
        val allInsertedButtons = buttonSlots.flatten()
        
        // The button was removed from Kategorie 1 and added to Hauptseite
        val movedButton = allInsertedButtons.find { it.label == "Apple" }
        assertNotNull(movedButton)
        assertEquals(newHauptseite!!.id, movedButton!!.pageId)
        assertEquals(0, movedButton.globalIndex) // first empty slot on Hauptseite
    }

    @Test
    fun `cloning book with DEACTIVATE_BUTTON restructure proposal applies action`() = runTest {
        val sourceBookId = "srcBookId"
        val sourceBook = Book(id = sourceBookId, name = "My Book")

        val page1Id = "p1"
        val page1 = Page(id = page1Id, bookId = sourceBookId, name = "Hauptseite", rows = 3, columns = 3)

        val btnToDeactivate = ButtonEntity(
            id = "btnToDeactivate",
            pageId = page1Id,
            globalIndex = 0,
            label = "Rarely Used",
            buttonAction = NavigateToPageButtonAction("some_page"),
            isActive = true
        )

        val oldPagesWithButtons = listOf(
            PageWithButtons(page = page1, buttons = listOf(btnToDeactivate))
        )

        coEvery { mockBookRepository.getBookById(sourceBookId) } returns sourceBook
        coEvery { mockPageDao.getPagesForBookWithButtons(sourceBookId) } returns oldPagesWithButtons

        val proposal = BookRestructureProposal(
            actions = listOf(
                RestructureAction(
                    type = "DEACTIVATE_BUTTON",
                    rationale = "Deaktivieren da selten genutzt",
                    buttonLabel = "Rarely Used",
                    sourcePageName = "Hauptseite"
                )
            )
        )

        val targetBookId = cloneBookUseCase.execute(sourceBookId, proposal)

        val buttonSlots = mutableListOf<List<ButtonEntity>>()
        coVerify(exactly = 1) { mockButtonDao.insertButtons(capture(buttonSlots)) }
        val allInsertedButtons = buttonSlots.flatten()

        val deactivatedButton = allInsertedButtons.find { it.label == "Rarely Used" }
        assertNotNull(deactivatedButton)
        assertFalse(deactivatedButton!!.isActive)
    }

    @Test
    fun `cloning book with SPLIT_PAGE restructure proposal applies action`() = runTest {
        val sourceBookId = "srcBookId"
        val sourceBook = Book(id = sourceBookId, name = "My Book")

        val page1Id = "p1"
        val page1 = Page(id = page1Id, bookId = sourceBookId, name = "Hauptseite", rows = 3, columns = 3)

        val btn1 = ButtonEntity(id = "b1", pageId = page1Id, globalIndex = 0, label = "Apple", buttonAction = NavigateToPageButtonAction(""), isActive = true)
        val btn2 = ButtonEntity(id = "b2", pageId = page1Id, globalIndex = 1, label = "Banana", buttonAction = NavigateToPageButtonAction(""), isActive = true)
        val btn3 = ButtonEntity(id = "b3", pageId = page1Id, globalIndex = 2, label = "Other", buttonAction = NavigateToPageButtonAction(""), isActive = true)

        val oldPagesWithButtons = listOf(
            PageWithButtons(page = page1, buttons = listOf(btn1, btn2, btn3))
        )

        coEvery { mockBookRepository.getBookById(sourceBookId) } returns sourceBook
        coEvery { mockPageDao.getPagesForBookWithButtons(sourceBookId) } returns oldPagesWithButtons

        val proposal = BookRestructureProposal(
            actions = listOf(
                RestructureAction(
                    type = "SPLIT_PAGE",
                    rationale = "Split page to categorize food buttons",
                    sourcePageName = "Hauptseite",
                    newCategories = listOf(
                        CategoryInfo(
                            name = "Food",
                            buttonLabels = listOf("Apple", "Banana")
                        )
                    )
                )
            )
        )

        val targetBookId = cloneBookUseCase.execute(sourceBookId, proposal)

        // Verify pages inserted: Hauptseite and the new Food subpage
        val pageSlots = mutableListOf<Page>()
        coVerify(atLeast = 2) { mockPageDao.insertPageEntity(capture(pageSlots)) }
        
        val newHauptseite = pageSlots.find { it.name == "Hauptseite" }
        val newFoodSubpage = pageSlots.find { it.name == "Food" }
        assertNotNull(newHauptseite)
        assertNotNull(newFoodSubpage)

        val buttonSlots = mutableListOf<List<ButtonEntity>>()
        coVerify(atLeast = 2) { mockButtonDao.insertButtons(capture(buttonSlots)) }
        val allInsertedButtons = buttonSlots.flatten()

        // Apple and Banana should have been moved to Food subpage
        val movedApple = allInsertedButtons.find { it.label == "Apple" }
        val movedBanana = allInsertedButtons.find { it.label == "Banana" }
        assertNotNull(movedApple)
        assertNotNull(movedBanana)
        assertEquals(newFoodSubpage!!.id, movedApple!!.pageId)
        assertEquals(newFoodSubpage.id, movedBanana!!.pageId)

        // Other should remain on Hauptseite
        val remainingOther = allInsertedButtons.find { it.label == "Other" }
        assertNotNull(remainingOther)
        assertEquals(newHauptseite!!.id, remainingOther!!.pageId)

        // A navigation button "Food" should have been created on Hauptseite
        val navButton = allInsertedButtons.find { it.label == "Food" && it.pageId == newHauptseite.id }
        assertNotNull(navButton)
        val navAction = navButton!!.buttonAction as NavigateToPageButtonAction
        assertEquals(newFoodSubpage.id, navAction.pageId)
    }

    @Test
    fun `cloning book with MOVE_BUTTON and displacement swap applies swap correctly`() = runTest {
        val sourceBookId = "srcBookId"
        val sourceBook = Book(id = sourceBookId, name = "My Book")

        val page1Id = "p1"
        val page2Id = "p2"
        val page3Id = "p3"

        val page1 = Page(id = page1Id, bookId = sourceBookId, name = "Hauptseite", rows = 2, columns = 2)
        val page2 = Page(id = page2Id, bookId = sourceBookId, name = "Kategorie 1", rows = 2, columns = 2)
        val page3 = Page(id = page3Id, bookId = sourceBookId, name = "Unterseite 1", rows = 2, columns = 2)

        val btnToMoveEntity = ButtonEntity(
            id = "btnToMove",
            pageId = page2Id,
            globalIndex = 0,
            label = "Apple",
            buttonAction = NavigateToPageButtonAction(""),
            isActive = true
        )

        // Target page (Hauptseite) has a button "Banana" at slot 1
        val btnToDisplaceEntity = ButtonEntity(
            id = "btnToDisplace",
            pageId = page1Id,
            globalIndex = 1,
            label = "Banana",
            buttonAction = NavigateToPageButtonAction(""),
            isActive = true
        )

        val oldPagesWithButtons = listOf(
            PageWithButtons(page = page1, buttons = listOf(btnToDisplaceEntity)),
            PageWithButtons(page = page2, buttons = listOf(btnToMoveEntity)),
            PageWithButtons(page = page3, buttons = emptyList())
        )

        coEvery { mockBookRepository.getBookById(sourceBookId) } returns sourceBook
        coEvery { mockPageDao.getPagesForBookWithButtons(sourceBookId) } returns oldPagesWithButtons

        val proposal = BookRestructureProposal(
            actions = listOf(
                RestructureAction(
                    type = "MOVE_BUTTON",
                    rationale = "Apple nach Hauptseite, verdrängt Banana nach Unterseite 1.",
                    buttonLabel = "Apple",
                    sourcePageName = "Kategorie 1",
                    targetPageName = "Hauptseite",
                    displaceButtonLabel = "Banana",
                    displaceTargetPageName = "Unterseite 1",
                    targetPlacementDescription = "Reihe 1 Spalte 2"
                )
            )
        )

        val targetBookId = cloneBookUseCase.execute(sourceBookId, proposal)

        val buttonSlots = mutableListOf<List<ButtonEntity>>()
        coVerify { mockButtonDao.insertButtons(capture(buttonSlots)) }
        val allInsertedButtons = buttonSlots.flatten()

        val movedButton = allInsertedButtons.find { it.label == "Apple" }
        val displacedButton = allInsertedButtons.find { it.label == "Banana" }

        assertNotNull(movedButton)
        assertNotNull(displacedButton)

        // Verify "Apple" took slot 1 (Banana's old slot) on Hauptseite
        assertEquals(1, movedButton!!.globalIndex)

        // Verify "Banana" was moved to Unterseite 1
        val pageSlots = mutableListOf<Page>()
        coVerify { mockPageDao.insertPageEntity(capture(pageSlots)) }
        val newHauptseite = pageSlots.find { it.name == "Hauptseite" }
        val newUnterseite1 = pageSlots.find { it.name == "Unterseite 1" }
        assertNotNull(newHauptseite)
        assertNotNull(newUnterseite1)

        assertEquals(newHauptseite!!.id, movedButton.pageId)
        assertEquals(newUnterseite1!!.id, displacedButton!!.pageId)
        assertEquals(0, displacedButton.globalIndex) // first free slot on Unterseite 1
    }
}
