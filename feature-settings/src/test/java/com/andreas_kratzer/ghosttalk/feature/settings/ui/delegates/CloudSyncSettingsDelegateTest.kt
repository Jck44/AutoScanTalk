package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.app.Activity
import android.app.Application
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.domain.GetAvailableBackupsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.ImportCloudBackupUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignOutUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var getAvailableBackupsUseCase: GetAvailableBackupsUseCase
    private lateinit var importCloudBackupUseCase: ImportCloudBackupUseCase
    private lateinit var getDriveFoldersUseCase: com.andreas_kratzer.ghosttalk.core.cloud.domain.GetDriveFoldersUseCase
    private lateinit var signInUseCase: SignInUseCase
    private lateinit var signOutUseCase: SignOutUseCase
    private lateinit var syncLogProvider: SyncLogProvider
    private lateinit var rescheduleProfileSyncUseCase: com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleProfileSyncUseCase
    private lateinit var delegate: CloudSyncSettingsDelegate

    private val userEmailFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<Int>(), any()) } returns mockk(relaxed = true)
        every { Toast.makeText(any(), any<String>(), any()) } returns mockk(relaxed = true)

        application = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        setCloudSyncEnabledUseCase = mockk(relaxed = true)
        performManualSyncUseCase = mockk(relaxed = true)
        getAvailableBackupsUseCase = mockk(relaxed = true)
        importCloudBackupUseCase = mockk(relaxed = true)
        getDriveFoldersUseCase = mockk(relaxed = true)
        signInUseCase = mockk(relaxed = true)
        signOutUseCase = mockk(relaxed = true)
        syncLogProvider = mockk(relaxed = true)
        rescheduleProfileSyncUseCase = mockk(relaxed = true)
        
        every { googleAuthManager.userEmail } returns userEmailFlow
        every { settingsRepository.googleAuthTypeFlow } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM)
        every { settingsRepository.googleUserEmailFlow } returns MutableStateFlow(null)

        delegate = CloudSyncSettingsDelegate(
            application = application,
            authManager = googleAuthManager,
            settingsRepository = settingsRepository,
            setCloudSyncEnabledUseCase = setCloudSyncEnabledUseCase,
            performManualSyncUseCase = performManualSyncUseCase,
            getAvailableBackupsUseCase = getAvailableBackupsUseCase,
            importCloudBackupUseCase = importCloudBackupUseCase,
            getDriveFoldersUseCase = getDriveFoldersUseCase,
            signInUseCase = signInUseCase,
            signOutUseCase = signOutUseCase,
            syncLogProvider = syncLogProvider,
            rescheduleProfileSyncUseCase = rescheduleProfileSyncUseCase
        )
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        unmockkStatic(Toast::class)
    }

    @Test
    fun `setCloudSyncEnabled calls use case when logged in`() {
        userEmailFlow.value = "test@example.com"
        testDispatcher.scheduler.advanceUntilIdle()
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
        coEvery { performManualSyncUseCase.execute(any(), any()) } returns PerformManualSyncUseCase.Result.Success
        delegate.performManualSync(SyncMode.TWO_WAY, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { performManualSyncUseCase.execute(SyncMode.TWO_WAY, any()) }
    }

    @Test
    fun `signIn handles failure correctly`() = runTest {
        val activity = mockk<Activity>(relaxed = true)
        coEvery { signInUseCase.execute(activity) } returns false
        
        delegate.signIn(activity, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(delegate.signInErrorMessage.value?.contains("fehlgeschlagen") == true)
    }
    
    @Test
    fun `fetchAvailableBackupsForImport handles success`() = runTest {
        coEvery { googleAuthManager.getGoogleCredential() } returns mockk(relaxed = true)
        coEvery { getAvailableBackupsUseCase.execute(any()) } returns listOf(
            com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo("id", "file", "book", 123L)
        )
        
        delegate.fetchAvailableBackupsForImport(scope = testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(delegate.availableBackups.value.isNotEmpty())
        assert(delegate.showBackupSelectionDialog.value)
    }

    // -------------------------------------------------------------------------
    // extractFolderId — URL parsing
    // -------------------------------------------------------------------------

    @Test
    fun `extractFolderId extracts ID from folders-path URL`() {
        val url = "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQr"
        val id = delegate.extractFolderId(url)
        assertEquals("1AbCdEfGhIjKlMnOpQr", id)
    }

    @Test
    fun `extractFolderId extracts ID from id-query-param URL`() {
        val url = "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQr"
        val id = delegate.extractFolderId(url)
        assertEquals("1AbCdEfGhIjKlMnOpQr", id)
    }

    @Test
    fun `extractFolderId returns raw ID when input is a plain ID`() {
        val rawId = "1AbCdEfGhIjKlMnOpQr_-xyz"
        val id = delegate.extractFolderId(rawId)
        assertEquals(rawId, id)
    }

    @Test
    fun `extractFolderId returns empty string for invalid input`() {
        assertEquals("", delegate.extractFolderId("not a url or id !!"))
        assertEquals("", delegate.extractFolderId("https://drive.google.com/no/id/here"))
    }

    @Test
    fun `extractFolderId trims whitespace before parsing`() {
        val url = "  https://drive.google.com/drive/folders/TrimmedId123  "
        assertEquals("TrimmedId123", delegate.extractFolderId(url))
    }

    @Test
    fun `extractFolderId handles http URLs as well as https`() {
        val url = "http://drive.google.com/drive/folders/HttpFolderId"
        assertEquals("HttpFolderId", delegate.extractFolderId(url))
    }

    // -------------------------------------------------------------------------
    // fetchAvailableBackupsFromSaf
    // -------------------------------------------------------------------------

    @Test
    fun `fetchAvailableBackupsFromSaf shows dialog when backups found`() = runTest {
        coEvery { getAvailableBackupsUseCase.execute(null, "content://test/uri") } returns listOf(
            com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo("content://test/uri/book.zip", "book.zip", "My Book", 123L)
        )

        delegate.fetchAvailableBackupsFromSaf("content://test/uri", "Test Ordner", testScope)
        testDispatcher.scheduler.advanceUntilIdle()

        assert(delegate.availableBackups.value.isNotEmpty())
        assert(delegate.showBackupSelectionDialog.value)
    }

    @Test
    fun `fetchAvailableBackupsFromSaf does not show dialog when no backups found`() = runTest {
        coEvery { getAvailableBackupsUseCase.execute(null, "content://test/empty") } returns emptyList()

        delegate.fetchAvailableBackupsFromSaf("content://test/empty", "Leerer Ordner", testScope)
        testDispatcher.scheduler.advanceUntilIdle()

        assert(delegate.availableBackups.value.isEmpty())
        assert(!delegate.showBackupSelectionDialog.value)
    }

    @Test
    fun `fetchAvailableBackupsFromSaf sets isSyncing false after completion`() = runTest {
        coEvery { getAvailableBackupsUseCase.execute(null, any()) } returns emptyList()

        delegate.fetchAvailableBackupsFromSaf("content://test/uri", "Ordner", testScope)
        testDispatcher.scheduler.advanceUntilIdle()

        assert(!delegate.isSyncing.value)
    }
}
