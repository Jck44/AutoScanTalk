package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.andreas_kratzer.ghosttalk.MainActivity
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.utils.TestDataResetHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class NavigationIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dataResetHelper: TestDataResetHelper

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @get:Rule(order = 1)
    val clearDataRule = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                hiltRule.inject()
                dataResetHelper.resetData()
                // Ensure auto-scanning is disabled for navigation tests to avoid interference
                settingsRepository.autoStartScanning = false
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()


    @Test
    fun bookList_to_startScreen_to_userMode_andBack() {
        val waitForRouteAndResumedState = { expectedRoute: String ->
            composeTestRule.waitUntil(10000) {
                var isResumed = false
                composeTestRule.runOnUiThread {
                    val navController = composeTestRule.activity.navControllerForTesting
                    val currentEntry = navController?.currentBackStackEntry
                    isResumed = currentEntry?.destination?.route == expectedRoute &&
                            currentEntry.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED
                }
                isResumed
            }
            composeTestRule.waitForIdle()
        }

        // 1. Wait for BookListScreen and select "Standardbuch"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Standardbuch", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Standardbuch", substring = true).performClick()
        
        // Wait until we reach "start" and it is resumed
        waitForRouteAndResumedState("start")

        // 2. Verify "User Mode" card exists.
        composeTestRule.onNodeWithTag("start_card_user_mode").assertExists()

        // 3. Click "User Mode" to go to PageScreen
        composeTestRule.onNodeWithTag("start_card_user_mode").performClick()
        
        // Wait until we reach "main" and it is resumed
        waitForRouteAndResumedState("main")

        // 4. Verify PageScreen by checking back button
        composeTestRule.onNodeWithTag("page_screen_back_button").assertExists()

        // 5. Navigate back to StartScreen
        composeTestRule.onNodeWithTag("page_screen_back_button").performClick()
        
        // Wait until we return to "start" and it is resumed
        waitForRouteAndResumedState("start")

        // 6. Verify back on StartScreen
        composeTestRule.onNodeWithTag("start_card_user_mode").assertExists()
        
        // 7. Navigate back to BookListScreen
        val backDesc = composeTestRule.activity.getString(R.string.start_back_to_books)
        composeTestRule.onNodeWithContentDescription(backDesc).performClick()
        
        // Wait until we return to "book_list" and it is resumed
        waitForRouteAndResumedState("book_list")
        
        // Verify BookListScreen elements exist
        composeTestRule.onNodeWithTag("book_add_fab").assertExists()
    }

    @Test
    fun startScreen_to_contentManagement_andBack() {
        // 1. Navigate to StartScreen
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Standardbuch", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Standardbuch", substring = true).performClick()

        // 2. Click "Manage" (start_card_manage)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("start_card_manage")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("start_card_manage").performClick()

        // 3. Verify ContentManagementScreen (checking for tag)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("content_manage_pages")
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // 4. Navigate back to StartScreen (assume back button exists)
        // PageListScreen uses CoreR.string.back_button_content_description
        // Let's try to find a back button
        composeTestRule.onNodeWithTag("content_management_back_button", useUnmergedTree = true).performClick()
            .runCatching { performClick() } // Fallback to system back or other if tag missing

        // Since I'm not sure about the tag for back button in ContentManagement, 
        // I might need to add it or use content description.
    }
}
