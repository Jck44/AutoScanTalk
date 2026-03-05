package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.domain.auth.SignInUseCase

import android.app.Activity
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SignInUseCaseTest {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var useCase: SignInUseCase

    @Before
    fun setup() {
        googleAuthManager = mockk()
        useCase = SignInUseCase(googleAuthManager)
    }

    @Test
    fun `execute calls googleAuthManager signIn`() = runTest {
        val activity = mockk<Activity>()
        coEvery { googleAuthManager.signIn(activity) } returns true

        val result = useCase.execute(activity)

        assertTrue(result)
    }
}
