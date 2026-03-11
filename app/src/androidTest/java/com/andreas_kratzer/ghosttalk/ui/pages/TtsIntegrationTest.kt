package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.andreas_kratzer.ghosttalk.MainActivity
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.tts.TtsRecordingHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class TtsIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var ttsHelper: TextToSpeechHelper

    private val recordingHelper: TtsRecordingHelper
        get() = ttsHelper as TtsRecordingHelper

    @Before
    fun setup() {
        hiltRule.inject()
        settingsRepository.autoStartScanning = false
        recordingHelper.clear()
    }

    @Test
    fun buttonClick_triggersSingleTtsCall() {
        // 1. Enter User Mode
        composeTestRule.onNodeWithTag("start_card_user_mode").performClick()

        // 2. Wait for PageScreen
        composeTestRule.onNodeWithTag("page_screen_back_button").assertExists()

        // 3. Click the first button (which we tagged as 'button_idle' initially)
        // Find a button by tag 'button_idle' and click it
        composeTestRule.onAllNodesWithTag("button_idle").onFirst().performClick()

        // 4. Verify TTS recording
        val spoken = recordingHelper.spokenTexts.value
        assertEquals("Expected exactly one TTS announcement for a button click", 1, spoken.size)
    }

    @Test
    fun scanning_triggersTtsForCues() {
        // 1. Enable auto-scanning
        settingsRepository.autoStartScanning = true
        
        // 2. Enter User Mode
        composeTestRule.onNodeWithTag("start_card_user_mode").performClick()

        // 3. Wait for scanning to start and focus first button
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("button_focused").fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Verify that at least one cue was spoken during scanning start
        val spoken = recordingHelper.spokenTexts.value
        assertTrue("Expected at least one cue announcement during scanning", spoken.isNotEmpty())
    }
}
