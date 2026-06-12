package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformProfilesSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDraftCoordinatorTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val performProfilesSyncUseCase = mockk<PerformProfilesSyncUseCase>(relaxed = true)
    private val cloudSyncDelegate = mockk<CloudSyncSettingsDelegate>(relaxed = true)
    private val testScope = TestScope()

    private lateinit var coordinatorScope: CoroutineScope
    private lateinit var coordinator: ProfileDraftCoordinator

    @Before
    fun setup() {
        every { settingsRepository.activeProfileIdFlow } returns MutableStateFlow("profile-default")
        every { settingsRepository.getAllProfilesFlow() } returns MutableStateFlow(emptyList())
        every { settingsRepository.activeProfileId } returns "profile-default"
        coordinatorScope = CoroutineScope(testScope.coroutineContext + Job())
        coordinator = ProfileDraftCoordinator(
            settingsRepository,
            performProfilesSyncUseCase,
            cloudSyncDelegate,
            coordinatorScope
        )
    }

    @After
    fun tearDown() {
        coordinatorScope.cancel()
    }

    @Test
    fun testUpdateWithoutDraftUpdatesRepo() = testScope.runTest {
        var repoUpdated = false
        var configUpdated = false

        coordinator.update(
            updateRepo = { repoUpdated = true },
            updateConfig = { config ->
                configUpdated = true
                config
            }
        )

        assertTrue(repoUpdated)
        assertFalse(configUpdated)
    }

    @Test
    fun testUpdateWithDraftUpdatesConfigNotRepo() = testScope.runTest {
        val testProfile = SettingsProfile(
            id = "profile-test",
            name = "Test Profile",
            config = ProfileConfig(scanDelayMillis = 400L),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("profile-test") } returns testProfile

        coordinator.startEditingProfile("profile-test")
        runCurrent()

        var repoUpdated = false
        coordinator.update(
            updateRepo = { repoUpdated = true },
            updateConfig = { config ->
                config.copy(scanDelayMillis = 600L)
            }
        )
        runCurrent()

        assertFalse(repoUpdated)
        assertTrue(coordinator.hasUnsavedChanges.value)
        assertEquals(600L, coordinator.draftManager.value?.configState?.value?.scanDelayMillis)
    }

    @Test
    fun testScopedFlow_returnsRepoFlowWithoutDraftAndDraftFlowWithDraft() = testScope.runTest {
        val repoFlow = MutableStateFlow(100L)
        val configValFlow = coordinator.scopedFlow(repoFlow) { it.scanDelayMillis }

        assertEquals(100L, configValFlow.value)

        // Start draft editing
        val testProfile = SettingsProfile(
            id = "profile-test",
            name = "Test Profile",
            config = ProfileConfig(scanDelayMillis = 400L),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("profile-test") } returns testProfile

        coordinator.startEditingProfile("profile-test")
        runCurrent()

        // Should return draft's config value
        assertEquals(400L, configValFlow.value)

        // Update config
        coordinator.update(
            updateRepo = {},
            updateConfig = { it.copy(scanDelayMillis = 500L) }
        )
        runCurrent()
        assertEquals(500L, configValFlow.value)

        // Cancel editing
        coordinator.cancelEditingProfile()
        runCurrent()

        // Should return repo value again
        assertEquals(100L, configValFlow.value)
    }

    @Test
    fun testDebounceEchoSchutz_noUploadWhenSyncingOrEmailNull() = testScope.runTest {
        val activeProfileIdFlow = MutableStateFlow("profile-default")
        val allProfilesFlow = MutableStateFlow<List<SettingsProfile>>(emptyList())
        val isSyncingFlow = MutableStateFlow(true)
        val userEmailFlow = MutableStateFlow<String?>("user@example.com")

        every { settingsRepository.activeProfileIdFlow } returns activeProfileIdFlow
        every { settingsRepository.getAllProfilesFlow() } returns allProfilesFlow
        every { settingsRepository.activeProfileId } returns "profile-default"
        every { cloudSyncDelegate.isSyncing } returns isSyncingFlow
        every { cloudSyncDelegate.userEmail } returns userEmailFlow
        every { settingsRepository.isDataCloudSyncEnabled } returns true

        val testProfile = SettingsProfile(
            id = "profile-default",
            name = "Default Profile",
            config = ProfileConfig(),
            profileVersionSequence = 2L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("profile-default") } returns testProfile

        // Re-create coordinator to use these mocked flows
        coordinatorScope.cancel() // Cancel the default setup coordinator
        coordinatorScope = CoroutineScope(testScope.coroutineContext + Job())
        val localCoordinator = ProfileDraftCoordinator(
            settingsRepository,
            performProfilesSyncUseCase,
            cloudSyncDelegate,
            coordinatorScope
        )

        // Emit new profile
        allProfilesFlow.value = listOf(testProfile)
        runCurrent()

        // Fast-forward delay (5000ms)
        testScope.testScheduler.advanceTimeBy(6000)
        runCurrent()

        // Verify that performProfilesSyncUseCase was never executed because isSyncing is true
        io.mockk.coVerify(exactly = 0) { performProfilesSyncUseCase.execute() }
    }

    @Test
    fun testDebounce_noUploadWhenEmailIsNull() = testScope.runTest {
        val activeProfileIdFlow = MutableStateFlow("profile-default")
        val allProfilesFlow = MutableStateFlow<List<SettingsProfile>>(emptyList())
        val isSyncingFlow = MutableStateFlow(false)
        val userEmailFlow = MutableStateFlow<String?>(null)

        every { settingsRepository.activeProfileIdFlow } returns activeProfileIdFlow
        every { settingsRepository.getAllProfilesFlow() } returns allProfilesFlow
        every { settingsRepository.activeProfileId } returns "profile-default"
        every { cloudSyncDelegate.isSyncing } returns isSyncingFlow
        every { cloudSyncDelegate.userEmail } returns userEmailFlow

        val testProfile = SettingsProfile(
            id = "profile-default",
            name = "Default Profile",
            config = ProfileConfig(),
            profileVersionSequence = 2L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("profile-default") } returns testProfile

        coordinatorScope.cancel()
        coordinatorScope = CoroutineScope(testScope.coroutineContext + Job())
        val localCoordinator = ProfileDraftCoordinator(
            settingsRepository,
            performProfilesSyncUseCase,
            cloudSyncDelegate,
            coordinatorScope
        )

        allProfilesFlow.value = listOf(testProfile)
        runCurrent()

        testScope.testScheduler.advanceTimeBy(6000)
        runCurrent()

        io.mockk.coVerify(exactly = 0) { performProfilesSyncUseCase.execute() }
    }

    @Test
    fun testDebounce_noUploadWhenProfileAlreadySyncing() = testScope.runTest {
        val activeProfileIdFlow = MutableStateFlow("profile-default")
        val allProfilesFlow = MutableStateFlow<List<SettingsProfile>>(emptyList())
        val isSyncingFlow = MutableStateFlow(false)
        val userEmailFlow = MutableStateFlow<String?>("user@example.com")

        every { settingsRepository.activeProfileIdFlow } returns activeProfileIdFlow
        every { settingsRepository.getAllProfilesFlow() } returns allProfilesFlow
        every { settingsRepository.activeProfileId } returns "profile-default"
        every { cloudSyncDelegate.isSyncing } returns isSyncingFlow
        every { cloudSyncDelegate.userEmail } returns userEmailFlow

        val testProfile = SettingsProfile(
            id = "profile-default",
            name = "Default Profile",
            config = ProfileConfig(),
            profileVersionSequence = 2L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { settingsRepository.getProfileById("profile-default") } returns testProfile

        coordinatorScope.cancel()
        coordinatorScope = CoroutineScope(testScope.coroutineContext + Job())
        val localCoordinator = ProfileDraftCoordinator(
            settingsRepository,
            performProfilesSyncUseCase,
            cloudSyncDelegate,
            coordinatorScope
        )

        // Make coordinator think it's already syncing
        val isProfileSyncingField = ProfileDraftCoordinator::class.java.getDeclaredField("_isProfileSyncing")
        isProfileSyncingField.isAccessible = true
        val isProfileSyncingState = isProfileSyncingField.get(localCoordinator) as MutableStateFlow<Boolean>
        isProfileSyncingState.value = true

        allProfilesFlow.value = listOf(testProfile)
        runCurrent()

        testScope.testScheduler.advanceTimeBy(6000)
        runCurrent()

        io.mockk.coVerify(exactly = 0) { performProfilesSyncUseCase.execute() }
    }
}
