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
    fun `deletePage calls dao deletePageEntity`() = runTest {
        val page = Page(id = "1", name = "Page 1", bookId = "book1", buttonConfigs = emptyList())
        pageRepository.deletePage(page)
        coVerify { mockPageDao.deletePageEntity(any()) }
    }
}
