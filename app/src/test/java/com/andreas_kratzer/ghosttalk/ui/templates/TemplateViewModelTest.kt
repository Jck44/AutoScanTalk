package com.andreas_kratzer.ghosttalk.ui.templates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.ReorderTemplatesUseCase
import com.andreas_kratzer.ghosttalk.domain.UpdateButtonConfigInTemplateUseCase
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var templateRepository: TemplateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var createTemplateUseCase: CreateTemplateUseCase
    private lateinit var deleteTemplateUseCase: DeleteTemplateUseCase
    private lateinit var reorderTemplatesUseCase: ReorderTemplatesUseCase
    private lateinit var updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase
    private lateinit var viewModel: TemplateViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        templateRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        createTemplateUseCase = mockk(relaxed = true)
        deleteTemplateUseCase = mockk(relaxed = true)
        reorderTemplatesUseCase = mockk(relaxed = true)
        updateButtonConfigInTemplateUseCase = mockk(relaxed = true)

        every { settingsRepository.templateSortOrderFlow } returns MutableStateFlow(SortOrder.MANUAL.name)
        every { settingsRepository.experimentalManualSortingFlow } returns MutableStateFlow(false)
        every { templateRepository.getAllTemplates() } returns flowOf(emptyList())

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createTemplate delegates to use case`() = runTest {
        viewModel.createTemplate("New Template", 2, 2)
        advanceUntilIdle()

        coVerify {
            createTemplateUseCase.execute("New Template", 2, 2, null)
        }
    }

    @Test
    fun `deleteTemplate calls use case`() = runTest {
        val template = PageTemplate("id", "Name", 1, 1, emptyList())
        viewModel.deleteTemplate(template)
        advanceUntilIdle()

        coVerify { deleteTemplateUseCase.execute(template) }
    }

    @Test
    fun `updateSearchQuery updates flow and filters results`() = runTest {
        val t1 = PageTemplate("1", "Apple", 1, 1, emptyList(), orderIndex = 0)
        val t2 = PageTemplate("2", "Banana", 1, 1, emptyList(), orderIndex = 1)
        every { templateRepository.getAllTemplates() } returns flowOf(listOf(t1, t2))

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.templates.value.size)

        viewModel.updateSearchQuery("Apple")
        advanceUntilIdle()

        assertEquals(1, viewModel.templates.value.size)
        assertEquals("Apple", viewModel.templates.value.first().name)
    }

    @Test
    fun `reorderTemplates calls use case`() = runTest {
        val t1 = PageTemplate("1", "T1", 1, 1, emptyList(), orderIndex = 0)
        val t2 = PageTemplate("2", "T2", 1, 1, emptyList(), orderIndex = 1)
        val templates = listOf(t1, t2)
        every { templateRepository.getAllTemplates() } returns flowOf(templates)

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase
        )
        advanceUntilIdle()

        viewModel.reorderTemplates(0, 1)
        advanceUntilIdle()

        coVerify { reorderTemplatesUseCase.execute(match { it.size == 2 }, 0, 1) }
    }
}
