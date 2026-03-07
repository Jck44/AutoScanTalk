package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.NotificationButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeatureGuardTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var featureGuard: FeatureGuard

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        featureGuard = FeatureGuard(settingsRepository)
    }

    @Test
    fun `isActionEnabled returns true for standard actions`() {
        assertTrue(featureGuard.isActionEnabled(SpeakTextButtonAction()))
        assertTrue(featureGuard.isActionEnabled(NavigateToPageButtonAction("p1")))
    }

    @Test
    fun `isActionEnabled respects smart prediction setting`() {
        val action = SmartPredictionButtonAction(1)
        
        every { settingsRepository.isSmartPredictionEnabled } returns true
        assertTrue(featureGuard.isActionEnabled(action))

        every { settingsRepository.isSmartPredictionEnabled } returns false
        assertFalse(featureGuard.isActionEnabled(action))
    }

    @Test
    fun `isActionEnabled respects gemini setting`() {
        val action = GeminiButtonAction(prompt = "Help")
        val searchAction = GeminiSearchButtonAction(prompt = "Search")

        every { settingsRepository.isGeminiEnabled } returns true
        assertTrue(featureGuard.isActionEnabled(action))
        assertTrue(featureGuard.isActionEnabled(searchAction))

        every { settingsRepository.isGeminiEnabled } returns false
        assertFalse(featureGuard.isActionEnabled(action))
        assertFalse(featureGuard.isActionEnabled(searchAction))
    }

    @Test
    fun `isActionEnabled respects gemini nano setting`() {
        val action = GeminiNanoButtonAction(prompt = "Local")

        every { settingsRepository.useLocalGenerativeAi } returns true
        assertTrue(featureGuard.isActionEnabled(action))

        every { settingsRepository.useLocalGenerativeAi } returns false
        assertFalse(featureGuard.isActionEnabled(action))
    }

    @Test
    fun `isActionEnabled respects notification reading setting`() {
        val action = NotificationButtonAction()

        every { settingsRepository.isNotificationReadingEnabled } returns true
        assertTrue(featureGuard.isActionEnabled(action))

        every { settingsRepository.isNotificationReadingEnabled } returns false
        assertFalse(featureGuard.isActionEnabled(action))
    }

    @Test
    fun `isButtonVisible delegates to isActionEnabled`() {
        val action = SmartPredictionButtonAction(1)
        val config = ButtonConfig(id = "b1", label = "Smart", buttonAction = action, auditoryCue = null)

        every { settingsRepository.isSmartPredictionEnabled } returns true
        assertTrue(featureGuard.isButtonVisible(config))

        every { settingsRepository.isSmartPredictionEnabled } returns false
        assertFalse(featureGuard.isButtonVisible(config))
    }
}
