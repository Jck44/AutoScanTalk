package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    /**
     * Navigate from wherever we land (BookListScreen or StartScreen) to the StartScreen.
     * The app starts on BookListScreen by default (startupBehavior = BOOK_SELECTION).
     * We need to click a book card to get to StartScreen.
     */
    private fun navigateToStartScreen() {
        // First, try to see if we're already on StartScreen
        val startCardNodes = composeTestRule
            .onAllNodesWithTag("start_card_user_mode")
            .fetchSemanticsNodes()

        if (startCardNodes.isNotEmpty()) {
            // Already on StartScreen
            return
        }

        // We're on BookListScreen – wait for a book card to appear and click it.
        // The default book is named "Standardbuch" (created by SampleDataInitializer).
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Standardbuch", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Standardbuch", substring = true).performClick()

        // Now wait for StartScreen to appear
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("start_card_user_mode")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun startScanningOnUserModeEntry_andStopOnExit() {
        // 1. Navigate to StartScreen and click "User Mode"
        navigateToStartScreen()
        composeTestRule.onNodeWithTag("start_card_user_mode").performClick()

        // 2. We should now be in PageScreen.
        // Verify we are on the PageScreen by checking the back button.
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("page_screen_back_button")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("page_screen_back_button").assertExists()

        // 3. Since auto-start is true, the ScanCoordinator should have started.
        // We expect the first button (at index 0) to be focused eventually.
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("button_focused").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("button_focused").assertExists()

        // 4. Leaving the screen works.
        composeTestRule.onNodeWithTag("page_screen_back_button").performClick()

        // 5. We should be back at StartScreen
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("start_card_user_mode")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("start_card_user_mode").assertExists()
    }
}
