package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlDeviceIntegrationTest {

    private val application = mockk<Application>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    @Before
    fun setup() {
        every { settingsRepository.holdingTimeMillis } returns 0L
        every { ttsHelper.isReady } returns true
        
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { application.getSystemService(android.content.Context.AUDIO_SERVICE) } returns audioManager
    }

    @Test
    fun `test ControlDeviceButtonAction dispatches to handler`() = runTest {
        val actionExecutor = ActionExecutor(
            application = application,
            scope = this,
            settingsRepository = settingsRepository,
            logger = logger,
            geminiUseCase = null,
            ttsHelper = ttsHelper,
            localIntentRouter = mockk(relaxed = true),
            weatherExecutor = mockk(relaxed = true),
            buttonUsageRepository = null
        )

        val action = ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT)
        val buttonConfig = ButtonConfig(
            id = "d1",
            label = "Device",
            buttonAction = action,
            auditoryCue = null,
            isActive = true
        )

        actionExecutor.executeButtonAction(buttonConfig)
        
        // ControlDeviceActionHandler uses context.getSystemService(Context.AUDIO_SERVICE)
        verify { application.getSystemService(android.content.Context.AUDIO_SERVICE) }
    }
}
