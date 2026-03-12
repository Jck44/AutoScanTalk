package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.core.database.TemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTemplateUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var createTemplateUseCase: CreateTemplateUseCase

    @Before
    fun setup() {
        templateRepository = mockk(relaxed = true)
        createTemplateUseCase = CreateTemplateUseCase(templateRepository)
    }

    @Test
    fun `execute creates a new template with correct properties`() = runTest {
        // Given
        coEvery { templateRepository.getAllTemplates() } returns flowOf(emptyList())

        // When
        createTemplateUseCase.execute("Test Template", 3, 3, null)

        // Then
        coVerify {
            templateRepository.insert(match { 
                it.name == "Test Template" &&
                it.rows == 3 &&
                it.columns == 3 &&
                it.buttonConfigs.size == 49 &&
                it.orderIndex == 0
            })
        }
    }
}
