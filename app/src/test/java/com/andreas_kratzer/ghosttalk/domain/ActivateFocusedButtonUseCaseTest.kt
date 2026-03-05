package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateFocusedButtonUseCase
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ActivateFocusedButtonUseCaseTest {

    private lateinit var activateButtonUseCase: ActivateButtonUseCase
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var scanCoordinator: ScanCoordinator
    private lateinit var useCase: ActivateFocusedButtonUseCase

    @Before
    fun setup() {
        activateButtonUseCase = mockk(relaxed = true)
        actionExecutor = mockk(relaxed = true)
        scanCoordinator = mockk(relaxed = true)
        useCase = ActivateFocusedButtonUseCase(activateButtonUseCase)
    }

    @Test
    fun `execute with focused button index calls activateButtonUseCase`() = runTest {
        every { scanCoordinator.focusedButtonIndex } returns MutableStateFlow(5)
        every { scanCoordinator.focusedRowIndex } returns MutableStateFlow(null)

        useCase.execute(null, "b1", true, emptyList(), actionExecutor, scanCoordinator)

        coVerify { 
            activateButtonUseCase.execute(5, null, "b1", true, emptyList(), actionExecutor, scanCoordinator) 
        }
    }

    @Test
    fun `execute with focused row index and no button index calls selectCurrentRow`() = runTest {
        every { scanCoordinator.focusedButtonIndex } returns MutableStateFlow(null)
        every { scanCoordinator.focusedRowIndex } returns MutableStateFlow(2)

        useCase.execute(null, "b1", true, emptyList(), actionExecutor, scanCoordinator)

        coVerify { scanCoordinator.selectCurrentRow() }
    }

    @Test
    fun `execute with neither index does nothing`() = runTest {
        every { scanCoordinator.focusedButtonIndex } returns MutableStateFlow(null)
        every { scanCoordinator.focusedRowIndex } returns MutableStateFlow(null)

        useCase.execute(null, "b1", true, emptyList(), actionExecutor, scanCoordinator)

        coVerify(exactly = 0) { activateButtonUseCase.execute(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { scanCoordinator.selectCurrentRow() }
    }
}
