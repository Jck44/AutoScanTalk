package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenManagementDelegateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var callActionProxy: CallActionProxy

    private val keepScreenOnUserModeFlow = MutableStateFlow(false)
    private val userModeScreenBehaviorFlow = MutableStateFlow("NONE")
    private val isInCallFlow = MutableStateFlow(false)

    private lateinit var delegate: ScreenManagementDelegate

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        settingsRepository = mockk(relaxed = true)
        callActionProxy = mockk(relaxed = true)

        every { settingsRepository.keepScreenOnUserModeFlow } returns keepScreenOnUserModeFlow
        every { settingsRepository.userModeScreenBehaviorFlow } returns userModeScreenBehaviorFlow
        every { callActionProxy.isInCall } returns isInCallFlow

        delegate = ScreenManagementDelegate(settingsRepository, callActionProxy)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default state is bright and overlay is hidden when user mode is inactive`() = runTest(testDispatcher) {
        val isUserModeActiveFlow = MutableStateFlow(false)
        delegate.init(backgroundScope, isUserModeActiveFlow)

        val state = delegate.screenState.value
        assertFalse(state.keepScreenOn)
        assertNull(state.dimAmount)
        assertFalse(state.isBlackOverlayVisible)
    }

    @Test
    fun `user mode keepScreenOn is true and behavior is BLACK`() = runTest(testDispatcher) {
        val isUserModeActiveFlow = MutableStateFlow(true)
        keepScreenOnUserModeFlow.value = true
        userModeScreenBehaviorFlow.value = "BLACK"

        delegate.init(backgroundScope, isUserModeActiveFlow)

        val state = delegate.screenState.value
        assertTrue(state.keepScreenOn)
        assertNull(state.dimAmount)
        assertTrue(state.isBlackOverlayVisible)
    }

    @Test
    fun `user mode keepScreenOn is true and behavior is DIMMED`() = runTest(testDispatcher) {
        val isUserModeActiveFlow = MutableStateFlow(true)
        keepScreenOnUserModeFlow.value = true
        userModeScreenBehaviorFlow.value = "DIMMED"

        delegate.init(backgroundScope, isUserModeActiveFlow)

        val state = delegate.screenState.value
        assertTrue(state.keepScreenOn)
        assertEquals(0.01f, state.dimAmount)
        assertFalse(state.isBlackOverlayVisible)
    }

    @Test
    fun `when isInCall is true overlays are disabled and screen is kept on`() = runTest(testDispatcher) {
        val isUserModeActiveFlow = MutableStateFlow(true)
        keepScreenOnUserModeFlow.value = true
        userModeScreenBehaviorFlow.value = "BLACK"
        isInCallFlow.value = true

        delegate.init(backgroundScope, isUserModeActiveFlow)

        val state = delegate.screenState.value
        assertTrue(state.keepScreenOn)
        assertNull(state.dimAmount)
        assertFalse(state.isBlackOverlayVisible)
    }
}
