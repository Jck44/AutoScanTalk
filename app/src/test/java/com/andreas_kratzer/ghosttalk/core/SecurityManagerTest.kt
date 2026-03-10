package com.andreas_kratzer.ghosttalk.core

import android.util.Base64
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityManagerTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var securityManager: SecurityManager
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(args[0] as String)
        }
        every { Base64.encodeToString(any<ByteArray>(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(args[0] as ByteArray)
        }

        settingsRepository = mockk(relaxed = true)
        securityManager = SecurityManager(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unlock with correct hashed pin sets isUnlocked to true`() {
        val pin = "1234"
        val salt = securityManager.generateSalt()
        val hash = securityManager.hashPin(pin, salt)
        
        every { settingsRepository.securityPinHash } returns hash
        every { settingsRepository.securityPinSalt } returns salt
        
        val result = securityManager.unlock(pin)
        
        assertTrue(result)
        assertTrue(securityManager.isUnlocked.value)
    }

    @Test
    fun `unlock with legacy pin sets isUnlocked to true`() {
        val pin = "1234"
        every { settingsRepository.securityPinHash } returns ""
        every { settingsRepository.securityPinSalt } returns ""
        every { settingsRepository.securityPin } returns pin
        
        val result = securityManager.unlock(pin)
        
        assertTrue(result)
        assertTrue(securityManager.isUnlocked.value)
    }

    @Test
    fun `unlock with incorrect pin returns false and isUnlocked remains false`() {
        val pin = "1234"
        val salt = securityManager.generateSalt()
        val hash = securityManager.hashPin(pin, salt)
        
        every { settingsRepository.securityPinHash } returns hash
        every { settingsRepository.securityPinSalt } returns salt
        
        val result = securityManager.unlock("wrong")
        
        assertFalse(result)
        assertFalse(securityManager.isUnlocked.value)
    }

    @Test
    fun `lock sets isUnlocked to false`() {
        val pin = "1234"
        val salt = securityManager.generateSalt()
        val hash = securityManager.hashPin(pin, salt)
        
        every { settingsRepository.securityPinHash } returns hash
        every { settingsRepository.securityPinSalt } returns salt
        
        securityManager.unlock(pin)
        assertTrue(securityManager.isUnlocked.value)
        
        securityManager.lock()
        
        assertFalse(securityManager.isUnlocked.value)
    }

    @Test
    fun `isSecurityRequiredForDeletion returns true only if enabled and pin set`() {
        every { settingsRepository.isPinRequiredForDeletion } returns true
        every { settingsRepository.securityPinHash } returns "somehash"
        assertTrue(securityManager.isSecurityRequiredForDeletion())

        every { settingsRepository.securityPinHash } returns ""
        every { settingsRepository.securityPin } returns "1234"
        assertTrue(securityManager.isSecurityRequiredForDeletion())

        every { settingsRepository.isPinRequiredForDeletion } returns false
        assertFalse(securityManager.isSecurityRequiredForDeletion())

        every { settingsRepository.isPinRequiredForDeletion } returns true
        every { settingsRepository.securityPinHash } returns ""
        every { settingsRepository.securityPin } returns ""
        assertFalse(securityManager.isSecurityRequiredForDeletion())
    }

    @Test
    fun `isSecurityRequiredForEdit returns true only if enabled and pin set`() {
        every { settingsRepository.isSecurityRequiredForEdit } returns true
        every { settingsRepository.securityPin } returns "1234"
        assertTrue(securityManager.isSecurityRequiredForEdit())

        every { settingsRepository.isSecurityRequiredForEdit } returns false
        assertFalse(securityManager.isSecurityRequiredForEdit())

        every { settingsRepository.isSecurityRequiredForEdit } returns true
        every { settingsRepository.securityPin } returns ""
        assertFalse(securityManager.isSecurityRequiredForEdit())
    }

    @Test
    fun `isSecurityRequiredForSettings returns true only if enabled and pin set`() {
        every { settingsRepository.isSecurityRequiredForSettings } returns true
        every { settingsRepository.securityPin } returns "1234"
        assertTrue(securityManager.isSecurityRequiredForSettings())

        every { settingsRepository.isSecurityRequiredForSettings } returns false
        assertFalse(securityManager.isSecurityRequiredForSettings())

        every { settingsRepository.isSecurityRequiredForSettings } returns true
        every { settingsRepository.securityPin } returns ""
        assertFalse(securityManager.isSecurityRequiredForSettings())
    }
}
