package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AudioDeviceManagerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockAudioManager = mockk<AudioManager>(relaxed = true)
    private lateinit var audioDeviceManager: AudioDeviceManager

    @Before
    fun setup() {
        every { mockContext.getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
        audioDeviceManager = AudioDeviceManager(mockContext)
    }

    @Test
    fun `getAvailableOutputDevices filters and maps devices correctly`() {
        val speaker = mockk<AudioDeviceInfo>()
        every { speaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        every { speaker.productName } returns "Internal Speaker"
        every { speaker.id } returns 1
        every { speaker.address } returns ""

        val bluetooth = mockk<AudioDeviceInfo>()
        every { bluetooth.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        every { bluetooth.productName } returns "BT Headset"
        every { bluetooth.id } returns 2
        every { bluetooth.address } returns "00:11:22:33:44:55"

        val telephony = mockk<AudioDeviceInfo>()
        every { telephony.type } returns AudioDeviceInfo.TYPE_TELEPHONY

        every { mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(speaker, bluetooth, telephony)

        val devices = audioDeviceManager.getAvailableOutputDevices()

        // Telephony should be filtered out
        assertEquals(2, devices.size)
        
        // Speaker mapping
        val speakerModel = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        assertNotNull(speakerModel)
        assertEquals("Internal Speaker (Lautsprecher)", speakerModel?.name)
        
        // Bluetooth mapping
        val btModel = devices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        assertEquals("BT Headset (Bluetooth (Media))", btModel?.name)
        assertEquals("2|00:11:22:33:44:55", btModel?.address)
    }

    @Test
    fun `getAudioDeviceInfo finds device by ID prefix`() {
        val speaker = mockk<AudioDeviceInfo>()
        every { speaker.id } returns 1
        every { speaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        every { speaker.productName } returns "Speaker"
        every { speaker.address } returns ""

        every { mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(speaker)

        val result = audioDeviceManager.getAudioDeviceInfo("1|type_2_Speaker")
        assertEquals(speaker, result)
    }

    @Test
    fun `getBuiltInSpeaker returns speaker or first device`() {
        val bt = mockk<AudioDeviceInfo>()
        every { bt.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        
        val speaker = mockk<AudioDeviceInfo>()
        every { speaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        every { mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(bt, speaker)

        val result = audioDeviceManager.getBuiltInSpeaker()
        assertEquals(speaker, result)
    }
}
