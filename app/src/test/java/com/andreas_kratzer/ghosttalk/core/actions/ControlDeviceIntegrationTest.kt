package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
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
                scanControllerLazy = object : dagger.Lazy<ScannerController> {
                    override fun get() = mockk<ScannerController>(relaxed = true)
                },
                callActionProxy = object : dagger.Lazy<CallActionProxy> {
                    override fun get() = mockk<CallActionProxy>(relaxed = true)
                },
                actionLogger = actionCoordinator,
                updateManagerLazy = object : dagger.Lazy<com.andreas_kratzer.ghosttalk.core.UpdateManager> {
                    override fun get() = mockk<com.andreas_kratzer.ghosttalk.core.UpdateManager>(relaxed = true)
                },
                syncActionProxy = object : dagger.Lazy<SyncActionProxy> {
                    override fun get() = mockk<SyncActionProxy>(relaxed = true)
                }
            )
        )

        val ttsHelper = mockk<com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper>(relaxed = true)
        val actionExecutor = ActionExecutor(
            scope = this,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = handlers,
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper,
            scanCoordinatorProvider = object : javax.inject.Provider<com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator> {
                override fun get() = mockk<com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator>(relaxed = true)
            },
            firebaseAnalyticsManager = mockk(relaxed = true)
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
