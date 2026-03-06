package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveDynamicButtonsUseCaseTest {

    private lateinit var frequentActionResolver: FrequentActionResolver
    private lateinit var pageRepository: PageRepository
    private lateinit var resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase

    @Before
    fun setup() {
        frequentActionResolver = mockk()
        pageRepository = mockk()
        resolveDynamicButtonsUseCase = ResolveDynamicButtonsUseCase(frequentActionResolver, pageRepository)
    }

    @Test
    fun `execute resolves frequent actions and smart predictions`() = runTest {
        // Given
        val bookId = "book1"
        val predictions = listOf("button2", "page2")
        
        val initialPage = Page(
            id = "page1",
            bookId = bookId,
            name = "Page 1",
            buttonConfigs = listOf(
                ButtonConfig(id = "frequent1", label = "Frequent", auditoryCue = null, buttonAction = FrequentActionButtonAction(rank = 1)),
                ButtonConfig(id = "smart1", label = "Smart 1", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 1)),
                ButtonConfig(id = "smart2", label = "Smart 2", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 2)),
                ButtonConfig(id = "normal1", label = "Normal", auditoryCue = null, buttonAction = SpeakTextButtonAction())
            )
        )

        val resolvedFrequentConfig = ButtonConfig(id = "frequent1", label = "Resolved Frequent", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val frequentlyResolvedPage = initialPage.copy(
            buttonConfigs = listOf(
                resolvedFrequentConfig,
                initialPage.buttonConfigs[1],
                initialPage.buttonConfigs[2],
                initialPage.buttonConfigs[3]
            )
        )

        val targetPage = Page(id = "page2", bookId = bookId, name = "Target Page", buttonConfigs = emptyList())

        coEvery { frequentActionResolver.resolve(initialPage, bookId) } returns frequentlyResolvedPage
        coEvery { pageRepository.getPageById("page2") } returns targetPage
        coEvery { pageRepository.getPageById("button2") } returns null

        // When
        val result = resolveDynamicButtonsUseCase.execute(initialPage, bookId, predictions)

        // Then
        assertEquals("Resolved Frequent", result.buttonConfigs[0]?.label)
        // button2 not found in page configs, and pageRepository search for button2 (not found) -> null
        assertNull(result.buttonConfigs[1]) 
        
        assertEquals("Target Page", result.buttonConfigs[2]?.label)
        assertTrue(result.buttonConfigs[2]?.buttonAction is NavigateToPageButtonAction)
        assertEquals("Normal", result.buttonConfigs[3]?.label)
    }

    @Test
    fun `execute handles recursion guard`() = runTest {
        // Given
        val bookId = "book1"
        val predictions = listOf("smart1") // Points to itself
        
        val page = Page(
            id = "page1",
            bookId = bookId,
            name = "Page 1",
            buttonConfigs = listOf(
                ButtonConfig(id = "smart1", label = "Smart 1", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 1))
            )
        )

        coEvery { frequentActionResolver.resolve(page, bookId) } returns page

        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, predictions)

        // Then
        assertNull(result.buttonConfigs[0]) // Should be null due to recursion guard
    }
}
