package com.andreas_kratzer.ghosttalk.feature.settings.domain


import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ToggleExperimentalFeatureUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ToggleExperimentalFeatureUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        useCase = ToggleExperimentalFeatureUseCase(settingsRepository)
    }


    @Test
    fun `invoke with SMART_PREDICTION updates isSmartPredictionEnabled`() {
        useCase(ExperimentalFeature.SMART_PREDICTION, true)
        verify { settingsRepository.isSmartPredictionEnabled = true }

        useCase(ExperimentalFeature.SMART_PREDICTION, false)
        verify { settingsRepository.isSmartPredictionEnabled = false }
    }
}
