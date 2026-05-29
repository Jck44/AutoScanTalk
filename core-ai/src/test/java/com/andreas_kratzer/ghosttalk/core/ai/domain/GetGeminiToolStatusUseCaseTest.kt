package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetGeminiToolStatusUseCaseTest {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var useCase: GetGeminiToolStatusUseCase

    @Before
    fun setup() {
        googleAuthManager = mockk()
        settingsRepository = mockk(relaxed = true)
        geminiUseCase = mockk()
        
        useCase = GetGeminiToolStatusUseCase(geminiUseCase, googleAuthManager, settingsRepository)
    }

    @Test
    fun `invoke returns correct status map based on auth`() {
        // Given
        val mockStatus = mapOf("tool1" to GeminiUseCase.ToolStatus.AVAILABLE)
        every { googleAuthManager.userEmail } returns MutableStateFlow<String?>("test@example.com")
        every { geminiUseCase.getToolStatus(true) } returns mockStatus

        // When
        val result = useCase()

        // Then
        assertEquals(mockStatus, result)
    }
}
