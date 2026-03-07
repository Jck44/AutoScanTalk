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
        
        val frequentConfig = ButtonConfig(id = "frequent1", label = "Frequent", auditoryCue = null, buttonAction = FrequentActionButtonAction(rank = 1))
        val smart1Config = ButtonConfig(id = "smart1", label = "Smart 1", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 1))
        val smart2Config = ButtonConfig(id = "smart2", label = "Smart 2", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 2))
        val normalConfig = ButtonConfig(id = "normal1", label = "Normal", auditoryCue = null, buttonAction = SpeakTextButtonAction())

        val initialPage = Page(
            id = "page1",
            bookId = bookId,
            name = "Page 1",
            buttonConfigs = listOf(frequentConfig, smart1Config, smart2Config, normalConfig)
        )

        val resolvedFrequentConfig = ButtonConfig(id = "frequent1", label = "Resolved Frequent", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val frequentlyResolvedPage = initialPage.copy(
            buttonConfigs = listOf(
                resolvedFrequentConfig,
                smart1Config,
                smart2Config,
                normalConfig
            )
        )

        val targetPage = Page(id = "page2", bookId = bookId, name = "Target Page", buttonConfigs = emptyList())
        val otherPage = Page(id = "page3", bookId = bookId, name = "Page 3", buttonConfigs = listOf(
            ButtonConfig(id = "button2", label = "Button 2", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        ))

        coEvery { frequentActionResolver.resolve(initialPage, bookId) } returns frequentlyResolvedPage
        coEvery { pageRepository.getPageById("page2") } returns targetPage
        coEvery { pageRepository.getPageById("button2") } returns null
        coEvery { pageRepository.getAllPages() } returns listOf(initialPage, otherPage)

        // When
        val result = resolveDynamicButtonsUseCase.execute(initialPage, bookId, predictions)

        // Then
        assertEquals("Resolved Frequent", result.buttonConfigs[0]?.label)
        // button2 found in Page 3
        assertEquals("Button 2", result.buttonConfigs[1]?.label) 
        assertEquals("button2", result.buttonConfigs[1]?.id)
        
        assertEquals("Target Page", result.buttonConfigs[2]?.label)
        assertEquals("page2", result.buttonConfigs[2]?.id)
        assertTrue(result.buttonConfigs[2]?.buttonAction is NavigateToPageButtonAction)
        assertEquals("Normal", result.buttonConfigs[3]?.label)
    }

    @Test
    fun `execute handles recursion guard`() = runTest {
        // Given
        val bookId = "book1"
        val predictions = listOf("smart1") // Points to itself
        
        val smart1Config = ButtonConfig(id = "smart1", label = "Smart 1", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 1))
        val page = Page(
            id = "page1",
            bookId = bookId,
            name = "Page 1",
            buttonConfigs = listOf(smart1Config)
        )

        coEvery { frequentActionResolver.resolve(page, bookId) } returns page

        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, predictions)

        // Then
        // Recursion guard should return null (hiding the button) to unblock the scanner
        assertNull(result.buttonConfigs[0])
    }

    @Test
    fun `execute preserves placeholders when predictions are null`() = runTest {
        // Given
        val bookId = "book1"
        val predictions: List<String>? = null
        
        val smartConfig = ButtonConfig(id = "smart", label = "Smart Placeholder", auditoryCue = null, buttonAction = SmartPredictionButtonAction(rank = 1))
        val page = Page(id = "page1", bookId = bookId, name = "Page 1", buttonConfigs = listOf(smartConfig))

        coEvery { frequentActionResolver.resolve(page, bookId) } returns page

        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, predictions)

        // Then
        // Should keep the placeholder while waiting
        assertEquals("Smart Placeholder", result.buttonConfigs[0]?.label)
        assertTrue(result.buttonConfigs[0]?.buttonAction is SmartPredictionButtonAction)
    }
}
