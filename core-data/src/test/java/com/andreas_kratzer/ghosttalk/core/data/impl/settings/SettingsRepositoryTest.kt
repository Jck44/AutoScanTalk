package com.andreas_kratzer.ghosttalk.core.data.impl.settings

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

    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val mockedPrefsStore = mutableMapOf<String, String?>()

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockContext.applicationContext } returns mockContext
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
        every { mockPrefs.getStringSet(any(), any()) } answers {
            val key = args[0] as String
            @Suppress("UNCHECKED_CAST")
            val default = args[1] as Set<String>?
            mockedPrefsStore[key]?.split(",")?.toSet() ?: default
        }
        every { mockPrefs.getFloat(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as Float
            mockedPrefsStore.getOrDefault(key, default.toString())?.toFloatOrNull() ?: default
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
        
        // Mock putFloat
        every { mockEditor.putFloat(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as Float
            mockedPrefsStore[key] = value.toString()
            mockEditor
        }
        
        // Mock putStringSet
        every { mockEditor.putStringSet(any(), any()) } answers {
            val key = args[0] as String
            @Suppress("UNCHECKED_CAST")
            val value = args[1] as Set<String>?
            mockedPrefsStore[key] = value?.joinToString(",")
            mockEditor
        }
        
        // Mock remove
        every { mockEditor.remove(any()) } answers {
            val key = args[0] as String
            mockedPrefsStore.remove(key)
            mockEditor
        }
        
        // Mock contains
        every { mockPrefs.contains(any()) } answers {
            val key = args[0] as String
            mockedPrefsStore.containsKey(key)
        }

        // Mock all
        every { mockPrefs.all } answers {
            mockedPrefsStore.toMap()
        }

        every { mockEditor.apply() } returns Unit

        repository = SettingsRepositoryImpl(mockContext)
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

        assertNull(mockedPrefsStore["action_logs_storage"])
        assertNull(repository.actionLogsStorage)
        assertNull(repository.actionLogsStorageFlow.first())
    }

    @Test
    fun persistActionLogs_initializesTrue() = runBlocking {
        every { mockPrefs.getBoolean("persist_action_logs", true) } returns true
        assertEquals(true, repository.persistActionLogs)
        assertEquals(true, repository.persistActionLogsFlow.first())
    }

    @Test
    fun persistActionLogs_savesAndEmitsValue() = runBlocking {
        repository.persistActionLogs = true

        assertEquals("true", mockedPrefsStore["persist_action_logs"])
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

        assertEquals(testStorage, mockedPrefsStore["action_logs_storage"])
        assertEquals(testStorage, repository.actionLogsStorage)
        assertEquals(testStorage, repository.actionLogsStorageFlow.first())
    }

    @Test
    fun switchActivationKey_initializesTilde3() = runBlocking {
        every { mockPrefs.getString("switch_activation_key", "~3") } returns "~3"
        assertEquals("~3", repository.switchActivationKey)
        assertEquals("~3", repository.switchActivationKeyFlow.first())
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

    @Test
    fun themeMode_initializesLight() = runBlocking {
        every { mockPrefs.getString("theme_mode", "LIGHT") } returns "LIGHT"
        assertEquals("LIGHT", repository.themeMode)
        assertEquals("LIGHT", repository.themeModeFlow.first())
    }

    @Test
    fun themeMode_savesAndEmitsValue() = runBlocking {
        val testTheme = "DARK"
        repository.themeMode = testTheme

        assertEquals(testTheme, mockedPrefsStore["theme_mode"])
        assertEquals(testTheme, repository.themeMode)
        assertEquals(testTheme, repository.themeModeFlow.first())
    }

    @Test
    fun holdingTimeMillis_savesAndEmitsValue() = runBlocking {
        repository.holdingTimeMillis = 500L
        assertEquals("500", mockedPrefsStore["book-default_holding_time_millis"])
    }

    @Test
    fun bluetoothDelay_initializes100() = runBlocking {
        every { mockPrefs.getLong("bluetooth_delay_ms", 100L) } returns 100L
        assertEquals(100L, repository.bluetoothDelay)
        assertEquals(100L, repository.bluetoothDelayFlow.first())
    }

    @Test
    fun useLocalGenerativeAi_initializesTrue() = runBlocking {
        every { mockPrefs.getBoolean("use_local_generative_ai", true) } returns true
        assertEquals(true, repository.useLocalGenerativeAi)
        assertEquals(true, repository.useLocalGenerativeAiFlow.first())
    }

    @Test
    fun useLocalGenerativeAi_savesAndEmitsValue() = runBlocking {
        repository.useLocalGenerativeAi = false
        assertEquals("false", mockedPrefsStore["book-default_use_local_generative_ai"])
    }

    @Test
    fun audioDeviceNamesCache_savesAndLoadsNames() = runBlocking {
        val mac = "00:11:22:33:44:55"
        val name = "Test Headphones"
        
        repository.saveDeviceName(mac, name)
        
        assertEquals(name, mockedPrefsStore["device_name_$mac"])
        assertEquals(name, repository.getDeviceName(mac))
    }

    @Test
    fun audioDeviceNamesCache_clearUnusedExcept() = runBlocking {
        repository.saveDeviceName("mac1", "Device 1")
        repository.saveDeviceName("mac2", "Device 2")
        repository.saveDeviceName("mac3", "Device 3")
        
        repository.cleanupDeviceCache(setOf("mac1", "mac3"))
        
        assertEquals("Device 1", repository.getDeviceName("mac1"))
        assertNull(repository.getDeviceName("mac2"))
        assertEquals("Device 3", repository.getDeviceName("mac3"))
    }
}
