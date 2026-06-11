package com.andreas_kratzer.ghosttalk.ui.pages

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ButtonTemplateDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SuggestionsDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.TtsPreviewDelegate
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GridEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pageManagementDelegate: PageManagementDelegate
    private lateinit var buttonTemplateDelegate: ButtonTemplateDelegate
    private lateinit var suggestionsDelegate: SuggestionsDelegate
    private lateinit var ttsPreviewDelegate: TtsPreviewDelegate
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var viewModel: GridEditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        pageManagementDelegate = mockk(relaxed = true)
        buttonTemplateDelegate = mockk(relaxed = true)
        suggestionsDelegate = mockk(relaxed = true)
        ttsPreviewDelegate = mockk(relaxed = true)
        actionExecutor = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        geminiUseCase = mockk(relaxed = true)

        viewModel = GridEditorViewModel(
            pageManagementDelegate = pageManagementDelegate,
            buttonTemplateDelegate = buttonTemplateDelegate,
            suggestionsDelegate = suggestionsDelegate,
            ttsPreviewDelegate = ttsPreviewDelegate,
            actionExecutor = actionExecutor,
            settingsRepository = settingsRepository,
            geminiUseCase = geminiUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `suggestButtonLabel forwards to suggestionsDelegate`() = runTest {
        val config = ButtonConfig(label = "Test")
        viewModel.suggestButtonLabel(config) { }
        verify { suggestionsDelegate.suggestButtonLabel(config, any()) }
    }

    @Test
    fun `suggestRowName forwards to suggestionsDelegate`() = runTest {
        viewModel.suggestRowName("itemId", 0) { }
        verify { suggestionsDelegate.suggestRowName("itemId", 0, any()) }
    }
}
