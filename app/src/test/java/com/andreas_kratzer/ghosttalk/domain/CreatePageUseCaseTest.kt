package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CreatePageUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var createPageUseCase: CreatePageUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        createPageUseCase = CreatePageUseCase(pageRepository, settingsRepository, templateRepository, bookRepository)
    }

    @Test
    fun `execute with template creates page with template configuration`() = runTest {
        // Given
        val template = PageTemplate("template1", "Test Template", 2, 2, listOf(null, null, null, null))
        coEvery { templateRepository.getById("template1") } returns template

        // When
        createPageUseCase.execute("New Page", 4, 4, "book1", emptyList(), "template1")

        // Then
        coVerify {
            pageRepository.insertPage(match {
                it.name == "New Page" &&
                it.rows == 2 &&
                it.columns == 2 &&
                it.buttonConfigs.size == 4
            })
        }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `execute without template creates page with default home button`() = runTest {
        // Given
        coEvery { settingsRepository.defaultStartPageId } returns "home"

        // When
        createPageUseCase.execute("New Page", 2, 2, "book1", emptyList(), null)

        // Then
        coVerify {
            pageRepository.insertPage(match {
                it.name == "New Page" &&
                it.rows == 2 &&
                it.columns == 2 &&
                it.buttonConfigs.size == 4 &&
                it.buttonConfigs[3] != null &&
                it.buttonConfigs[3]!!.label == "zurück zum Start"
            })
        }
        coVerify { bookRepository.updateLastModified("book1") }
    }
}
