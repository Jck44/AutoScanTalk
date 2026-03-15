package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleHomeManager
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartHomeActionHandlerTest {
    private val scope = TestScope()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val googleHomeManager = mockk<GoogleHomeManager>(relaxed = true)
    private val hueManager = mockk<PhilipsHueManager>(relaxed = true)

    private val ttsProxy = mockk<ActionTtsProxy>(relaxed = true)
    private val actionLogger = mockk<ActionLogger>(relaxed = true)
    
    private lateinit var handler: SmartHomeActionHandler

    @Before
    fun setup() {
        handler = SmartHomeActionHandler(
            scope = scope,
            googleHomeManagerLazy = object : dagger.Lazy<GoogleHomeManager> {
                override fun get() = googleHomeManager
            },
            hueManagerLazy = object : dagger.Lazy<PhilipsHueManager> {
                override fun get() = hueManager
            },
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get() = ttsProxy
            },
            actionLogger = actionLogger,
            settingsRepository = settingsRepository
        )
        every { ttsProxy.isReady } returns true
    }

    @Test
    fun `handle should delegate to GoogleHomeManager when provider is GOOGLE_HOME`() = scope.runTest {
        // GIVEN
        val action = SmartHomeButtonAction(
            provider = SmartHomeProvider.GOOGLE_HOME,
            deviceId = "device123",
            deviceName = "Licht",
            intent = "action.devices.commands.OnOff",
            value = "on"
        )
        val config = ButtonConfig(id = "1", label = "Home", buttonAction = action)
        
        every { settingsRepository.googleHomeProjectId } returns "project-id"
        coEvery { googleHomeManager.executeCommand(any(), any(), any(), any(), any()) } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify { googleHomeManager.executeCommand("project-id", "device123", any(), "action.devices.commands.OnOff", any()) }
        verify { ttsProxy.speakRouted(text = match { it.contains("Licht") }, any(), any(), any(), any()) }
    }

    @Test
    fun `handle should delegate to PhilipsHueManager using local API when no token`() = scope.runTest {
        // GIVEN
        val action = SmartHomeButtonAction(
            provider = SmartHomeProvider.PHILIPS_HUE,
            deviceId = "light1",
            deviceName = "Hue Light",
            intent = "action.on"
        )
        val config = ButtonConfig(id = "1", label = "Hue", buttonAction = action)
        
        every { settingsRepository.hueAccessToken } returns ""
        every { settingsRepository.hueBridgeIp } returns "192.168.1.100"
        every { settingsRepository.hueUsername } returns "user123"
        coEvery { hueManager.executeLocalCommand(any(), any(), any(), any(), any()) } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify { hueManager.executeLocalCommand("192.168.1.100", "user123", "light1", "action.on", null) }
        verify { ttsProxy.speakRouted(text = match { it.contains("Hue Light") }, any(), any(), any(), any()) }
    }

    @Test
    fun `handle should delegate to PhilipsHueManager using remote API when token present`() = scope.runTest {
        // GIVEN
        val action = SmartHomeButtonAction(
            provider = SmartHomeProvider.PHILIPS_HUE,
            deviceId = "light1",
            deviceName = "Hue Light",
            intent = "action.on"
        )
        val config = ButtonConfig(id = "1", label = "Hue", buttonAction = action)
        
        every { settingsRepository.hueAccessToken } returns "token123"
        every { settingsRepository.hueBridgeIp } returns "bridge123" // Bridge ID in remote context
        coEvery { hueManager.executeRemoteCommand(any(), any(), any(), any(), any()) } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify { hueManager.executeRemoteCommand("token123", "bridge123", "light1", "action.on", null) }
        verify { ttsProxy.speakRouted(text = match { it.contains("Hue Light") }, any(), any(), any(), any()) }
    }


}
