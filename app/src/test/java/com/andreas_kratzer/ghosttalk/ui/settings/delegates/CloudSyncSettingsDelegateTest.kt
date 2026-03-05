package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.domain.auth.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SignInUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SignOutUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SyncMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var application: Application
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var setCloudSyncEnabledUseCase: SetCloudSyncEnabledUseCase
    private lateinit var performManualSyncUseCase: PerformManualSyncUseCase
    private lateinit var signInUseCase: SignInUseCase
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var delegate: CloudSyncSettingsDelegate

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        setCloudSyncEnabledUseCase = mockk(relaxed = true)
        performManualSyncUseCase = mockk(relaxed = true)
        signInUseCase = mockk(relaxed = true)
        signOutUseCase = mockk(relaxed = true)
        
        delegate = CloudSyncSettingsDelegate(
            application,
            googleAuthManager,
            setCloudSyncEnabledUseCase,
            performManualSyncUseCase,
            signInUseCase,
            signOutUseCase
        )
    }

    @Test
    fun `setCloudSyncEnabled calls use case`() {
        delegate.setCloudSyncEnabled(true)
        verify { setCloudSyncEnabledUseCase(true) }

        delegate.setCloudSyncEnabled(false)
        verify { setCloudSyncEnabledUseCase(false) }
    }

    @Test
    fun `performManualSync calls use case`() = runTest {
        coEvery { performManualSyncUseCase.execute(any(), any()) } returns PerformManualSyncUseCase.Result.Success
        delegate.performManualSync(SyncMode.TWO_WAY, this)
        advanceUntilIdle()
        coVerify { performManualSyncUseCase.execute(SyncMode.TWO_WAY, null) }
    }

    @Test
    fun `signIn handles failure correctly`() = runTest {
        val activity = mockk<android.app.Activity>(relaxed = true)
        coEvery { googleAuthManager.signIn(activity) } returns false
        
        delegate.signIn(activity, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(delegate.signInErrorMessage.value?.contains("fehlgeschlagen") == true)
    }
}
