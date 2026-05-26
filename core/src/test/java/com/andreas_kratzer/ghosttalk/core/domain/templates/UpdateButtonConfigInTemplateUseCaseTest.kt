package com.andreas_kratzer.ghosttalk.core.domain.templates

import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateButtonConfigInTemplateUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase

    @Before
    fun setup() {
        templateRepository = mockk<TemplateRepository>(relaxed = true)
        updateButtonConfigInTemplateUseCase = UpdateButtonConfigInTemplateUseCase(templateRepository)
    }

    @Test
    fun `execute updates button config in template`() = runTest {
        // Given
        val initialConfigs = listOf(
            null, 
            ButtonConfig(id = "btn1", label = "Old", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        )
        val template = PageTemplate(
            id = "id",
            name = "Name",
            rows = 1,
            columns = 2,
            buttonConfigs = initialConfigs
        )
        val newConfig = ButtonConfig(id = "btn2", label = "New", auditoryCue = null, buttonAction = SpeakTextButtonAction())

        // When
        updateButtonConfigInTemplateUseCase.execute(template, 0, newConfig)

        // Then
        coVerify {
            templateRepository.insert(match {
                it.id == "id" &&
                it.buttonConfigs.size == 2 &&
                it.buttonConfigs[0]?.label == "New" &&
                it.buttonConfigs[1]?.label == "Old"
            })
        }
    }
}
