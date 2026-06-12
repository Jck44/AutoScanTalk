package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiActionHandlerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val geminiUseCase = mockk<GeminiUseCase>(relaxed = true)
    private val ttsProxy = mockk<ActionTtsProxy>(relaxed = true)
    private val visionUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.VisionUseCase>(relaxed = true)
    private val actionLogger = mockk<ActionLogger>(relaxed = true)
    private val buttonUsageRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository>(relaxed = true)
    private val cameraProvider = mockk<CameraProvider>(relaxed = true)
    
    private lateinit var handler: GeminiActionHandler

    @Before
    fun setup() {
        handler = GeminiActionHandler(
            scope = scope,
            context = context,
            settingsRepository = settingsRepository,
            geminiUseCaseLazy = object : dagger.Lazy<GeminiUseCase> {
                override fun get() = geminiUseCase
            },
            visionUseCase = visionUseCase,
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get() = ttsProxy
            },
            actionLogger = actionLogger,
            buttonUsageRepository = buttonUsageRepository,
            cameraProvider = cameraProvider
        )
        every { ttsProxy.isReady } returns true
    }

    @Test
    fun `CloudAction should speak Wait 45s when 429 occurs`() = scope.runTest {
        // GIVEN
        val action = GeminiButtonAction("Help")
        val config = ButtonConfig(id = "1", label = "Gemini", auditoryCue = null, buttonAction = action)
        
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any(), any()) } throws Exception("HTTP 429: Rate limit exceeded. Please retry in 45s")
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        
        // Mock localized string
        every { context.getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, 45) } returns "Wait 45s"
        
        every { ttsProxy.speakRouted(text = "Wait 45s", deviceAddress = any(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        verify { ttsProxy.speakRouted(text = "Wait 45s", deviceAddress = any(), onDone = any()) }
        
        ttsCallback.captured.invoke()
        runCurrent()
        verify { onFinish(1) }
    }

    @Test
    fun `GeminiActionHandler regression - should extract wait time correctly from 429 error and skip status code`() = scope.runTest {
        // GIVEN
        every { settingsRepository.isGeminiEnabled } returns true
        every { ttsProxy.isReady } returns true
        
        // Mock localized string
        every { context.getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, 45) } returns "Wait 45s"

        // Mock 429 with both code and wait time
        coEvery { geminiUseCase.generateResponse(any(), any()) } throws Exception("HTTP 429: Rate limit exceeded. Please retry in 45s.")

        val button = ButtonConfig(id = "1", label = "G", buttonAction = GeminiButtonAction("Hi"), auditoryCue = null)
        
        // WHEN
        handler.handle(button, button.buttonAction, 1) {}
        runCurrent()

        // THEN
        // Verify it extracts 45, not 429 or 42945
        verify { ttsProxy.speakRouted("Wait 45s", any(), any(), any(), any()) }
    }

    @Test
    fun `GeminiActionHandler - should default to 60s if 429 error message has no time`() = scope.runTest {
        // GIVEN
        every { settingsRepository.isGeminiEnabled } returns true
        every { ttsProxy.isReady } returns true
        
        // Mock localized string for 60s default
        every { context.getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, 60) } returns "Wait 60s"

        // Mock 429 without wait time
        coEvery { geminiUseCase.generateResponse(any(), any()) } throws Exception("HTTP 429: Rate limit exceeded.")

        val button = ButtonConfig(id = "1", label = "G", buttonAction = GeminiButtonAction("Hi"), auditoryCue = null)
        
        // WHEN
        handler.handle(button, button.buttonAction, 1) {}
        runCurrent()

        // THEN
        verify { ttsProxy.speakRouted("Wait 60s", any(), any(), any(), any()) }
    }

    @Test
    fun `GeminiSearchButtonAction should not use Google Search anymore`() = scope.runTest {
        // GIVEN
        val action = com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction("Search for something")
        val config = ButtonConfig(id = "1", label = "Search", auditoryCue = null, buttonAction = action)
        
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any(), any()) } returns "Regular Cloud Response"
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        // Must explicitly pass useGoogleSearch = false
        coVerify { geminiUseCase.generateResponse("Search for something", false) }
        verify { ttsProxy.speakRouted(text = "Regular Cloud Response", deviceAddress = any(), onDone = any()) }
    }

    @Test
    fun `VisionAction should capture image and play sound if enabled`() = scope.runTest {
        // GIVEN
        val action = com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(
            prompt = "Describe",
            useCloud = true,
            playShutterSound = true
        )
        val config = ButtonConfig(id = "1", label = "Vision", buttonAction = action, auditoryCue = null)
        
        // Mock cache dir for image saving
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "ghosttalk_test_cache")
        tempDir.mkdirs()
        every { context.cacheDir } returns tempDir
        
        // Mock MediaActionSound to avoid "Method not mocked" error
        io.mockk.mockkConstructor(android.media.MediaActionSound::class)
        every { anyConstructed<android.media.MediaActionSound>().load(any()) } returns Unit
        every { anyConstructed<android.media.MediaActionSound>().play(any()) } returns Unit
        
        val bitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        every { bitmap.compress(any(), any(), any()) } returns true
        coEvery { cameraProvider.captureImage() } returns bitmap
        coEvery { visionUseCase.describeImage(any(), any()) } returns "A photo"
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify { cameraProvider.captureImage() }
        coVerify { visionUseCase.describeImage(bitmap, "Describe") }
        coVerify { buttonUsageRepository.updateLastEventImage(any()) }
        
        val ttsCallback = slot<() -> Unit>()
        verify { ttsProxy.speakRouted(text = "A photo", deviceAddress = any(), onDone = capture(ttsCallback)) }
        
        ttsCallback.captured.invoke()
        runCurrent()
        verify { onFinish(1) }
    }

    @Test
    fun `VisionAction should speak error if camera permission is missing`() = scope.runTest {
        // GIVEN
        val action = com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction("Describe", true)
        val config = ButtonConfig(id = "1", label = "Vision", buttonAction = action, auditoryCue = null)
        
        // Mock permission missing
        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_DENIED
        
        // Broaden getString mock to handle various vararg/array combinations
        every { context.getString(any()) } returns "No Camera"
        every { context.getString(any(), *anyVararg()) } returns "No Camera"
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        every { ttsProxy.speakRouted(text = any(), deviceAddress = any(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        verify { ttsProxy.speakRouted(text = "No Camera", deviceAddress = any(), onDone = any()) }
        ttsCallback.captured.invoke() // Manually trigger to satisfy onFinish if verified
        runCurrent()
        
        coVerify(exactly = 0) { cameraProvider.captureImage() }
        
        io.mockk.unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `CloudAction should handle timeout or slow response`() = scope.runTest {
        // GIVEN
        val action = GeminiButtonAction("Complex query")
        val config = ButtonConfig(id = "1", label = "Gemini", buttonAction = action, auditoryCue = null)
        
        every { settingsRepository.isGeminiEnabled } returns true
        
        // Mock a slow response
        coEvery { geminiUseCase.generateResponse(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            "Slow Response"
        }
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        every { ttsProxy.speakRouted(text = "Slow Response", deviceAddress = any(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        testScheduler.advanceTimeBy(6000)
        runCurrent()
        
        // THEN
        verify { ttsProxy.speakRouted(text = "Slow Response", deviceAddress = any(), onDone = any()) }
        
        ttsCallback.captured.invoke()
        runCurrent()
        
        verify { onFinish(1) }
    }
}
