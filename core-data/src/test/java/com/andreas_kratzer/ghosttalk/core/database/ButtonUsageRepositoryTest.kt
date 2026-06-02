package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.andreas_kratzer.ghosttalk.core.data.impl.ButtonUsageRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ButtonUsageRepositoryTest {

    private val mockContext = mockk<android.content.Context>(relaxed = true)
    private val mockButtonUsageDao = mockk<ButtonUsageDao>(relaxed = true)
    private val mockSettingsRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.SettingsRepository>(relaxed = true)
    private val testScope = kotlinx.coroutines.test.TestScope()
    private val mockDatabase = mockk<AppDatabase>(relaxed = true)
    private val mockSessionTracker = mockk<com.andreas_kratzer.ghosttalk.core.data.impl.UserModeSessionTracker>(relaxed = true)
    private lateinit var buttonUsageRepository: ButtonUsageRepositoryImpl

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic(com.google.android.gms.location.LocationServices::class)
        io.mockk.every { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(any<android.content.Context>()) } returns mockk(relaxed = true)

        mockkStatic(androidx.core.content.ContextCompat::class)
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_DENIED

        coEvery { any<RoomDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
        coEvery { mockSettingsRepository.activeBookIdFlow } returns kotlinx.coroutines.flow.MutableStateFlow("book1")
        io.mockk.every { mockSessionTracker.currentSessionId } returns null
        io.mockk.every { mockContext.applicationContext } returns mockContext
        buttonUsageRepository = ButtonUsageRepositoryImpl(mockContext, mockButtonUsageDao, mockSettingsRepository, testScope, mockDatabase, mockSessionTracker)
    }

    @After
    fun teardown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        unmockkStatic(com.google.android.gms.location.LocationServices::class)
        unmockkStatic(androidx.core.content.ContextCompat::class)
    }
    
    private val testButton = ButtonConfig(
        id = "btn-1",
        label = "Ja",
        auditoryCue = null,
        buttonAction = SpeakTextButtonAction()
    )

    @Test
    fun `recordUsage creates new stat when button not yet tracked`() = runTest {
        coEvery { mockButtonUsageDao.getStatForButton("book1", "btn-1") } returns null

        buttonUsageRepository.recordUsage("book1", "page1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { mockButtonUsageDao.upsert(capture(statSlot)) }

        val stat = statSlot.captured
        assertEquals("book1", stat.bookId)
        assertEquals("btn-1", stat.buttonConfigId)
        assertEquals("Ja", stat.label)
        assertEquals(1L, stat.usageCount)
    }

    @Test
    fun `recordUsage increments counter for existing stat`() = runTest {
        val existing = ButtonUsageStat(
            bookId = "book1",
            buttonConfigId = "btn-1",
            pageId = "page1",
            label = "Ja",
            actionJson = "{}",
            usageCount = 5,
            lastUsedAt = 1000L
        )
        coEvery { mockButtonUsageDao.getStatForButton("book1", "btn-1") } returns existing

        buttonUsageRepository.recordUsage("book1", "page1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { mockButtonUsageDao.upsert(capture(statSlot)) }

        assertEquals(6L, statSlot.captured.usageCount)
    }

    @Test
    fun `recordUsage updates label from current button config`() = runTest {
        val existing = ButtonUsageStat(
            bookId = "book1",
            buttonConfigId = "btn-1",
            pageId = "page1",
            label = "Old Label",
            actionJson = "{}",
            usageCount = 3,
            lastUsedAt = 1000L
        )
        coEvery { mockButtonUsageDao.getStatForButton("book1", "btn-1") } returns existing

        buttonUsageRepository.recordUsage("book1", "page1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { mockButtonUsageDao.upsert(capture(statSlot)) }

        assertEquals("Ja", statSlot.captured.label)
    }

    @Test
    fun `getTopActions delegates to dao`() = runTest {
        val stats = listOf(
            ButtonUsageStat("book1", "btn-1", "page1", "Ja", "{}", 10),
            ButtonUsageStat("book1", "btn-2", "page1", "Nein", "{}", 5)
        )
        coEvery { mockButtonUsageDao.getTopButtons("book1", 5) } returns stats

        val result = buttonUsageRepository.getTopActions("book1", 5)

        assertEquals(2, result.size)
        assertEquals("Ja", result[0].label)
        assertEquals(10L, result[0].usageCount)
    }

    @Test
    fun `clearStats delegates to dao`() = runTest {
        buttonUsageRepository.clearStats("book1")

        coVerify { mockButtonUsageDao.clearStatsForBook("book1") }
    }

    @Test
    fun `recordUsage serializes action to JSON`() = runTest {
        coEvery { mockButtonUsageDao.getStatForButton("book1", "btn-1") } returns null

        buttonUsageRepository.recordUsage("book1", "page1", testButton, rows = 6, columns = 6, indexInPage = 0)

        val statSlot = slot<ButtonUsageStat>()
        coVerify { mockButtonUsageDao.upsert(capture(statSlot)) }

        // The actionJson should contain the serialized SpeakTextButtonAction
        assertTrue(statSlot.captured.actionJson.contains("SpeakTextButtonAction"))
    }

    @Test
    fun `recordUsage persists event in history and prunes`() = runTest {
        coEvery { mockSettingsRepository.statsRetentionDays } returns 90
        coEvery { mockButtonUsageDao.getStatForButton("book1", "btn-1") } returns null

        buttonUsageRepository.recordUsage("book1", "page1", testButton, 6, 6, 0)

        val historySlot = slot<ButtonUsageHistoryEntity>()
        coVerify { mockButtonUsageDao.insertHistoryEvent(capture(historySlot)) }
        assertEquals("btn-1", historySlot.captured.buttonId)
        assertEquals("Ja", historySlot.captured.label)

        coVerify { mockButtonUsageDao.pruneHistoryByTimestamp(any()) }
    }

    @Test
    fun `cleanupOldStats delegates to dao with safe threshold calculation`() = runTest {
        buttonUsageRepository.cleanupOldStats(90)

        val thresholdSlot = slot<Long>()
        coVerify { mockButtonUsageDao.pruneHistoryByTimestamp(capture(thresholdSlot)) }
        coVerify { mockButtonUsageDao.pruneStatsByTimestamp(capture(thresholdSlot)) }

        val diff = System.currentTimeMillis() - thresholdSlot.captured
        val expectedDiff = 90L * 24L * 60L * 60L * 1000L
        // Accept a small delta due to time passing during test execution
        assertTrue(Math.abs(diff - expectedDiff) < 1000)
    }

    @Test
    fun `cleanupOldStats with days=0 does nothing`() = runTest {
        buttonUsageRepository.cleanupOldStats(0)

        coVerify(exactly = 0) { mockButtonUsageDao.pruneHistoryByTimestamp(any()) }
        coVerify(exactly = 0) { mockButtonUsageDao.pruneStatsByTimestamp(any()) }
    }

    @Test
    fun `cleanupOldStats with negative days does nothing`() = runTest {
        buttonUsageRepository.cleanupOldStats(-1)

        coVerify(exactly = 0) { mockButtonUsageDao.pruneHistoryByTimestamp(any()) }
        coVerify(exactly = 0) { mockButtonUsageDao.pruneStatsByTimestamp(any()) }
    }

    @Test
    fun `getPredictiveButtons applies time decay to Markov successors`() = runTest {
        val bookId = "book1"
        val lastButtonId = "btn-last"
        val now = System.currentTimeMillis()

        // 40 days ago
        val timestampOld = now - (40L * 24 * 60 * 60 * 1000)
        // 1 hour ago
        val timestampRecent = now - (1L * 60 * 60 * 1000)

        // Construct events chronologically
        val chronologicalEvents = mutableListOf<ButtonUsageHistoryEntity>()

        // 2 recent transitions: btn-last followed by btn-recent
        chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampRecent - 100, label = "Last", actionType = "Speak", buttonId = lastButtonId, latitude = 40.0, longitude = 40.0))
        chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampRecent, label = "Recent", actionType = "Speak", buttonId = "btn-recent", latitude = 40.0, longitude = 40.0))

        chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampRecent + 900, label = "Last", actionType = "Speak", buttonId = lastButtonId, latitude = 40.0, longitude = 40.0))
        chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampRecent + 1000, label = "Recent", actionType = "Speak", buttonId = "btn-recent", latitude = 40.0, longitude = 40.0))

        // 5 old transitions: btn-last followed by btn-old
        for (i in 0 until 5) {
            chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampOld + i * 200, label = "Last", actionType = "Speak", buttonId = lastButtonId, latitude = 30.0, longitude = 30.0))
            chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = timestampOld + i * 200 + 100, label = "Old", actionType = "Speak", buttonId = "btn-old", latitude = 30.0, longitude = 30.0))
        }

        // Active last event (most recent)
        chronologicalEvents.add(ButtonUsageHistoryEntity(bookId = bookId, timestamp = now, label = "Last", actionType = "Speak", buttonId = lastButtonId, latitude = 40.0, longitude = 40.0))

        // Sort descending to mock database results
        val events = chronologicalEvents.sortedByDescending { it.timestamp }

        coEvery { mockButtonUsageDao.getRecentHistoryEvents(bookId, 1000) } returns events

        val predictions = buttonUsageRepository.getPredictiveButtons(bookId, limit = 3)

        val recentIndex = predictions.indexOf("btn-recent")
        val oldIndex = predictions.indexOf("btn-old")

        assertTrue("btn-recent should be found in predictions", recentIndex != -1)
        assertTrue("btn-old should be found in predictions", oldIndex != -1)
        assertTrue("btn-recent ($recentIndex) should rank before btn-old ($oldIndex)", recentIndex < oldIndex)
    }

    @Test
    fun `getPredictiveButtons gives high priority to context specific buttons via TF-IDF`() = runTest {
        val bookId = "book1"
        val now = System.currentTimeMillis()

        // Mock WiFiManager to return "School-WiFi"
        val mockWifiManager = mockk<android.net.wifi.WifiManager>(relaxed = true)
        val mockConnectionInfo = mockk<android.net.wifi.WifiInfo>(relaxed = true)
        io.mockk.every { mockContext.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) } returns mockWifiManager
        io.mockk.every { mockWifiManager.connectionInfo } returns mockConnectionInfo
        io.mockk.every { mockConnectionInfo.ssid } returns "\"School-WiFi\""
        io.mockk.every { mockConnectionInfo.networkId } returns 1

        // Deny location permission to skip LocationServices await hanging
        io.mockk.every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } returns android.content.pm.PackageManager.PERMISSION_DENIED

        // Mock favorites fallback to include both buttons
        coEvery { mockButtonUsageDao.getTopButtons(bookId, any()) } returns listOf(
            ButtonUsageStat(bookId, "btn-home", "page1", "Home", "{}", 10),
            ButtonUsageStat(bookId, "btn-school", "page1", "School", "{}", 2)
        )

        // Corpus:
        // "btn-home" clicked 10 times on "Home-WiFi" 3 days ago
        // "btn-school" clicked only 2 times on "School-WiFi"
        val events = mutableListOf<ButtonUsageHistoryEntity>()
        val threeDaysAgo = now - (3L * 24 * 60 * 60 * 1000)

        // 10 clicks for btn-home on Home-WiFi
        for (i in 0 until 10) {
            events.add(ButtonUsageHistoryEntity(
                bookId = bookId,
                timestamp = threeDaysAgo - i * 1000,
                label = "Home Button",
                actionType = "Speak",
                buttonId = "btn-home",
                wifiSsid = "Home-WiFi",
                latitude = 10.0,
                longitude = 10.0
            ))
        }

        // 2 clicks for btn-school on School-WiFi
        for (i in 0 until 2) {
            events.add(ButtonUsageHistoryEntity(
                bookId = bookId,
                timestamp = now - i * 1000,
                label = "School Button",
                actionType = "Speak",
                buttonId = "btn-school",
                wifiSsid = "School-WiFi",
                latitude = 20.0,
                longitude = 20.0
            ))
        }

        val sortedEvents = events.sortedByDescending { it.timestamp }
        coEvery { mockButtonUsageDao.getRecentHistoryEvents(bookId, 1000) } returns sortedEvents

        val predictions = buttonUsageRepository.getPredictiveButtons(bookId, limit = 3)

        val schoolIndex = predictions.indexOf("btn-school")
        val homeIndex = predictions.indexOf("btn-home")

        assertTrue("btn-school should be found in predictions", schoolIndex != -1)
        assertTrue("btn-home should be found in predictions", homeIndex != -1)
        assertTrue("btn-school ($schoolIndex) should rank before btn-home ($homeIndex)", schoolIndex < homeIndex)
    }
}
