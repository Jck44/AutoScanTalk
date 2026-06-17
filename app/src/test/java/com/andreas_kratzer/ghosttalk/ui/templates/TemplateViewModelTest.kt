package com.andreas_kratzer.ghosttalk.ui.templates

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.domain.templates.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.GetTemplateUsagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.UpdateButtonConfigInTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
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
    private lateinit var pageRepository: com.andreas_kratzer.ghosttalk.core.data.PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var createTemplateUseCase: CreateTemplateUseCase
    private lateinit var deleteTemplateUseCase: DeleteTemplateUseCase
    private lateinit var updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase
    private lateinit var getTemplateUsagesUseCase: GetTemplateUsagesUseCase
    private lateinit var geminiUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
    private lateinit var viewModel: TemplateViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        templateRepository = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        createTemplateUseCase = mockk<CreateTemplateUseCase>(relaxed = true)
        deleteTemplateUseCase = mockk<DeleteTemplateUseCase>(relaxed = true)
        updateButtonConfigInTemplateUseCase = mockk<UpdateButtonConfigInTemplateUseCase>(relaxed = true)
        getTemplateUsagesUseCase = mockk<GetTemplateUsagesUseCase>(relaxed = true)
        geminiUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase>(relaxed = true)
        val buttonTemplateDelegate = mockk<com.andreas_kratzer.ghosttalk.ui.pages.delegates.ButtonTemplateDelegate>(relaxed = true)

        every { settingsRepository.templateSortOrderFlow } returns MutableStateFlow(SortOrder.A_Z.name)
        every { templateRepository.getAllTemplates() } returns flowOf(emptyList())
        every { pageRepository.getUsedTemplateIdsFlow() } returns flowOf(emptySet())

        viewModel = TemplateViewModel(
            templateRepository,
            pageRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase,
            geminiUseCase,
            buttonTemplateDelegate
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
            pageRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase,
            geminiUseCase,
            mockk(relaxed = true)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.templates.value.size)

        viewModel.updateSearchQuery("Apple")
        advanceUntilIdle()

        assertEquals(1, viewModel.templates.value.size)
        assertEquals("Apple", viewModel.templates.value.first().name)
    }

    @Test
    fun `suggestRowName returns category suggestion based on template row buttons`() = runTest {
        val templateId = "t1"
        val button1 = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(label = "Hund")
        val button2 = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(label = "Katze")
        
        val buttonConfigs = MutableList<com.andreas_kratzer.ghosttalk.core.model.ButtonConfig?>(49) { null }
        buttonConfigs[0] = button1
        buttonConfigs[1] = button2
        
        val template = PageTemplate(
            id = templateId,
            name = "Animals",
            rows = 4,
            columns = 4,
            buttonConfigs = buttonConfigs
        )
        
        every { templateRepository.getAllTemplates() } returns flowOf(listOf(template))
        every { settingsRepository.isGeminiEnabled } returns true
        io.mockk.coEvery { geminiUseCase.generateResponse(any()) } returns "Haustiere"

        viewModel = TemplateViewModel(
            templateRepository,
            pageRepository,
            settingsRepository,
            createTemplateUseCase,
            deleteTemplateUseCase,
            updateButtonConfigInTemplateUseCase,
            getTemplateUsagesUseCase,
            geminiUseCase,
            mockk(relaxed = true)
        )
        advanceUntilIdle()

        var suggestionResult = ""
        viewModel.suggestRowName(templateId, rowIndex = 0) { result ->
            suggestionResult = result
        }
        advanceUntilIdle()

        assertEquals("Haustiere", suggestionResult)
    }
}
