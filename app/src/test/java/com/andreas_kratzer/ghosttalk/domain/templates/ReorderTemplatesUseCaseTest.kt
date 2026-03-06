package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.templates.ReorderTemplatesUseCase
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReorderTemplatesUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var reorderTemplatesUseCase: ReorderTemplatesUseCase

    @Before
    fun setup() {
        templateRepository = mockk<TemplateRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)
        reorderTemplatesUseCase = ReorderTemplatesUseCase(templateRepository, settingsRepository)
    }

    @Test
    fun `execute reorders templates and sets sort order`() = runTest {
        // Given
        val templates = listOf(
            PageTemplate(id = "1", name = "T1", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 0),
            PageTemplate(id = "2", name = "T2", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 1),
            PageTemplate(id = "3", name = "T3", rows = 1, columns = 1, buttonConfigs = emptyList(), orderIndex = 2)
        )

        // When
        reorderTemplatesUseCase.execute(templates, 0, 2)

        // Then
        coVerify { templateRepository.insert(match { it.id == "2" && it.orderIndex == 0 }) }
        coVerify { templateRepository.insert(match { it.id == "3" && it.orderIndex == 1 }) }
        coVerify { templateRepository.insert(match { it.id == "1" && it.orderIndex == 2 }) }
        verify { settingsRepository.templateSortOrder = SortOrder.MANUAL.name }
    }
}
