package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelSuggestionTest {

    @Test
    fun testGetLocalLabelSuggestionSpeak() {
        val context = mockk<Context>()
        val pages = emptyList<Page>()

        // 3 words or less
        val configShort = ButtonConfig(
            id = "b1",
            label = "",
            spokenText = "Hello world now",
            spokenTextMode = SpokenTextMode.TTS,
            buttonAction = SpeakTextButtonAction()
        )
        assertEquals("Hello world now", getLocalLabelSuggestion(configShort, pages, context))

        // More than 3 words
        val configLong = ButtonConfig(
            id = "b2",
            label = "",
            spokenText = "Hello world now this is long",
            spokenTextMode = SpokenTextMode.TTS,
            buttonAction = SpeakTextButtonAction()
        )
        assertEquals("Hello world now…", getLocalLabelSuggestion(configLong, pages, context))
    }

    @Test
    fun testGetLocalLabelSuggestionNavigate() {
        val context = mockk<Context>()
        val pages = listOf(
            Page(id = "page1", bookId = "book1", name = "TestPage", columns = 3, rows = 3)
        )
        every { context.getString(R.string.suggest_label_navigate, "TestPage") } returns "Gehe zu TestPage"

        val config = ButtonConfig(
            id = "b3",
            label = "",
            buttonAction = NavigateToPageButtonAction("page1")
        )
        assertEquals("Gehe zu TestPage", getLocalLabelSuggestion(config, pages, context))
    }
}
