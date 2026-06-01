package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
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
    fun `isActionEnabled always returns false for gemini nano setting`() {
        val action = GeminiNanoButtonAction(intent = "time")
        assertFalse(featureGuard.isActionEnabled(action))
    }

    @Test
    fun `isActionEnabled returns true for notification reading ControlDeviceButtonAction`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)
        assertTrue(featureGuard.isActionEnabled(action))
    }

    @Test
    fun `isActionEnabled returns true for media control actions`() {
        val action = ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT)
        assertTrue(featureGuard.isActionEnabled(action))
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
