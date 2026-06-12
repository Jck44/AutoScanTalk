@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.ui.pages.components

import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class ButtonActionFactoryTest {

    @Test
    fun testActionTypeIdOf() {
        assertEquals(ActionTypeId.SPEAK, ButtonActionFactory.actionTypeIdOf(SpeakTextButtonAction()))
        assertEquals(ActionTypeId.NAVIGATE, ButtonActionFactory.actionTypeIdOf(NavigateToPageButtonAction("page1")))
        assertEquals(ActionTypeId.NAVIGATE_BACK, ButtonActionFactory.actionTypeIdOf(NavigateBackButtonAction()))
        assertEquals(ActionTypeId.NAVIGATE_TO_START_PAGE, ButtonActionFactory.actionTypeIdOf(NavigateToStartPageButtonAction()))
        assertEquals(ActionTypeId.GEMINI, ButtonActionFactory.actionTypeIdOf(GeminiButtonAction("prompt")))
        assertEquals(ActionTypeId.GEMINI_SEARCH, ButtonActionFactory.actionTypeIdOf(GeminiSearchButtonAction("prompt")))
        @Suppress("DEPRECATION")
        assertEquals(ActionTypeId.GEMINI, ButtonActionFactory.actionTypeIdOf(GeminiNanoButtonAction("intent")))
        assertEquals(ActionTypeId.GEMINI_VISION, ButtonActionFactory.actionTypeIdOf(GeminiVisionButtonAction("prompt")))
        assertEquals(ActionTypeId.WEATHER, ButtonActionFactory.actionTypeIdOf(WeatherButtonAction()))

        assertEquals(ActionTypeId.READ_NOTIFICATIONS, ButtonActionFactory.actionTypeIdOf(ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)))
        assertEquals(ActionTypeId.SPOTIFY, ButtonActionFactory.actionTypeIdOf(PlayMediaButtonAction(MediaProvider.SPOTIFY)))
        assertEquals(ActionTypeId.PHILIPS_HUE, ButtonActionFactory.actionTypeIdOf(SmartHomeButtonAction(SmartHomeProvider.PHILIPS_HUE)))

        assertEquals(ActionTypeId.FREQUENT, ButtonActionFactory.actionTypeIdOf(FrequentActionButtonAction(3)))
        assertEquals(ActionTypeId.SMART, ButtonActionFactory.actionTypeIdOf(SmartPredictionButtonAction(2)))
        assertEquals(ActionTypeId.PREVIOUS, ButtonActionFactory.actionTypeIdOf(PreviousActionButtonAction(1)))
    }

    @Test
    fun testBuildAction() {
        val params = ActionParams(
            targetPageId = "testPage",
            geminiPrompt = "testPrompt",
            rank = 5,
            predictionType = PredictionType.ACTION,
            deviceActionType = DeviceActionType.READ_TIME,
            volumeValue = "75",
            contactName = "Alice",
            messageText = "Hello",
            mediaProvider = MediaProvider.SPOTIFY,
            mediaContentUri = "spotify:uri",
            mediaContentName = "MyPlaylist",
            smartHomeProvider = SmartHomeProvider.PHILIPS_HUE,
            smartHomeDeviceId = "hue1",
            smartHomeDeviceName = "HueLight",
            smartHomeIntent = "on"
        )

        val speak = ButtonActionFactory.buildAction(ActionTypeId.SPEAK, params)
        assertTrue(speak is SpeakTextButtonAction)

        val nav = ButtonActionFactory.buildAction(ActionTypeId.NAVIGATE, params)
        assertTrue(nav is NavigateToPageButtonAction)
        assertEquals("testPage", (nav as NavigateToPageButtonAction).pageId)

        val gemini = ButtonActionFactory.buildAction(ActionTypeId.GEMINI, params)
        assertTrue(gemini is GeminiButtonAction)
        assertEquals("testPrompt", (gemini as GeminiButtonAction).prompt)


        val deviceMsg = ButtonActionFactory.buildAction(ActionTypeId.SEND_MESSAGE, params)
        assertTrue(deviceMsg is ControlDeviceButtonAction)
        assertEquals(DeviceActionType.SEND_MESSAGE, (deviceMsg as ControlDeviceButtonAction).actionType)
        assertEquals("Alice", deviceMsg.contactName)
        assertEquals("Hello", deviceMsg.messageText)

        val spotify = ButtonActionFactory.buildAction(ActionTypeId.SPOTIFY, params)
        assertTrue(spotify is PlayMediaButtonAction)
        assertEquals(MediaProvider.SPOTIFY, (spotify as PlayMediaButtonAction).provider)
        assertEquals("spotify:uri", spotify.contentUri)
        assertEquals("MyPlaylist", spotify.contentName)

        val hue = ButtonActionFactory.buildAction(ActionTypeId.PHILIPS_HUE, params)
        assertTrue(hue is SmartHomeButtonAction)
        assertEquals(SmartHomeProvider.PHILIPS_HUE, (hue as SmartHomeButtonAction).provider)
        assertEquals("hue1", hue.deviceId)
        assertEquals("HueLight", hue.deviceName)
        assertEquals("on", hue.intent)
    }

    @Test
    fun roundtripAllDeviceActionTypes() {
        for (type in DeviceActionType.entries) {
            val id = ButtonActionFactory.actionTypeIdOf(ControlDeviceButtonAction(type))
            val rebuilt = ButtonActionFactory.buildAction(id, ActionParams())
            assertEquals(type, (rebuilt as ControlDeviceButtonAction).actionType)
        }
    }

    @Test
    fun buildActionAllIdsRoundtrip() {
        for (id in ActionTypeId.entries) {
            assertEquals(id, ButtonActionFactory.actionTypeIdOf(ButtonActionFactory.buildAction(id, ActionParams())))
        }
    }
}
