package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.impl.PageRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.model.Page
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageRepositoryTest {

    private val mockPageDao = mockk<PageDao>(relaxed = true)
    private val mockButtonDao = mockk<ButtonDao>(relaxed = true)
    private val mockDatabase = mockk<AppDatabase>(relaxed = true)
    private lateinit var pageRepository: PageRepositoryImpl

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
        pageRepository = PageRepositoryImpl(mockPageDao, mockButtonDao, mockDatabase)
    }

    @After
    fun teardown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `getAllPagesFlow returns flow from dao`() = runTest {
        val pages = listOf(Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList()))
        every { mockPageDao.getAllPagesFlow() } returns flowOf(pages)

        pageRepository.getAllPagesFlow().collect {
            assertEquals(pages, it)
        }
    }

    @Test
    fun `getPagesForBookFlow returns flow from dao`() = runTest {
        val pages = listOf(Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList()))
        every { mockPageDao.getPagesForBookFlow("book1") } returns flowOf(pages)

        pageRepository.getPagesForBookFlow("book1").collect {
            assertEquals(pages, it)
        }
    }

    @Test
    fun `getPageById calls dao getPageById`() = runTest {
        val page = Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList())
        coEvery { mockPageDao.getPageById("1") } returns page

        val result = pageRepository.getPageById("1")
        assertEquals(page, result)
        coVerify { mockPageDao.getPageById("1") }
    }

    @Test
    fun `getAllPages calls dao getAllPages`() = runTest {
        val pages = listOf(Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList()))
        coEvery { mockPageDao.getAllPages() } returns pages

        val result = pageRepository.getAllPages()
        assertEquals(pages, result)
    }

    @Test
    fun `getPagesForBook calls dao getPagesForBook`() = runTest {
        val pages = listOf(Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList()))
        coEvery { mockPageDao.getPagesForBook("book1") } returns pages

        val result = pageRepository.getPagesForBook("book1")
        assertEquals(pages, result)
    }

    @Test
    fun `insertPage calls dao insertPageEntity`() = runTest {
        val page = Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList())
        pageRepository.insertPage(page)
        coVerify { mockPageDao.insertPageEntity(any()) }
    }

    @Test
    fun `updatePage calls dao updatePageEntity`() = runTest {
        val page = Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList())
        pageRepository.updatePage(page)
        coVerify { mockPageDao.updatePageEntity(any()) }
    }

    @Test
    fun `updatePage writes button tombstones when buttons are removed`() = runTest {
        val pageId = "1"
        val bookId = "book1"
        
        val existingButtons = listOf(
            ButtonEntity(id = "button1", pageId = pageId, globalIndex = 0, label = "B1", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
            ButtonEntity(id = "button2", pageId = pageId, globalIndex = 1, label = "B2", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction())
        )
        
        coEvery { mockButtonDao.getButtonsForPage(pageId) } returns existingButtons
        
        val updatedPage = Page(
            id = pageId,
            name = "Page 1",
            bookId = bookId,
            buttonConfigs = listOf(
                com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "button1", label = "B1", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction())
            )
        )
        
        val mockDeletedEntityDao = mockk<DeletedEntityDao>(relaxed = true)
        every { mockDatabase.deletedEntityDao() } returns mockDeletedEntityDao
        
        pageRepository.updatePage(updatedPage)
        
        coVerify(exactly = 1) {
            mockDeletedEntityDao.insertDeletedEntity(
                match {
                    it.entityId == "button2" && it.entityType == "BUTTON" && it.bookId == bookId
                }
            )
        }
    }
    @Test
    fun `deletePage calls dao deletePageEntity`() = runTest {
        val page = Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList())
        pageRepository.deletePage(page)
        coVerify { mockPageDao.deletePageEntity(any()) }
    }

    @Test
    fun `updatePage writes no tombstone when button set is unchanged or grows`() = runTest {
        val pageId = "1"
        val bookId = "book1"
        
        val existingButtons = listOf(
            ButtonEntity(id = "button1", pageId = pageId, globalIndex = 0, label = "B1", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction())
        )
        coEvery { mockButtonDao.getButtonsForPage(pageId) } returns existingButtons
        
        val updatedPage = Page(
            id = pageId,
            name = "Page 1",
            bookId = bookId,
            buttonConfigs = listOf(
                com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "button1", label = "B1", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()),
                com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "button2", label = "B2", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction())
            )
        )
        
        val mockDeletedEntityDao = mockk<DeletedEntityDao>(relaxed = true)
        every { mockDatabase.deletedEntityDao() } returns mockDeletedEntityDao
        
        pageRepository.updatePage(updatedPage)
        
        coVerify(exactly = 0) {
            mockDeletedEntityDao.insertDeletedEntity(any())
        }
    }

    @Test
    fun `insertPage overwrites timestamps with current time`() = runTest {
        val button = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "button1", label = "B1", updatedAt = 1000L)
        val page = Page(id = "page1", bookId = "book1", name = "Page 1", updatedAt = 1000L, buttonConfigs = listOf(button))

        val capturedPage = io.mockk.slot<Page>()
        coEvery { mockPageDao.insertPageEntity(capture(capturedPage)) } returns Unit

        pageRepository.insertPage(page)

        org.junit.Assert.assertTrue(capturedPage.captured.updatedAt > 1000L)
    }

    @Test
    fun `insertPageRaw preserves original page and button timestamps`() = runTest {
        val button = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "button1", label = "B1", updatedAt = 1000L)
        val page = Page(id = "page1", bookId = "book1", name = "Page 1", updatedAt = 1000L, buttonConfigs = listOf(button))

        val capturedPage = io.mockk.slot<Page>()
        coEvery { mockPageDao.insertPageEntity(capture(capturedPage)) } returns Unit

        pageRepository.insertPageRaw(page)

        assertEquals(1000L, capturedPage.captured.updatedAt)
    }
}
