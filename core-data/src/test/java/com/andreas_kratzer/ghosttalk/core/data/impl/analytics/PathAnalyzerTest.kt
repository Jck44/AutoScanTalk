package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathAnalyzerTest {

    private val pathAnalyzer = PathAnalyzer()

    @Test
    fun testSessionGroupingAndShortcutMining() {
        val legoButtons = MutableList<ButtonConfig?>(49) { null }
        legoButtons[0] = ButtonConfig(id = "lego_btn_1", label = "Bauen", isActive = true)
        legoButtons[1] = ButtonConfig(id = "lego_btn_2", label = "Stein", isActive = true)

        val pageLego = Page(
            id = "page_lego",
            bookId = "book-default",
            name = "Lego-Seite",
            rows = 2,
            columns = 2,
            buttonConfigs = legoButtons
        )

        val drinksButtons = MutableList<ButtonConfig?>(49) { null }
        drinksButtons[0] = ButtonConfig(id = "drinks_btn_1", label = "Apfelsaft", isActive = true)
        drinksButtons[1] = ButtonConfig(id = "drinks_btn_2", label = "Wasser", isActive = true)

        val pageDrinks = Page(
            id = "page_drinks",
            bookId = "book-default",
            name = "Getränke",
            rows = 2,
            columns = 2,
            buttonConfigs = drinksButtons
        )

        val pages = listOf(pageLego, pageDrinks)

        val baseTime = 1000000000000L
        val scanDelayMs = 2000L // 2.0s scan delay

        // Create chronological chronological usage events:
        // Transition 1: Lego -> Drinks (Durst) within threshold
        val events = listOf(
            // Session 1: Lego build
            ButtonUsageEvent(baseTime, "Bauen", "SpeakText", buttonId = "lego_btn_1", pageId = "page_lego"),
            ButtonUsageEvent(baseTime + 5000, "Stein", "SpeakText", buttonId = "lego_btn_2", pageId = "page_lego"),
            // Navigates to drinks page and clicks Apfelsaft quickly (within scan limit: 10 * 2s + 25s = 45s)
            ButtonUsageEvent(baseTime + 18000, "Apfelsaft", "SpeakText", buttonId = "drinks_btn_1", pageId = "page_drinks"),

            // --- Break of 10 minutes (New Session) ---
            
            // Session 2: Lego build again
            ButtonUsageEvent(baseTime + 10 * 60 * 1000, "Bauen", "SpeakText", buttonId = "lego_btn_1", pageId = "page_lego"),
            // Navigates and clicks Apfelsaft quickly again
            ButtonUsageEvent(baseTime + 10 * 60 * 1000 + 12000, "Apfelsaft", "SpeakText", buttonId = "drinks_btn_1", pageId = "page_drinks")
        )

        val recommendations = pathAnalyzer.analyzePaths(events, pages, scanDelayMs)

        // Verifications
        assertEquals(1, recommendations.size)
        val rec = recommendations.first()
        assertEquals("page_lego", rec.sourcePageId)
        assertEquals("Lego-Seite", rec.sourcePageName)
        assertEquals("drinks_btn_1", rec.targetButtonConfig.id)
        assertEquals("Apfelsaft", rec.targetButtonConfig.label)
        assertEquals(2, rec.occurrenceCount) // Occurred twice across the two sessions
        
        // Time saved = max(5, ((6 * 2000) + 3000)/1000) = max(5, 15) = 15s
        assertEquals(15, rec.estimatedTimeSavedSec)
    }

    @Test
    fun testDoNotRecommendIfAlreadyExists() {
        val legoButtons = MutableList<ButtonConfig?>(49) { null }
        legoButtons[0] = ButtonConfig(id = "lego_btn_1", label = "Bauen", isActive = true)
        legoButtons[1] = ButtonConfig(id = "drinks_btn_1", label = "Apfelsaft", isActive = true) // Already present!

        val pageLego = Page(
            id = "page_lego",
            bookId = "book-default",
            name = "Lego-Seite",
            rows = 2,
            columns = 2,
            buttonConfigs = legoButtons
        )

        val drinksButtons = MutableList<ButtonConfig?>(49) { null }
        drinksButtons[0] = ButtonConfig(id = "drinks_btn_1", label = "Apfelsaft", isActive = true)

        val pageDrinks = Page(
            id = "page_drinks",
            bookId = "book-default",
            name = "Getränke",
            rows = 2,
            columns = 2,
            buttonConfigs = drinksButtons
        )

        val pages = listOf(pageLego, pageDrinks)
        val baseTime = 1000000000000L
        val scanDelayMs = 1000L

        val events = listOf(
            ButtonUsageEvent(baseTime, "Bauen", "SpeakText", buttonId = "lego_btn_1", pageId = "page_lego"),
            ButtonUsageEvent(baseTime + 4000, "Apfelsaft", "SpeakText", buttonId = "drinks_btn_1", pageId = "page_drinks"),
            
            // Session 2
            ButtonUsageEvent(baseTime + 10 * 60 * 1000, "Bauen", "SpeakText", buttonId = "lego_btn_1", pageId = "page_lego"),
            ButtonUsageEvent(baseTime + 10 * 60 * 1000 + 4000, "Apfelsaft", "SpeakText", buttonId = "drinks_btn_1", pageId = "page_drinks")
        )

        val recommendations = pathAnalyzer.analyzePaths(events, pages, scanDelayMs)
        assertTrue(recommendations.isEmpty()) // Should be empty because Apfelsaft already exists on Lego page!
    }
}
