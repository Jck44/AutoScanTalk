package com.andreas_kratzer.ghosttalk.domain.templates


import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DeleteTemplateUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var deleteTemplateUseCase: DeleteTemplateUseCase

    @Before
    fun setup() {
        templateRepository = mockk<TemplateRepository>(relaxed = true)
        deleteTemplateUseCase = DeleteTemplateUseCase(templateRepository)
    }

    @Test
    fun `execute calls repository delete`() = runTest {
        // Given
        val template = PageTemplate(
            id = "id",
            name = "Name",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList()
        )

        // When
        deleteTemplateUseCase.execute(template)

        // Then
        coVerify { templateRepository.delete(template) }
    }
}
