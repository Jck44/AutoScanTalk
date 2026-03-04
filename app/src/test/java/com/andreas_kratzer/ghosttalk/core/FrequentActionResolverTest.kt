package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrequentActionResolverTest {

    private val buttonUsageRepository = mockk<ButtonUsageRepository>()
    private val resolver = FrequentActionResolver(buttonUsageRepository)

    @Test
    fun `resolve returns original page if no FrequentActionButtons`() = runTest {
        val originalPage = Page(
            id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
            buttonConfigs = listOf(
                ButtonConfig(label = "Static", buttonAction = SpeakTextButtonAction("Static"), auditoryCue = null, isActive = true)
            )
        )

        val resolvedPage = resolver.resolve(originalPage, "b1")

        assertEquals(originalPage, resolvedPage)
    }

    @Test
    fun `resolve populates FrequentActionButtons correctly`() = runTest {
        val originalPage = Page(
            id = "p1", bookId = "b1", name = "Test",
            buttonConfigs = listOf(
                ButtonConfig(id = "slot1", label = "Top 1", buttonAction = FrequentActionButtonAction(1), auditoryCue = null),
                ButtonConfig(id = "slot2", label = "Top 2", buttonAction = FrequentActionButtonAction(2), auditoryCue = null)
            )
        )

        val stats = listOf(
            ButtonUsageStat("b1", "orig1", "Yes", """{"type":"SpeakTextButtonAction","data":{"textToSpeech":"Yes"}}""", 10),
            ButtonUsageStat("b1", "orig2", "No", """{"type":"SpeakTextButtonAction","data":{"textToSpeech":"No"}}""", 5)
        )
        coEvery { buttonUsageRepository.getTopActions("b1", 2) } returns stats

        val resolvedPage = resolver.resolve(originalPage, "b1")

        val btn1 = resolvedPage.buttonConfigs[0]!!
        val btn2 = resolvedPage.buttonConfigs[1]!!

        assertEquals("Yes", btn1.label)
        assertEquals("Yes", (btn1.buttonAction as SpeakTextButtonAction).textToSpeech)
        assertEquals("slot1", btn1.id) // ID should remain the same

        assertEquals("No", btn2.label)
        assertEquals("No", (btn2.buttonAction as SpeakTextButtonAction).textToSpeech)
        assertEquals("slot2", btn2.id)
    }

    @Test
    fun `resolve sets button to null if not enough stats`() = runTest {
        val originalPage = Page(
            id = "p1", bookId = "b1", name = "Test",
            buttonConfigs = listOf(
                ButtonConfig(id = "slot1", label = "Top 1", buttonAction = FrequentActionButtonAction(1), auditoryCue = null),
                ButtonConfig(id = "slot2", label = "Top 2", buttonAction = FrequentActionButtonAction(2), auditoryCue = null)
            )
        )

        val stats = listOf(
            ButtonUsageStat("b1", "orig1", "Yes", """{"type":"SpeakTextButtonAction","data":{"textToSpeech":"Yes"}}""", 10)
        )
        // Only 1 stat available
        coEvery { buttonUsageRepository.getTopActions("b1", 2) } returns stats

        val resolvedPage = resolver.resolve(originalPage, "b1")

        assertNotNull(resolvedPage.buttonConfigs[0])
        assertNull(resolvedPage.buttonConfigs[1]) // Should be null because no 2nd stat
    }
}
