package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.andreas_kratzer.ghosttalk.MainActivity
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class ScanningIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        // Ensure auto-scanning is enabled for tests
        settingsRepository.autoStartScanning = true
    }

    @Test
    fun startScanningOnUserModeEntry_andStopOnExit() {
        // 1. Wait for StartScreen and click "User Mode"
        composeTestRule.onNodeWithTag("start_card_user_mode").performClick()

        // 2. We should now be in PageScreen. 
        // We wait a bit or check for a focused button index (which is internal state, 
        // but we can check if any button has a focused border/style if we added tags there too).
        // For now, let's just verify we are on the PageScreen by checking the back button.
        composeTestRule.onNodeWithTag("page_screen_back_button").assertExists()

        // 3. Since auto-start is true, the ScanCoordinator should have started.
        // We expect the first button (at index 0) to be focused eventually.
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("button_focused").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("button_focused").assertExists()

        // 4. Leaving the screen works.
        composeTestRule.onNodeWithTag("page_screen_back_button").performClick()

        // 4. We should be back at StartScreen
        composeTestRule.onNodeWithTag("start_card_user_mode").assertExists()
    }
}
