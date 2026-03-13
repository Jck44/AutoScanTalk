package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Activity
import android.app.Application
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignOutUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var application: Application
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
    private lateinit var setCloudSyncEnabledUseCase: SetCloudSyncEnabledUseCase
    private lateinit var performManualSyncUseCase: PerformManualSyncUseCase
    private lateinit var cloudSyncUseCase: CloudSyncUseCase
    private lateinit var signInUseCase: SignInUseCase
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var delegate: CloudSyncSettingsDelegate

    private val userEmailFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<Int>(), any()) } returns mockk(relaxed = true)
        every { Toast.makeText(any(), any<String>(), any()) } returns mockk(relaxed = true)

        application = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        setCloudSyncEnabledUseCase = mockk(relaxed = true)
        performManualSyncUseCase = mockk(relaxed = true)
        cloudSyncUseCase = mockk(relaxed = true)
        signInUseCase = mockk(relaxed = true)
        signOutUseCase = mockk(relaxed = true)
        
        every { googleAuthManager.userEmail } returns userEmailFlow

        delegate = CloudSyncSettingsDelegate(
            application,
            googleAuthManager,
            settingsRepository,
            setCloudSyncEnabledUseCase,
            performManualSyncUseCase,
            cloudSyncUseCase,
            signInUseCase,
            signOutUseCase
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Toast::class)
    }

    @Test
    fun `setCloudSyncEnabled calls use case when logged in`() {
        userEmailFlow.value = "test@example.com"
        val context = mockk<Activity>(relaxed = true)

        delegate.setCloudSyncEnabled(context, true, testScope)
        verify { setCloudSyncEnabledUseCase(true) }

        delegate.setCloudSyncEnabled(context, false, testScope)
        verify { setCloudSyncEnabledUseCase(false) }
    }

    @Test
    fun `setCloudSyncEnabled triggers sign in when not logged in`() = runTest {
        userEmailFlow.value = null
        val context = mockk<Activity>(relaxed = true)
        coEvery { signInUseCase.execute(any()) } returns true

        delegate.setCloudSyncEnabled(context, true, testScope)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { signInUseCase.execute(context) }
        verify(exactly = 0) { setCloudSyncEnabledUseCase(any()) }
    }

    @Test
    fun `performManualSync calls use case`() = runTest {
        coEvery { performManualSyncUseCase.execute(any()) } returns PerformManualSyncUseCase.Result.Success
        delegate.performManualSync(SyncMode.TWO_WAY, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { performManualSyncUseCase.execute(SyncMode.TWO_WAY) }
    }

    @Test
    fun `signIn handles failure correctly`() = runTest {
        val activity = mockk<Activity>(relaxed = true)
        coEvery { signInUseCase.execute(activity) } returns false
        
        delegate.signIn(activity, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(delegate.signInErrorMessage.value?.contains("fehlgeschlagen") == true)
    }
}
