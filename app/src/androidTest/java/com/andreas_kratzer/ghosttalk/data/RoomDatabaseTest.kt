package com.andreas_kratzer.ghosttalk.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.PageRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.database.AppDatabase
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonDao
import com.andreas_kratzer.ghosttalk.core.database.PageDao
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var bookDao: BookDao
    private lateinit var pageDao: PageDao
    private lateinit var buttonDao: ButtonDao
    private lateinit var pageRepository: PageRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Ein In-Memory Repository, das nach dem Prozess stirbt.
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        bookDao = db.bookDao()
        pageDao = db.pageDao()
        buttonDao = db.buttonDao()
        pageRepository = PageRepositoryImpl(pageDao, buttonDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadBook() = runBlocking {
        val book = Book(id = "book1", name = "Test Book")
        bookDao.insertBook(book)

        val retrievedBook = bookDao.getBookById("book1")
        assertNotNull(retrievedBook)
        assertEquals("Test Book", retrievedBook?.name)
    }

    @Test
    fun getPagesForBook_filtersCorrectly() = runBlocking {
        // Books
        val book1 = Book(id = "b1", name = "Book A")
        val book2 = Book(id = "b2", name = "Book B")
        bookDao.insertBook(book1)
        bookDao.insertBook(book2)

        // Pages
        val page1 = Page(id = "p1", bookId = "b1", name = "Page A1", rows = 2, columns = 2, buttonConfigs = emptyList())
        val page2 = Page(id = "p2", bookId = "b1", name = "Page A2", rows = 2, columns = 2, buttonConfigs = emptyList())
        val page3 = Page(id = "p3", bookId = "b2", name = "Page B1", rows = 2, columns = 2, buttonConfigs = emptyList())

        pageRepository.insertPage(page1)
        pageRepository.insertPage(page2)
        pageRepository.insertPage(page3)

        val pagesBook1 = pageRepository.getPagesForBook("b1")
        val pagesBook2 = pageRepository.getPagesForBook("b2")

        assertEquals(2, pagesBook1.size)
        assertEquals(1, pagesBook2.size)
        assertEquals("p3", pagesBook2[0].id)
    }

    @Test
    fun serializeAndDeserializeComplexPage() = runBlocking {
        val book = Book(id = "complexBook", name = "Complex")
        bookDao.insertBook(book)

        // A highly customized button configuration to test normalization and roundtrip deeply
        val buttonConfig1 = ButtonConfig(
            id = "b1",
            label = "Speak Label",
            spokenText = "I override the label",
            auditoryCue = AuditoryCue.TextToSpeechCue("Hint Text"),
            buttonAction = SpeakTextButtonAction()
        )

        val buttonConfig2 = ButtonConfig(
            id = "b2",
            label = "Nav Label",
            spokenText = "Going Home",
            auditoryCue = AuditoryCue.TextToSpeechCue("Go Home Hint"),
            buttonAction = NavigateToPageButtonAction("homeId")
        )

        val buttons = listOf(buttonConfig1, null, buttonConfig2, null)

        val page = Page(
            id = "complexPage",
            bookId = "complexBook",
            name = "Normalized DB Test Page",
            rows = 2,
            columns = 2,
            buttonConfigs = buttons
        )

        pageRepository.insertPage(page)

        val retrievedPage = pageRepository.getPageById("complexPage")
        assertNotNull(retrievedPage)
        assertEquals("Normalized DB Test Page", retrievedPage?.name)
        assertEquals(49, retrievedPage?.buttonConfigs?.size)
        
        // Slot 1: Speak Text Config
        val loadedBtn1 = retrievedPage?.buttonConfigs?.get(0)!!
        assertEquals("Speak Label", loadedBtn1.label)
        assertEquals("I override the label", loadedBtn1.spokenText)
        assertTrue(loadedBtn1.buttonAction is SpeakTextButtonAction)

        // Slot 2: Null
        assertNull(retrievedPage.buttonConfigs[1])

        // Slot 3: Navigate Action
        val loadedBtn2 = retrievedPage.buttonConfigs[2]!!
        assertTrue(loadedBtn2.buttonAction is NavigateToPageButtonAction)
        assertEquals("homeId", (loadedBtn2.buttonAction as NavigateToPageButtonAction).pageId)
    }
}
