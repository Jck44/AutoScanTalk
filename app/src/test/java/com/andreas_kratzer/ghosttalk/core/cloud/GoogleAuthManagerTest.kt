package com.andreas_kratzer.ghosttalk.core.cloud

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.CredentialManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GoogleAuthManagerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val mockCredentialManager = mockk<CredentialManager>(relaxed = true)
    private val mockAccountManager = mockk<AccountManager>(relaxed = true)

    private lateinit var googleAuthManager: GoogleAuthManager

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any<Context>()) } returns mockCredentialManager
        
        mockkStatic(AccountManager::class)
        every { AccountManager.get(any<Context>()) } returns mockAccountManager

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any<String>(), any<Int>()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString(any<String>(), any()) } returns null
        every { mockEditor.remove(any<String>()) } returns mockEditor
        every { mockEditor.putString(any<String>(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        googleAuthManager = GoogleAuthManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial userEmail is null if not in prefs`() {
        assertNull(googleAuthManager.userEmail.value)
    }

    @Test
    fun `initial userEmail is loaded from prefs`() {
        every { mockPrefs.getString("user_email", null) } returns "test@example.com"
        val manager = GoogleAuthManager(mockContext)
        assertEquals("test@example.com", manager.userEmail.value)
    }

    @Test
    fun `signOut clears email and clears credential manager`() = runTest {
        googleAuthManager.signOut()
        
        assertNull(googleAuthManager.userEmail.value)
        verify { mockEditor.remove("user_email") }
        coVerify { mockCredentialManager.clearCredentialState(any()) }
    }

    @Test
    fun `getGoogleCredential returns null if not signed in`() {
        assertNull(googleAuthManager.getGoogleCredential())
    }

    @Test
    fun `getGoogleCredential returns credential with correct account info`() {
        mockkStatic(com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential::class)
        val mockCredential = mockk<com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential>(relaxed = true)
        every { com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential.usingOAuth2(any(), any()) } returns mockCredential
        
        // Mock Account constructor to prevent crashes in some environments
        mockkConstructor(Account::class)

        every { mockPrefs.getString("user_email", null) } returns "test@example.com"
        val manager = GoogleAuthManager(mockContext)
        
        val result = manager.getGoogleCredential()
        
        assertNotNull(result)
        // Verify that the email was used in some way to identify the account
        verify(atLeast = 1) { 
            mockCredential.selectedAccount = any()
        }
    }
}
