package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SignOutUseCaseTest {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var useCase: SignOutUseCase

    @Before
    fun setup() {
        googleAuthManager = mockk(relaxed = true)
        useCase = SignOutUseCase(googleAuthManager)
    }

    @Test
    fun `execute calls signOut on googleAuthManager`() = runTest {
        useCase.execute()

        coVerify(exactly = 1) { googleAuthManager.signOut() }
    }
}
