@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.ActionConfigFields
import java.io.File

@Composable
fun ButtonSettingsTabContent(
    context: Context,
    state: ButtonConfigDialogState,
    pages: List<Page>,
    templates: List<PageTemplate>,
    defaultStartPageId: String?,
    featureGuard: FeatureGuard?,
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool>,
    spotifyPlaylists: List<SpotifyPlaylist>,
    availableHomeDevices: List<HomeDevice>,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    audioRecordingController: AudioRecordingController,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    onStopTts: (() -> Unit)?,
    isTtsElevenLabs: () -> Boolean,
    isTextCached: ((String) -> Boolean)?,
    onPrefetchText: ((String, () -> Unit) -> Unit)?,
    onSuggestLabel: ((ButtonConfig, onResult: (String) -> Unit) -> Unit)?,
    onRefreshHueCache: ((silentOnFailure: Boolean, onResult: (Boolean) -> Unit) -> Unit)?,
    onAvailableHomeDevicesChange: (List<HomeDevice>) -> Unit,
    // Spotify status
    isLoadingSpotifyPlaylists: Boolean,
    spotifyUserDisplayName: String?,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onLoadSpotifyPlaylists: () -> Unit,
    onAutoSave: () -> Unit,
    saveWithAction: (ButtonAction) -> Unit
) {
    val isElevenLabs = remember { isTtsElevenLabs() }
    val resolver = remember(context) { ActionTypeResolver(context) }

    var playingField by remember { mutableStateOf<String?>(null) }
    val labelPlayback = rememberTtsFieldPlayback(
        fieldName = "label",
        isElevenLabs = isElevenLabs,
        isTextCached = isTextCached,
        onPrefetchText = onPrefetchText,
        onStopTts = onStopTts,
        onPlayTts = onPlayTts,
        getPlayingField = { playingField },
        setPlayingField = { playingField = it },
        currentText = state.label
    )
    val spokenTextPlayback = rememberTtsFieldPlayback(
        fieldName = "spokenText",
        isElevenLabs = isElevenLabs,
        isTextCached = isTextCached,
        onPrefetchText = onPrefetchText,
        onStopTts = onStopTts,
        onPlayTts = onPlayTts,
        getPlayingField = { playingField },
        setPlayingField = { playingField = it },
        currentText = state.spokenText
    )
    val auditoryCueTextPlayback = rememberTtsFieldPlayback(
        fieldName = "auditoryCueText",
        isElevenLabs = isElevenLabs,
        isTextCached = isTextCached,
        onPrefetchText = onPrefetchText,
        onStopTts = onStopTts,
        onPlayTts = onPlayTts,
        getPlayingField = { playingField },
        setPlayingField = { playingField = it },
        currentText = state.auditoryCueText
    )

    ActionTypeDropdownSection(
        state = state,
        context = context,
        pages = pages,
        templates = templates,
        defaultStartPageId = defaultStartPageId,
        featureGuard = featureGuard,
        permissionLauncher = permissionLauncher,
        onNavigateToPage = onNavigateToPage,
        onCreatePage = onCreatePage,
        onDismiss = onDismiss,
        onAutoSave = onAutoSave
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    LabelFieldSection(
        state = state,
        context = context,
        pages = pages,
        onSuggestLabel = onSuggestLabel,
        labelPlayback = labelPlayback,
        playingField = playingField,
        onPlayTts = onPlayTts,
        onAutoSave = onAutoSave
    )

    SpokenTextSection(
        state = state,
        context = context,
        spokenTextPlayback = spokenTextPlayback,
        playingField = playingField,
        onPlayTts = onPlayTts,
        audioRecordingController = audioRecordingController,
        onAutoSave = onAutoSave
    )

    CueAndTogglesSection(
        state = state,
        featureGuard = featureGuard,
        auditoryCueTextPlayback = auditoryCueTextPlayback,
        playingField = playingField,
        onPlayTts = onPlayTts,
        onAutoSave = onAutoSave
    )

    ActionConfigFields(
        selectedActionType = resolver.getLabel(state.selectedActionType),
        pages = pages,
        templates = templates,
        targetPageId = state.targetPageId,
        onTargetPageIdChange = {
            state.targetPageId = it
            onAutoSave()
        },
        geminiPrompt = state.geminiPrompt,
        onGeminiPromptChange = {
            state.geminiPrompt = it
        },
        rank = state.rank,
        onRankChange = { newRank ->
            val currentConfigWithOldRank = state.buildConfig()
            val oldSuggest = getLocalLabelSuggestion(currentConfigWithOldRank, pages, context)
            
            state.rank = newRank
            
            val currentConfigWithNewRank = state.buildConfig()
            val newSuggest = getLocalLabelSuggestion(currentConfigWithNewRank, pages, context)
            
            if (state.label == oldSuggest || state.label.isBlank()) {
                state.label = newSuggest
            }
            onAutoSave()
        },
        predictionType = state.predictionType,
        onPredictionTypeChange = { 
            state.predictionType = it
            onAutoSave()
        },
        availableGeminiTools = availableGeminiTools,
        deviceActionType = state.deviceActionType,
        onDeviceActionTypeChange = {
            state.deviceActionType = it
            onAutoSave()
        },
        volumeValue = state.volumeValue,
        onVolumeValueChange = {
            state.volumeValue = it
        },
        contactName = state.contactName,
        onContactNameChange = {
            state.contactName = it
        },
        contactPhone = state.contactPhone,
        onContactPhoneChange = {
            state.contactPhone = it
        },
        onContactSelected = { name, phone ->
            state.contactName = name
            state.contactPhone = phone
            val updatedAction = ControlDeviceButtonAction(
                actionType = state.deviceActionType,
                volumeValue = state.volumeValue,
                contactName = name,
                contactPhone = phone,
                messageText = state.messageText,
                includeWeekday = state.includeWeekday,
                prefixText = state.prefixText.takeIf { it.isNotBlank() },
                suffixText = state.suffixText.takeIf { it.isNotBlank() },
                offsetValue = state.offsetValue.toIntOrNull() ?: 0,
                ignoreEmojis = state.ignoreEmojis
            )
            saveWithAction(updatedAction)
        },
        messageText = state.messageText,
        onMessageTextChange = {
            state.messageText = it
        },
        includeWeekday = state.includeWeekday,
        onIncludeWeekdayChange = {
            state.includeWeekday = it
            onAutoSave()
        },
        prefixText = state.prefixText,
        onPrefixTextChange = {
            state.prefixText = it
        },
        suffixText = state.suffixText,
        onSuffixTextChange = {
            state.suffixText = it
        },
        offsetValue = state.offsetValue,
        onOffsetValueChange = {
            state.offsetValue = it
        },
        ignoreEmojis = state.ignoreEmojis,
        onIgnoreEmojisChange = {
            state.ignoreEmojis = it
            onAutoSave()
        },
        smartHomeProvider = state.smartHomeProvider,
        onSmartHomeProviderChange = {
            state.smartHomeProvider = it
            onAutoSave()
        },
        smartHomeDeviceId = state.smartHomeDeviceId,
        onSmartHomeDeviceIdChange = {
            state.smartHomeDeviceId = it
        },
        smartHomeDeviceName = state.smartHomeDeviceName,
        onSmartHomeDeviceNameChange = {
            state.smartHomeDeviceName = it
        },
        smartHomeIntent = state.smartHomeIntent,
        onSmartHomeIntentChange = {
            state.smartHomeIntent = it
        },
        smartHomeValue = state.smartHomeValue,
        onSmartHomeValueChange = {
            state.smartHomeValue = it
        },
        availableHomeDevices = availableHomeDevices,
        isFetchingDevices = state.isFetchingDevices,
        onFetchDevices = {
            if (state.smartHomeProvider == SmartHomeProvider.PHILIPS_HUE) {
                if (onRefreshHueCache != null) {
                    state.isFetchingDevices = true
                    onRefreshHueCache(false) { success ->
                        state.isFetchingDevices = false
                    }
                }
            }
        },
        onNavigateToPage = onNavigateToPage,
        onCreatePage = onCreatePage,
        onDismissDialog = onDismiss,
        playShutterSound = state.geminiVisionPlayShutterSound,
        onPlayShutterSoundChange = { 
            state.geminiVisionPlayShutterSound = it
            onAutoSave()
        },
        mediaProvider = state.mediaProvider,
        onMediaProviderChange = {
            state.mediaProvider = it
            onAutoSave()
        },
        mediaContentUri = state.mediaContentUri,
        onMediaContentUriChange = {
            state.mediaContentUri = it
        },
        mediaContentName = state.mediaContentName,
        onMediaContentNameChange = {
            state.mediaContentName = it
        },
        mediaReturnToAppDelaySec = state.mediaReturnToAppDelaySec,
        onMediaReturnToAppDelaySecChange = {
            state.mediaReturnToAppDelaySec = it
        },
        mediaForcePlayViaMediaSession = state.mediaForcePlayViaMediaSession,
        onMediaForcePlayViaMediaSessionChange = {
            state.mediaForcePlayViaMediaSession = it
            onAutoSave()
        },
        spotifyPlaylists = spotifyPlaylists,
        isLoadingSpotifyPlaylists = isLoadingSpotifyPlaylists,
        spotifyUserDisplayName = spotifyUserDisplayName,
        onConnectSpotify = onConnectSpotify,
        onDisconnectSpotify = onDisconnectSpotify,
        onLoadSpotifyPlaylists = onLoadSpotifyPlaylists,
        onAutoSave = onAutoSave
    )
}
