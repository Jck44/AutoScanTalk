package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifyActivePageLinksUseCaseTest {

    private val useCase = IdentifyActivePageLinksUseCase()

    @Test
    fun `execute identifies active navigation links on pages`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(isActive = true, buttonAction = NavigateToPageButtonAction("target1")),
                    ButtonConfig(isActive = false, buttonAction = NavigateToPageButtonAction("target2")),
                    ButtonConfig(isActive = true, buttonAction = SpeakTextButtonAction())
                )
            ),
            Page(
                id = "page2",
                bookId = "book1",
                name = "Page 2",
                buttonConfigs = listOf(
                    ButtonConfig(isActive = true, buttonAction = NavigateToPageButtonAction("target3"))
                )
            )
        )

        val result = useCase.execute(pages, emptyList())

        assertEquals(2, result.size)
        assertTrue(result.contains("target1"))
        assertTrue(result.contains("target3"))
    }

    @Test
    fun `execute identifies active navigation links on templates`() {
        val templates = listOf(
            PageTemplate(
                id = "tmpl1",
                name = "Template 1",
                buttonConfigs = listOf(
                    ButtonConfig(isActive = true, buttonAction = NavigateToPageButtonAction("target-tmpl"))
                )
            )
        )

        val result = useCase.execute(emptyList(), templates)

        assertEquals(1, result.size)
        assertTrue(result.contains("target-tmpl"))
    }

    @Test
    fun `execute returns empty set when no active links exist`() {
        val pages = listOf(
            Page(
                id = "page1",
                bookId = "book1",
                name = "Page 1",
                buttonConfigs = listOf(
                    ButtonConfig(isActive = false, buttonAction = NavigateToPageButtonAction("target1")),
                    ButtonConfig(isActive = true, buttonAction = SpeakTextButtonAction())
                )
            )
        )

        val result = useCase.execute(pages, emptyList())

        assertTrue(result.isEmpty())
    }
}
