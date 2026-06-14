package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@HiltAndroidTest
class PageManagementIntegrationTest {

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

    private fun navigateToPageList() {
        // 1. Select book
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Standardbuch", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Standardbuch", substring = true).performClick()

        // 2. Go to Manage
        composeTestRule.onNodeWithTag("start_card_manage").performClick()

        // 3. Go to Page Manager
        composeTestRule.onNodeWithTag("content_manage_pages").performClick()
    }

    @Test
    fun add_new_page_and_verify_in_list() {
        navigateToPageList()

        // 1. Click Add FAB
        composeTestRule.onNodeWithTag("page_add_fab").performClick()

        // 2. Fill dialog
        // Assume OutlinedTextFields in AddPageDialog have some identifying features.
        // Let's check AddPageDialog.kt if it exists or is internal to PageListScreen.
        // Reading PageListScreen.kt earlier showed AddPageDialog is used.
        val nameLabel = composeTestRule.activity.getString(R.string.page_name_field)
        composeTestRule.onNodeWithText(nameLabel, substring = true).performTextReplacement("Testseite")
        
        // 3. Confirm
        val createText = composeTestRule.activity.getString(CoreR.string.action_create)
        composeTestRule.onNodeWithText(createText, substring = false).performClick()

        // 4. Verify we are in the Editor (editable_page_title_row should exist and contain the title)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("editable_page_title_row")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("editable_page_title_row").assertTextContains("Testseite")
    }

    @Test
    fun edit_page_name_in_editor_and_verify_persistence() {
        navigateToPageList()

        // 1. Find a page and click it (e.g. "Hauptseite" which is created by sample data)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Hauptseite", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Hauptseite", substring = true).performClick()

        // 2. Change name in editor via rename dialog
        composeTestRule.onNodeWithTag("editable_page_title_row").performClick()
        composeTestRule.onNodeWithTag("page_editor_name_field").performTextReplacement("Ueberschriebene Seite")
        
        // Confirm rename
        val saveText = composeTestRule.activity.getString(CoreR.string.action_save)
        composeTestRule.onNodeWithText(saveText).performClick()

        // 3. Go back
        val backDesc = composeTestRule.activity.getString(CoreR.string.back_button_content_description)
        composeTestRule.onNodeWithContentDescription(backDesc, substring = true).performClick()

        // 4. Verify in list
        composeTestRule.onNodeWithText("Ueberschriebene Seite").assertExists()
    }
}
