package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import dagger.Lazy

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorLazyTest {

    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var logger: Logger
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var ttsHelper: TextToSpeechHelper
    
    private var geminiGetCount = 0
    private var ttsGetCount = 0

    private lateinit var geminiLazy: Lazy<GeminiUseCase>
    private lateinit var ttsLazy: Lazy<TextToSpeechHelper>

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true) {
            every { isGeminiEnabled } returns true
            every { useLocalGenerativeAi } returns true
            every { ttsAudioDeviceAddress } returns "mock_address"
            every { cuesAudioDeviceAddress } returns "mock_cues_address"
        }
        logger = mockk(relaxed = true)
        geminiUseCase = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        
        geminiGetCount = 0
        ttsGetCount = 0

        geminiLazy = object : Lazy<GeminiUseCase> {
            override fun get(): GeminiUseCase {
                geminiGetCount++
                return geminiUseCase
            }
        }

        ttsLazy = object : Lazy<TextToSpeechHelper> {
            override fun get(): TextToSpeechHelper {
                ttsGetCount++
                return ttsHelper
            }
        }
    }

    @Test
    fun `navigation action does NOT initialize gemini or tts`() = scope.runTest {
        val executor = createExecutor()
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", buttonAction = action, auditoryCue = null)

        executor.executeButtonAction(config)

        assert(geminiGetCount == 0) { "GeminiUseCase was initialized unnecessarily!" }
        assert(ttsGetCount == 0) { "TextToSpeechHelper was initialized unnecessarily!" }
    }

    @Test
    fun `gemini action initializes gemini only when needed`() = scope.runTest {
        val executor = createExecutor()
        val action = GeminiButtonAction("Hello")
        val config = ButtonConfig(id = "b1", label = "Ask", buttonAction = action, auditoryCue = null)

        executor.executeButtonAction(config)

        assert(geminiGetCount == 1) { "GeminiUseCase should be initialized exactly once" }
        // Note: GeminiActionHandler might also need TTS for feedback
        // If it doesn't use it in this specific test, ttsGetCount should be 0 or 1 depending on logic
    }

    @Test
    fun `executor handles lazy initialization failure gracefully`() = scope.runTest {
        val failingGeminiLazy = object : Lazy<GeminiUseCase> {
            override fun get(): GeminiUseCase = throw RuntimeException("Initialization Failed")
        }
        
        val executor = ActionExecutor(
            application = application,
            scope = scope,
            settingsRepository = settingsRepository,
            logger = logger,
            localIntentRouter = mockk(relaxed = true),
            weatherExecutor = mockk(relaxed = true),
            buttonUsageRepository = mockk(relaxed = true),
            geminiUseCaseLazy = failingGeminiLazy,
            ttsHelperLazy = ttsLazy
        )

        val action = GeminiButtonAction("Hello")
        val config = ButtonConfig(id = "b1", label = "Ask", buttonAction = action, auditoryCue = null)

        // This should not crash the app, but log an error
        executor.executeButtonAction(config)
        
        verify { logger.e(any(), any(), any()) }
    }

    private fun createExecutor() = ActionExecutor(
        application = application,
        scope = scope,
        settingsRepository = settingsRepository,
        logger = logger,
        localIntentRouter = mockk(relaxed = true),
        weatherExecutor = mockk(relaxed = true),
        buttonUsageRepository = mockk(relaxed = true),
        geminiUseCaseLazy = geminiLazy,
        ttsHelperLazy = ttsLazy
    )
}
