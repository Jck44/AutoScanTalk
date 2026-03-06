package com.andreas_kratzer.ghosttalk.domain.settings


import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckForPredictorUseCaseTest {

    private lateinit var featureGuard: FeatureGuard
    private lateinit var useCase: CheckForPredictorUseCase

    @Before
    fun setup() {
        featureGuard = mockk(relaxed = true)
        useCase = CheckForPredictorUseCase(featureGuard)
    }

    @Test
    fun `invoke returns true if page contains enabled smart prediction button`() {
        val smartButton = ButtonConfig(id = "smart", label = "Smart", auditoryCue = null, buttonAction = SmartPredictionButtonAction())
        val page = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(smartButton))
        every { featureGuard.isActionEnabled(smartButton.buttonAction) } returns true

        val result = useCase(page)

        assertTrue(result)
    }

    @Test
    fun `invoke returns false if page contains only regular buttons`() {
        val regularButton = ButtonConfig(id = "reg", label = "Regular", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val page = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(regularButton))

        val result = useCase(page)

        assertFalse(result)
    }

    @Test
    fun `invoke returns false if smart prediction button is disabled by feature guard`() {
        val smartButton = ButtonConfig(id = "smart", label = "Smart", auditoryCue = null, buttonAction = SmartPredictionButtonAction())
        val page = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = listOf(smartButton))
        every { featureGuard.isActionEnabled(smartButton.buttonAction) } returns false

        val result = useCase(page)

        assertFalse(result)
    }

    @Test
    fun `invoke returns false for null page`() {
        val result = useCase(null)
        assertFalse(result)
    }
}
