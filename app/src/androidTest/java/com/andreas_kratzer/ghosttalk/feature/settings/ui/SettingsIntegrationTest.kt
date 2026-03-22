package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.andreas_kratzer.ghosttalk.MainActivity
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.utils.TestDataResetHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@HiltAndroidTest
class SettingsIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dataResetHelper: TestDataResetHelper

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        dataResetHelper.resetData()
        settingsRepository.autoStartScanning = false
    }

    @Test
    fun open_global_settings_and_navigate_sections() {
        // 1. Click Global Settings icon on BookListScreen
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("book_list_settings_button")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("book_list_settings_button").performClick()

        // 2. Verify we are in Global Settings
        val globalSettingsTitle = composeTestRule.activity.getString(CoreR.string.settings_title_global)
        composeTestRule.onNodeWithText(globalSettingsTitle, substring = true).assertExists()

        // 3. Click Security section
        composeTestRule.onNodeWithTag("settings_section_SECURITY").performClick()

        // 4. Verify we are in Security section
        val securityTitle = composeTestRule.activity.getString(SettingsR.string.settings_category_security)
        // Use useUnmergedTree = true or check for a specific tag if possible, 
        // but here we just ensure at least one exists or use onFirst()
        composeTestRule.onAllNodesWithText(securityTitle, substring = true).onFirst().assertExists()

        // 5. Navigate back to Main Settings Menu
        composeTestRule.onNodeWithTag("settings_back_button").performClick()

        // 6. Navigate back to Book List
        composeTestRule.onNodeWithTag("settings_back_button").performClick()

        // 7. Verify back on Book List
        composeTestRule.onNodeWithTag("book_list_settings_button").assertExists()
    }

    @Test
    fun open_book_settings_and_toggle_scanning() {
        // 1. Select book
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Standardbuch", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Standardbuch", substring = true).performClick()

        // 2. Click Settings card on StartScreen
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("start_card_settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("start_card_settings").performClick()

        // 3. Click Scanning section
        composeTestRule.onNodeWithTag("settings_section_SCANNING").performClick()

        // 4. Toggle scanning
        val switchText = composeTestRule.activity.getString(SettingsR.string.settings_auto_scan)
        composeTestRule.onNodeWithText(switchText, substring = true).assertExists()
        
        val initialState = settingsRepository.autoStartScanning
        composeTestRule.onAllNodesWithText(switchText, substring = true).onFirst().performClick()
        
        // 5. Verify repository updated (wait for potentially async update)
        composeTestRule.waitUntil(5000) {
            settingsRepository.autoStartScanning != initialState
        }
        assert(settingsRepository.autoStartScanning != initialState)
    }
}
