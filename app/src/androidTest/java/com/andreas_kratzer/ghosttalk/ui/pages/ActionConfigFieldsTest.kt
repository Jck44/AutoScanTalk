package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import org.junit.Rule
import org.junit.Test

class ActionConfigFieldsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun verify_navigation_fields_shown_when_selected() {
        val label = context.getString(R.string.button_action_navigate_page)
        val targetLabel = context.getString(R.string.button_target_page_label)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDimensions provides Dimensions()) {
                ActionConfigFields(
                    selectedActionType = label,
                    pages = emptyList(),
                    templates = emptyList(),
                    targetPageId = "",
                    onTargetPageIdChange = {},
                    geminiPrompt = "",
                    onGeminiPromptChange = {},
                    rank = 1,
                    onRankChange = {},
                    deviceActionType = DeviceActionType.READ_BATTERY,
                    onDeviceActionTypeChange = {},
                    volumeValue = "",
                    onVolumeValueChange = {},
                    contactName = "",
                    onContactNameChange = {},
                    contactPhone = "",
                    onContactPhoneChange = {},
                    messageText = "",
                    onMessageTextChange = {},
                    onDismissDialog = {}
                )
            }
        }

        composeTestRule.onNodeWithText(targetLabel, substring = true).assertIsDisplayed()
    }

    @Test
    fun verify_gemini_fields_shown_when_selected() {
        val label = context.getString(R.string.button_action_gemini)
        val promptLabel = context.getString(R.string.button_gemini_prompt_field)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDimensions provides Dimensions()) {
                ActionConfigFields(
                    selectedActionType = label,
                    pages = emptyList(),
                    templates = emptyList(),
                    targetPageId = "",
                    onTargetPageIdChange = {},
                    geminiPrompt = "",
                    onGeminiPromptChange = {},
                    rank = 1,
                    onRankChange = {},
                    deviceActionType = DeviceActionType.READ_BATTERY,
                    onDeviceActionTypeChange = {},
                    volumeValue = "",
                    onVolumeValueChange = {},
                    contactName = "",
                    onContactNameChange = {},
                    contactPhone = "",
                    onContactPhoneChange = {},
                    messageText = "",
                    onMessageTextChange = {},
                    onDismissDialog = {}
                )
            }
        }

        composeTestRule.onNodeWithText(promptLabel, substring = true).assertIsDisplayed()
    }

    @Test
    fun verify_device_control_fields_shown_when_selected() {
        val label = context.getString(R.string.button_action_control_device)
        val typeLabel = context.getString(R.string.button_device_control_type_label)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDimensions provides Dimensions()) {
                ActionConfigFields(
                    selectedActionType = label,
                    pages = emptyList(),
                    templates = emptyList(),
                    targetPageId = "",
                    onTargetPageIdChange = {},
                    geminiPrompt = "",
                    onGeminiPromptChange = {},
                    rank = 1,
                    onRankChange = {},
                    deviceActionType = DeviceActionType.READ_BATTERY,
                    onDeviceActionTypeChange = {},
                    volumeValue = "",
                    onVolumeValueChange = {},
                    contactName = "",
                    onContactNameChange = {},
                    contactPhone = "",
                    onContactPhoneChange = {},
                    messageText = "",
                    onMessageTextChange = {},
                    onDismissDialog = {}
                )
            }
        }

        composeTestRule.onNodeWithText(typeLabel, substring = true).assertIsDisplayed()
    }
}
