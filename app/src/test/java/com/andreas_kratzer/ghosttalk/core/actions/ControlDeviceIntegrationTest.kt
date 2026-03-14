package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
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
    private val buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
    private val actionCoordinator = mockk<ActionCoordinator>(relaxed = true)

    @Before
    fun setup() {
        every { settingsRepository.holdingTimeMillis } returns 0L
        
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { application.getSystemService(android.content.Context.AUDIO_SERVICE) } returns audioManager
    }

    @Test
    fun `test ControlDeviceButtonAction dispatches to handler`() = runTest {
        val handlers = setOf(
            ControlDeviceActionHandler(
                context = application,
                settings = mockk(relaxed = true),
                ttsProxyLazy = object : dagger.Lazy<ControlDeviceTtsProxy> {
                    override fun get() = mockk<ControlDeviceTtsProxy>(relaxed = true)
                },
                actionLogger = actionCoordinator,
                actionEventEmitter = actionCoordinator
            )
        )

        val actionExecutor = ActionExecutor(
            scope = this,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = handlers,
            actionCoordinator = actionCoordinator
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
        
        verify { application.getSystemService(android.content.Context.AUDIO_SERVICE) }
    }
}
