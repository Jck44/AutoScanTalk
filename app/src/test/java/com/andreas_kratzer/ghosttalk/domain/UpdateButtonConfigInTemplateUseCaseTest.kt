package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.domain.templates.UpdateButtonConfigInTemplateUseCase

import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateButtonConfigInTemplateUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase

    @Before
    fun setup() {
        templateRepository = mockk(relaxed = true)
        updateButtonConfigInTemplateUseCase = UpdateButtonConfigInTemplateUseCase(templateRepository)
    }

    @Test
    fun `execute updates button config in template`() = runTest {
        // Given
        val initialConfigs = listOf(
            null, 
            ButtonConfig(id = "btn1", label = "Old", auditoryCue = null, buttonAction = SpeakTextButtonAction(""))
        )
        val template = PageTemplate("id", "Name", 1, 2, initialConfigs)
        val newConfig = ButtonConfig(id = "btn2", label = "New", auditoryCue = null, buttonAction = SpeakTextButtonAction("New"))

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
