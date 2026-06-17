package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetFilteredPagesUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>().apply {
        every { defaultStartPageIdFlow } returns MutableStateFlow<String?>(null)
    }
    private val useCase = GetFilteredPagesUseCase(settingsRepository)

    @Test
    fun `execute filters pages by search query`() = runTest {
        // Arrange
        val pages = listOf(
            createPage("1", "Apple"),
            createPage("2", "Banana"),
            createPage("3", "Cherry")
        )
        val allPagesFlow = flowOf(pages)
        val searchQueryFlow = flowOf("ba")
        
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow(SortOrder.A_Z.name)

        // Act
        val result = useCase.execute(allPagesFlow, searchQueryFlow, flowOf(emptySet())).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals("Banana", result[0].name)
    }

    @Test
    fun `execute sorts pages by A-Z`() = runTest {
        // Arrange
        val pages = listOf(
            createPage("1", "Cherry"),
            createPage("2", "Apple"),
            createPage("3", "Banana")
        )
        val allPagesFlow = flowOf(pages)
        val searchQueryFlow = flowOf("")
        
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow(SortOrder.A_Z.name)

        // Act
        val result = useCase.execute(allPagesFlow, searchQueryFlow, flowOf(emptySet())).first()

        // Assert
        assertEquals("Apple", result[0].name)
        assertEquals("Banana", result[1].name)
        assertEquals("Cherry", result[2].name)
    }

    @Test
    fun `execute sorts pages by Z-A`() = runTest {
        // Arrange
        val pages = listOf(
            createPage("1", "Apple"),
            createPage("2", "Cherry"),
            createPage("3", "Banana")
        )
        val allPagesFlow = flowOf(pages)
        val searchQueryFlow = flowOf("")
        
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow(SortOrder.Z_A.name)

        // Act
        val result = useCase.execute(allPagesFlow, searchQueryFlow, flowOf(emptySet())).first()

        // Assert
        assertEquals("Cherry", result[0].name)
        assertEquals("Banana", result[1].name)
        assertEquals("Apple", result[2].name)
    }

    @Test
    fun `execute sorts pages by ACTIVE_FIRST`() = runTest {
        // Arrange
        val pages = listOf(
            createPage("1", "Apple"),
            createPage("2", "Cherry"),
            createPage("3", "Banana")
        )
        val allPagesFlow = flowOf(pages)
        val searchQueryFlow = flowOf("")
        val activeIdsFlow = flowOf(setOf("2"))
        
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow(SortOrder.ACTIVE_FIRST.name)

        // Act
        val result = useCase.execute(allPagesFlow, searchQueryFlow, activeIdsFlow).first()

        // Assert
        assertEquals("Cherry", result[0].name) // Active first
        assertEquals("Apple", result[1].name)  // Then A-Z
        assertEquals("Banana", result[2].name)
    }

    private fun createPage(id: String, name: String) = Page(
        id = id,
        bookId = "book1",
        name = name,
        templateId = null,
        rows = 4,
        columns = 4,
        scanPattern = null,
        rowNames = emptyList(),
        orderIndex = 0,
        createdAt = System.currentTimeMillis()
    )
}
