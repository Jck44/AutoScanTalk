package com.andreas_kratzer.ghosttalk.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val mockedPrefsStore = mutableMapOf<String, String?>()

    @Before
    fun setup() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk()

        every { mockContext.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        
        every { mockPrefs.getString(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as String?
            mockedPrefsStore.getOrDefault(key, default)
        }
        every { mockPrefs.getLong(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as Long
            mockedPrefsStore.getOrDefault(key, default.toString())?.toLongOrNull() ?: default
        }
        every { mockPrefs.getBoolean(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as Boolean
            mockedPrefsStore.getOrDefault(key, default.toString())?.toBooleanStrictOrNull() ?: default
        }
        
        // Mock putString
        every { mockEditor.putString(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as String?
            mockedPrefsStore[key] = value
            mockEditor
        }
        
        // Mock putBoolean
        every { mockEditor.putBoolean(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as Boolean
            mockedPrefsStore[key] = value.toString()
            mockEditor
        }
        
        // Mock putLong
        every { mockEditor.putLong(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as Long
            mockedPrefsStore[key] = value.toString()
            mockEditor
        }
        
        // Mock contains
        every { mockPrefs.contains(any()) } answers {
            val key = args[0] as String
            mockedPrefsStore.containsKey(key)
        }

        every { mockEditor.apply() } returns Unit

        repository = SettingsRepository(mockContext)
    }

    @Test
    fun defaultStartPageId_initializesNull() = runBlocking {
        assertNull(repository.defaultStartPageId)
        assertNull(repository.defaultStartPageIdFlow.first())
    }

    @Test
    fun defaultStartPageId_savesAndEmitsValue() = runBlocking {
        val testPageId = "page-uuid-1234"
        repository.defaultStartPageId = testPageId

        assertEquals(testPageId, mockedPrefsStore["book-default_default_start_page_id"])
        assertEquals(testPageId, repository.defaultStartPageId)
        assertEquals(testPageId, repository.defaultStartPageIdFlow.first())
    }
    
    @Test
    fun defaultStartPageId_canBeResetToNull() = runBlocking {
        repository.defaultStartPageId = "temp-id"
        repository.defaultStartPageId = null
        
        assertNull(mockedPrefsStore["book-default_default_start_page_id"])
        assertNull(repository.defaultStartPageId)
        assertNull(repository.defaultStartPageIdFlow.first())
    }

    @Test
    fun ttsVoiceName_initializesNull() = runBlocking {
        assertNull(repository.ttsVoiceName)
        assertNull(repository.ttsVoiceNameFlow.first())
    }

    @Test
    fun ttsVoiceName_savesAndEmitsValue() = runBlocking {
        val testVoiceObjName = "de-de-x-deb-network"
        repository.ttsVoiceName = testVoiceObjName

        assertEquals(testVoiceObjName, mockedPrefsStore["book-default_tts_voice_name"])
        assertEquals(testVoiceObjName, repository.ttsVoiceName)
        assertEquals(testVoiceObjName, repository.ttsVoiceNameFlow.first())
    }

    @Test
    fun ttsVoiceName_canBeResetToNull() = runBlocking {
        repository.ttsVoiceName = "temp-voice"
        repository.ttsVoiceName = null

        assertNull(mockedPrefsStore["book-default_tts_voice_name"])
        assertNull(repository.ttsVoiceName)
        assertNull(repository.ttsVoiceNameFlow.first())
    }

    @Test
    fun actionLogsStorage_canBeResetToNull() = runBlocking {
        repository.actionLogsStorage = "temp-storage"
        repository.actionLogsStorage = null

        assertNull(mockedPrefsStore["book-default_action_logs_storage"])
        assertNull(repository.actionLogsStorage)
        assertNull(repository.actionLogsStorageFlow.first())
    }

    @Test
    fun persistActionLogs_initializesFalse() = runBlocking {
        every { mockPrefs.getBoolean("persist_action_logs", false) } returns false
        assertEquals(false, repository.persistActionLogs)
        assertEquals(false, repository.persistActionLogsFlow.first())
    }

    @Test
    fun persistActionLogs_savesAndEmitsValue() = runBlocking {
        repository.persistActionLogs = true

        assertEquals("true", mockedPrefsStore["book-default_persist_action_logs"])
        assertEquals(true, repository.persistActionLogs)
        assertEquals(true, repository.persistActionLogsFlow.first())
    }

    @Test
    fun actionLogsStorage_initializesNull() = runBlocking {
        assertNull(repository.actionLogsStorage)
        assertNull(repository.actionLogsStorageFlow.first())
    }

    @Test
    fun actionLogsStorage_savesAndEmitsValue() = runBlocking {
        val testStorage = "[\"action1\"]"
        repository.actionLogsStorage = testStorage

        assertEquals(testStorage, mockedPrefsStore["book-default_action_logs_storage"])
        assertEquals(testStorage, repository.actionLogsStorage)
        assertEquals(testStorage, repository.actionLogsStorageFlow.first())
    }

    @Test
    fun switchActivationKey_initializesSpace() = runBlocking {
        every { mockPrefs.getString("switch_activation_key", "Space") } returns "Space"
        assertEquals("Space", repository.switchActivationKey)
        assertEquals("Space", repository.switchActivationKeyFlow.first())
    }

    @Test
    fun switchActivationKey_savesAndEmitsValue() = runBlocking {
        val testKey = "Enter"
        repository.switchActivationKey = testKey

        assertEquals(testKey, mockedPrefsStore["book-default_switch_activation_key"])
        assertEquals(testKey, repository.switchActivationKey)
        assertEquals(testKey, repository.switchActivationKeyFlow.first())
    }

    @Test
    fun volumeKeysActivate_initializesFalse() = runBlocking {
        every { mockPrefs.getBoolean("volume_keys_activate", false) } returns false
        assertEquals(false, repository.volumeKeysActivate)
        assertEquals(false, repository.volumeKeysActivateFlow.first())
    }

    @Test
    fun volumeKeysActivate_savesAndEmitsValue() = runBlocking {
        repository.volumeKeysActivate = true

        assertEquals("true", mockedPrefsStore["book-default_volume_keys_activate"])
        assertEquals(true, repository.volumeKeysActivate)
        assertEquals(true, repository.volumeKeysActivateFlow.first())
    }

    @Test
    fun syncIntervalMinutes_initializes15() = runBlocking {
        every { mockPrefs.getLong("sync_interval_minutes", 15L) } returns 15L
        assertEquals(15L, repository.syncIntervalMinutes)
    }

    @Test
    fun syncIntervalMinutes_savesAndEmitsValue() = runBlocking {
        repository.syncIntervalMinutes = 30L

        assertEquals("30", mockedPrefsStore["book-default_sync_interval_minutes"])
        assertEquals(30L, repository.syncIntervalMinutes)
    }

    @Test
    fun syncMode_initializesTwoWay() = runBlocking {
        every { mockPrefs.getString("sync_mode", "TWO_WAY") } returns "TWO_WAY"
        assertEquals("TWO_WAY", repository.syncMode)
    }

    @Test
    fun syncMode_savesAndEmitsValue() = runBlocking {
        val testMode = "BACKUP_ONLY"
        repository.syncMode = testMode

        assertEquals(testMode, mockedPrefsStore["book-default_sync_mode"])
        assertEquals(testMode, repository.syncMode)
    }
}
