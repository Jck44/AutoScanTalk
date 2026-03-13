package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserModeScreenState(
    val keepScreenOn: Boolean = false,
    val dimAmount: Float? = null,
    val isBlackOverlayVisible: Boolean = false
)

class ScreenManagementDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _screenState = MutableStateFlow(UserModeScreenState())
    val screenState: StateFlow<UserModeScreenState> = _screenState.asStateFlow()

    fun init(scope: CoroutineScope, isUserModeActiveFlow: StateFlow<Boolean>) {
        scope.launch {
            combine(
                isUserModeActiveFlow,
                settingsRepository.keepScreenOnUserModeFlow,
                settingsRepository.userModeScreenBehaviorFlow
            ) { isUserMode, keepOn, behavior ->
                if (isUserMode && keepOn) {
                    UserModeScreenState(
                        keepScreenOn = true,
                        dimAmount = if (behavior == "DIMMED") 0.01f else null,
                        isBlackOverlayVisible = behavior == "BLACK"
                    )
                } else {
                    UserModeScreenState()
                }
            }.collect { _screenState.value = it }
        }
    }
}
