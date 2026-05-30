package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider
import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
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
    private lateinit var actionLogProvider: ActionLogProvider
    private lateinit var application: android.app.Application
    private lateinit var resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase

    @Before
    fun setup() {
        frequentActionResolver = mockk()
        actionLogProvider = mockk()
        application = mockk(relaxed = true)
        
        io.mockk.every { application.getString(any<Int>(), *anyVararg()) } answers {
            val resId = firstArg<Int>()
            val args = secondArg<Array<*>>()
            when (resId) {
                com.andreas_kratzer.ghosttalk.R.string.audio_cue_last_action_prefix -> "Letzte Aktion: ${args.getOrNull(0)}"
                com.andreas_kratzer.ghosttalk.R.string.audio_cue_previous_action_prefix_format -> "${args.getOrNull(0)}. Letzte Aktion: ${args.getOrNull(1)}"
                com.andreas_kratzer.ghosttalk.R.string.audio_cue_frequent_action_prefix -> "Häufigste Aktion: ${args.getOrNull(0)}"
                com.andreas_kratzer.ghosttalk.R.string.audio_cue_frequent_action_prefix_format -> "${args.getOrNull(0)}. Häufigste Aktion: ${args.getOrNull(1)}"
                else -> "mock_string"
            }
        }
        
        resolveDynamicButtonsUseCase = ResolveDynamicButtonsUseCase(frequentActionResolver, actionLogProvider, application)
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

        val allPages = listOf(initialPage, otherPage, targetPage)

        coEvery { frequentActionResolver.resolve(initialPage, bookId) } returns frequentlyResolvedPage

        // When
        val result = resolveDynamicButtonsUseCase.execute(initialPage, bookId, predictions, allPages)

        // Then
        assertEquals("Resolved Frequent", result.buttonConfigs[0]?.label)
        assertEquals("Häufigste Aktion: Resolved Frequent", (result.buttonConfigs[0]?.auditoryCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text)
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
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, predictions, listOf(page))

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
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, predictions, listOf(page))

        // Then
        // Should keep the placeholder while waiting
        assertEquals("Smart Placeholder", result.buttonConfigs[0]?.label)
        assertTrue(result.buttonConfigs[0]?.buttonAction is SmartPredictionButtonAction)
    }

    @Test
    fun `execute resolves previous action from history`() = runTest {
        // Given
        val bookId = "book1"
        val previousAction = SpeakTextButtonAction()
        val history = listOf(
            ActionLogEntry("Last", System.currentTimeMillis(), previousAction),
            ActionLogEntry("Older", System.currentTimeMillis() - 1000, NavigateToPageButtonAction("other"))
        )
        
        val buttonConfig = ButtonConfig(
            id = "prev1",
            label = "Wiederholen",
            auditoryCue = null,
            buttonAction = PreviousActionButtonAction(rank = 1)
        )
        val page = Page(id = "page1", bookId = bookId, name = "Page 1", buttonConfigs = listOf(buttonConfig))

        coEvery { frequentActionResolver.resolve(any(), any()) } returns page
        coEvery { actionLogProvider.loadSavedLogEntries() } returns history

        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, emptyList(), listOf(page))

        // Then
        val resolvedAction = result.buttonConfigs[0]?.buttonAction
        assertTrue(resolvedAction is SpeakTextButtonAction)
        assertEquals("Last", result.buttonConfigs[0]?.label)
        assertEquals("Letzte Aktion: Last", (result.buttonConfigs[0]?.auditoryCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text)
    }

    @Test
    fun `execute resolves and keeps name of previous action for non-speak actions`() = runTest {
        // Given
        val bookId = "book1"
        val previousAction = NavigateToPageButtonAction("targetPage")
        val history = listOf(
            ActionLogEntry("Go Home", System.currentTimeMillis(), previousAction)
        )

        val buttonConfig = ButtonConfig(
            id = "prev1",
            label = "Wiederholen",
            auditoryCue = null,
            buttonAction = PreviousActionButtonAction(rank = 1)
        )
        val page = Page(id = "page1", bookId = bookId, name = "Page 1", buttonConfigs = listOf(buttonConfig))

        coEvery { frequentActionResolver.resolve(any(), any()) } returns page
        coEvery { actionLogProvider.loadSavedLogEntries() } returns history

        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, emptyList(), listOf(page))

        // Then
        val resolvedAction = result.buttonConfigs[0]?.buttonAction
        assertTrue(resolvedAction is NavigateToPageButtonAction)
        assertEquals("Go Home", result.buttonConfigs[0]?.label)
    }

    @Test
    fun `execute resolves frequent action rank 2 and formats prefix`() = runTest {
        // Given
        val bookId = "book1"
        val frequentConfig = ButtonConfig(id = "frequent2", label = "Frequent 2", auditoryCue = null, buttonAction = FrequentActionButtonAction(rank = 2))
        val initialPage = Page(id = "page1", bookId = bookId, name = "Page 1", buttonConfigs = listOf(frequentConfig))
        
        val resolvedConfig = ButtonConfig(id = "frequent2", label = "Resolved Frequent 2", auditoryCue = com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue("Custom Cue"), buttonAction = SpeakTextButtonAction())
        val frequentlyResolvedPage = initialPage.copy(buttonConfigs = listOf(resolvedConfig))
        
        coEvery { frequentActionResolver.resolve(initialPage, bookId) } returns frequentlyResolvedPage
        
        // When
        val result = resolveDynamicButtonsUseCase.execute(initialPage, bookId, emptyList(), listOf(initialPage))
        
        // Then
        assertEquals("Resolved Frequent 2", result.buttonConfigs[0]?.label)
        assertEquals("2. Häufigste Aktion: Custom Cue", (result.buttonConfigs[0]?.auditoryCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text)
    }

    @Test
    fun `execute resolves previous action rank 2 and formats prefix`() = runTest {
        // Given
        val bookId = "book1"
        val previousAction = SpeakTextButtonAction()
        val history = listOf(
            ActionLogEntry("Last", System.currentTimeMillis(), previousAction),
            ActionLogEntry("Older", System.currentTimeMillis() - 1000, previousAction)
        )
        
        val buttonConfig = ButtonConfig(
            id = "prev2",
            label = "Wiederholen 2",
            auditoryCue = null,
            buttonAction = PreviousActionButtonAction(rank = 2)
        )
        val page = Page(id = "page1", bookId = bookId, name = "Page 1", buttonConfigs = listOf(buttonConfig))
        
        coEvery { frequentActionResolver.resolve(any(), any()) } returns page
        coEvery { actionLogProvider.loadSavedLogEntries() } returns history
        
        // When
        val result = resolveDynamicButtonsUseCase.execute(page, bookId, emptyList(), listOf(page))
        
        // Then
        val resolvedAction = result.buttonConfigs[0]?.buttonAction
        assertTrue(resolvedAction is SpeakTextButtonAction)
        assertEquals("Older", result.buttonConfigs[0]?.label)
        assertEquals("2. Letzte Aktion: Older", (result.buttonConfigs[0]?.auditoryCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text)
    }
}
