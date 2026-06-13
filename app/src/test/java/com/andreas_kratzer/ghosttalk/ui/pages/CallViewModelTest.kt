package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.call.SystemCallManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.CallManagementDelegate
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var application: Application
    private lateinit var systemCallManager: SystemCallManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var delegate: CallManagementDelegate
    private lateinit var viewModel: CallViewModel

    private val mockCallStateFlow = MutableStateFlow(CallState.NONE)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        every { settingsRepository.scanDelayMillis } returns 3000L
        every { settingsRepository.holdingTimeMillis } returns 0L
        every { settingsRepository.hangUpPressesRequired } returns 2

        systemCallManager = mockk(relaxed = true) {
            every { callState } returns mockCallStateFlow
            every { callerName } returns MutableStateFlow(null)
            every { callerPhone } returns MutableStateFlow(null)
            every { callDurationSeconds } returns MutableStateFlow(0)
            every { isOutgoing } returns MutableStateFlow(false)
            every { isSimulatedFlow } returns MutableStateFlow(false)
        }

        delegate = CallManagementDelegate(
            application = application,
            systemCallManager = systemCallManager,
            settingsRepository = settingsRepository,
            ttsHelper = ttsHelper
        )

        viewModel = CallViewModel(application, delegate)
    }

    @After
    fun tearDown() {
        mockCallStateFlow.value = CallState.NONE
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `when callState becomes RINGING call scanning starts and answer is announced`() = runTest {
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_answer) } returns "Answer"

        mockCallStateFlow.value = CallState.RINGING
        viewModel.startCallScanning()

        assertEquals("ANNEHMEN", viewModel.focusedCallScreenButton.value)

        verify {
            ttsHelper.speakRouted(
                text = "Answer",
                deviceAddress = any(),
                queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD,
                isForCues = true
            )
        }

        viewModel.stopCallScanning()
    }

    @Test
    fun `handleCallButtonPress answers call when ringing and focused on ANNEHMEN`() = runTest {
        mockCallStateFlow.value = CallState.RINGING
        delegate.focusedCallScreenButton.value = "ANNEHMEN"

        viewModel.handleCallButtonPress()

        verify { systemCallManager.answerCall() }
    }

    @Test
    fun `handleCallButtonPress rejects call when ringing and focused on ABLEHNEN`() = runTest {
        mockCallStateFlow.value = CallState.RINGING
        delegate.focusedCallScreenButton.value = "ABLEHNEN"

        viewModel.handleCallButtonPress()

        verify { systemCallManager.hangUp() }
    }

    @Test
    fun `handleCallButtonPress focuses hang up on first press and hangs up on second press`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 2
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up) } returns "Hang Up"
        mockCallStateFlow.value = CallState.ACTIVE

        viewModel.handleCallButtonPress()
        assertEquals(true, viewModel.isHangUpButtonFocused.value)
        verify {
            ttsHelper.speakRouted("Hang Up", any(), any(), isForCues = true)
        }

        viewModel.handleCallButtonPress()
        verify { systemCallManager.hangUp() }
    }

    @Test
    fun `hang up focus and count reset after the press window elapses`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 2
        every { settingsRepository.hangUpPressWindowSeconds } returns 3
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up) } returns "Hang Up"
        mockCallStateFlow.value = CallState.ACTIVE

        viewModel.handleCallButtonPress()
        assertEquals(true, viewModel.isHangUpButtonFocused.value)
        assertEquals(1, delegate.hangUpPressCount.value)

        // Let the configured window (3s) elapse without a second press.
        testDispatcher.scheduler.advanceTimeBy(3_001)
        testDispatcher.scheduler.runCurrent()

        assertEquals(false, viewModel.isHangUpButtonFocused.value)
        assertEquals(0, delegate.hangUpPressCount.value)
        verify(exactly = 0) { systemCallManager.hangUp() }
    }
}
