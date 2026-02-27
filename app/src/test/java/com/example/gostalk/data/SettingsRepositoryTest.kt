package com.example.gostalk.data

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

        every { mockContext.getSharedPreferences("gostalk_settings", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        
        every { mockPrefs.getString(any(), any()) } answers {
            val key = args[0] as String
            val default = args[1] as String?
            mockedPrefsStore.getOrDefault(key, default)
        }
        every { mockPrefs.getLong(any(), any()) } returns 1000L
        every { mockPrefs.getBoolean(any(), any()) } returns true
        
        // Mock putString
        every { mockEditor.putString(any(), any()) } answers {
            val key = args[0] as String
            val value = args[1] as String?
            mockedPrefsStore[key] = value
            mockEditor
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

        assertEquals(testPageId, mockedPrefsStore["default_start_page_id"])
        assertEquals(testPageId, repository.defaultStartPageId)
        assertEquals(testPageId, repository.defaultStartPageIdFlow.first())
    }
    
    @Test
    fun defaultStartPageId_canBeResetToNull() = runBlocking {
        repository.defaultStartPageId = "temp-id"
        repository.defaultStartPageId = null
        
        assertNull(mockedPrefsStore["default_start_page_id"])
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

        assertEquals(testVoiceObjName, mockedPrefsStore["tts_voice_name"])
        assertEquals(testVoiceObjName, repository.ttsVoiceName)
        assertEquals(testVoiceObjName, repository.ttsVoiceNameFlow.first())
    }

    @Test
    fun ttsVoiceName_canBeResetToNull() = runBlocking {
        repository.ttsVoiceName = "temp-voice"
        repository.ttsVoiceName = null

        assertNull(mockedPrefsStore["tts_voice_name"])
        assertNull(repository.ttsVoiceName)
        assertNull(repository.ttsVoiceNameFlow.first())
    }
}
