package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.actions.*

@Composable
fun ActionConfigFields(
    selectedActionType: String,
    pages: List<Page>,
    templates: List<PageTemplate>,
    targetPageId: String,
    onTargetPageIdChange: (String) -> Unit,
    geminiPrompt: String,
    onGeminiPromptChange: (String) -> Unit,
    rank: Int,
    onRankChange: (Int) -> Unit,
    deviceActionType: DeviceActionType,
    onDeviceActionTypeChange: (DeviceActionType) -> Unit,
    volumeValue: String,
    onVolumeValueChange: (String) -> Unit,
    contactName: String,
    onContactNameChange: (String) -> Unit,
    contactPhone: String,
    onContactPhoneChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    // New fields for Date/Time
    includeWeekday: Boolean = false,
    onIncludeWeekdayChange: (Boolean) -> Unit = {},
    prefixText: String = "",
    onPrefixTextChange: (String) -> Unit = {},
    suffixText: String = "",
    onSuffixTextChange: (String) -> Unit = {},
    offsetValue: String = "0",
    onOffsetValueChange: (String) -> Unit = {},
    // Smart Home specific
    smartHomeProvider: SmartHomeProvider = SmartHomeProvider.GOOGLE_HOME,
    onSmartHomeProviderChange: (SmartHomeProvider) -> Unit = {},
    smartHomeDeviceId: String = "",
    onSmartHomeDeviceIdChange: (String) -> Unit = {},
    smartHomeDeviceName: String = "",
    onSmartHomeDeviceNameChange: (String) -> Unit = {},
    smartHomeIntent: String = "",
    onSmartHomeIntentChange: (String) -> Unit = {},
    smartHomeValue: String? = null,
    onSmartHomeValueChange: (String) -> Unit = {},
    availableHomeDevices: List<HomeDevice> = emptyList(),
    isFetchingDevices: Boolean = false,
    onFetchDevices: () -> Unit = {},

    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null,
    onDismissDialog: () -> Unit,
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    // Vision specific
    useCloud: Boolean = false,
    isCloudEnabled: Boolean = true,
    playShutterSound: Boolean = true,
    onUseCloudChange: (Boolean) -> Unit = {},
    onPlayShutterSoundChange: (Boolean) -> Unit = {}
) {
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeGeminiVision = stringResource(R.string.button_action_gemini_vision)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeDevice = stringResource(R.string.button_action_control_device)
    val actionTypeSmartHome = stringResource(R.string.button_action_smart_home)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    val dimensions = LocalDimensions.current

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
        when (selectedActionType) {
            actionTypeNavigate -> {
                NavigationActionFields(
                    navigateToPageId = targetPageId,
                    onPageSelected = onTargetPageIdChange,
                    availablePages = pages,
                    templates = templates,
                    onNavigateToPage = onNavigateToPage,
                    onCreatePage = onCreatePage,
                    onDismissDialog = onDismissDialog
                )
            }
            actionTypeGemini, actionTypeGeminiSearch -> {
                GeminiActionFields(
                    prompt = geminiPrompt,
                    onPromptChanged = onGeminiPromptChange,
                    availableTools = availableGeminiTools
                )
            }
            actionTypeGeminiNano -> {
                GeminiNanoActionFields()
            }
            actionTypeGeminiVision -> {
                GeminiVisionActionFields(
                    prompt = geminiPrompt,
                    useCloud = useCloud,
                    isCloudEnabled = isCloudEnabled,
                    playShutterSound = playShutterSound,
                    onPromptChanged = onGeminiPromptChange,
                    onUseCloudChanged = onUseCloudChange,
                    onPlayShutterSoundChanged = onPlayShutterSoundChange
                )
            }
            actionTypeFrequent, actionTypeSmart -> {
                RankActionFields(
                    rank = rank.toString(),
                    onRankChanged = { onRankChange(it.toIntOrNull() ?: 1) }
                )
            }
            actionTypeDevice -> {
                ControlDeviceActionFields(
                    selectedType = deviceActionType,
                    onTypeSelected = onDeviceActionTypeChange,
                    volumeValue = volumeValue,
                    onVolumeValueChange = onVolumeValueChange,
                    contactName = contactName,
                    onContactSelected = { name, phone ->
                        onContactNameChange(name)
                        onContactPhoneChange(phone)
                    },
                    messageText = messageText,
                    onMessageTextChange = onMessageTextChange,
                    includeWeekday = includeWeekday,
                    onIncludeWeekdayChange = onIncludeWeekdayChange,
                    prefixText = prefixText,
                    onPrefixTextChange = onPrefixTextChange,
                    suffixText = suffixText,
                    onSuffixTextChange = onSuffixTextChange,
                    offsetValue = offsetValue,
                    onOffsetValueChange = onOffsetValueChange
                )
            }
            actionTypeSmartHome -> {
                SmartHomeActionFields(
                    selectedProvider = smartHomeProvider,
                    onProviderSelected = onSmartHomeProviderChange,
                    deviceId = smartHomeDeviceId,
                    onDeviceSelected = { device ->
                        onSmartHomeDeviceIdChange(device.id)
                        onSmartHomeDeviceNameChange(device.name)
                    },
                    deviceName = smartHomeDeviceName,
                    selectedIntent = smartHomeIntent,
                    onIntentSelected = onSmartHomeIntentChange,
                    value = smartHomeValue ?: "",
                    onValueChange = onSmartHomeValueChange,
                    devices = availableHomeDevices,
                    isFetching = isFetchingDevices,
                    onRefresh = onFetchDevices
                )
            }
            actionTypeWeather -> {
                WeatherActionFields()
            }
        }
    }
}
