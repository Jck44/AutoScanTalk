package com.andreas_kratzer.ghosttalk.ui.templates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.templates.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.GetTemplateUsagesUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.ReorderTemplatesUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.UpdateButtonConfigInTemplateUseCase
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
    private lateinit var getTemplateUsagesUseCase: GetTemplateUsagesUseCase
    private lateinit var viewModel: TemplateViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        templateRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        createTemplateUseCase = mockk<CreateTemplateUseCase>(relaxed = true)
        deleteTemplateUseCase = mockk<DeleteTemplateUseCase>(relaxed = true)
        reorderTemplatesUseCase = mockk<ReorderTemplatesUseCase>(relaxed = true)
        updateButtonConfigInTemplateUseCase = mockk<UpdateButtonConfigInTemplateUseCase>(relaxed = true)
        getTemplateUsagesUseCase = mockk<GetTemplateUsagesUseCase>(relaxed = true)

        every { settingsRepository.templateSortOrderFlow } returns MutableStateFlow(SortOrder.MANUAL.name)
        every { settingsRepository.experimentalManualSortingFlow } returns MutableStateFlow(false)
        every { templateRepository.getAllTemplates() } returns flowOf(emptyList())

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase
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
        val template = PageTemplate(
            id = "id",
            name = "Name",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList()
        )
        viewModel.deleteTemplate(template)
        advanceUntilIdle()

        coVerify { deleteTemplateUseCase.execute(template) }
    }

    @Test
    fun `updateSearchQuery updates flow and filters results`() = runTest {
        val t1 = PageTemplate(id = "1", name = "Apple", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 0)
        val t2 = PageTemplate(id = "2", name = "Banana", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 1)
        every { templateRepository.getAllTemplates() } returns flowOf(listOf(t1, t2))

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase
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
        val t1 = PageTemplate(id = "1", name = "T1", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 0)
        val t2 = PageTemplate(id = "2", name = "T2", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 1)
        val templates = listOf(t1, t2)
        every { templateRepository.getAllTemplates() } returns flowOf(templates)

        viewModel = TemplateViewModel(
            templateRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            reorderTemplatesUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase
        )
        advanceUntilIdle()

        viewModel.reorderTemplates(0, 1)
        advanceUntilIdle()

        coVerify { reorderTemplatesUseCase.execute(match { it.size == 2 }, 0, 1) }
    }
}
