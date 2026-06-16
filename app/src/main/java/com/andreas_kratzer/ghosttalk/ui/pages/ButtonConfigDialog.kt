@file:Suppress("DEPRECATION", "UNUSED_PARAMETER")
package com.andreas_kratzer.ghosttalk.ui.pages

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.buttonstats.ButtonStatisticsTabContent
import com.andreas_kratzer.ghosttalk.ui.pages.components.ActionTypeId
import com.andreas_kratzer.ghosttalk.ui.pages.components.ActionTypeResolver
import com.andreas_kratzer.ghosttalk.ui.pages.components.rememberButtonConfigDialogState

@Composable
fun ButtonConfigDialog(
    buttonConfig: ButtonConfig,
    pages: List<Page>,
    templates: List<PageTemplate>,
    onSave: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit,
    onTest: (ButtonConfig) -> Unit,
    onMove: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null,
    currentPageId: String? = null,
    isTextCached: ((String) -> Boolean)? = null,
    onPrefetchText: ((String, () -> Unit) -> Unit)? = null,
    onSuggestLabel: ((ButtonConfig, onResult: (String) -> Unit) -> Unit)? = null,
    // AI Tools
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    // Philips Hue Support
    philipsHueManager: PhilipsHueManager? = null,
    hueBridgeIp: String = "",
    hueUsername: String = "",
    hueCachedDevices: String = "",
    onRefreshHueCache: ((silentOnFailure: Boolean, onResult: (Boolean) -> Unit) -> Unit)? = null,
    featureGuard: FeatureGuard? = null,
    onPlayTts: ((String, () -> Unit) -> Unit)? = null,
    onStopTts: (() -> Unit)? = null,
    isTtsElevenLabs: () -> Boolean = { false },
    spotifyPlaylists: List<com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist> = emptyList(),
    isLoadingSpotifyPlaylists: Boolean = false,
    spotifyUserDisplayName: String? = null,
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    onLoadSpotifyPlaylists: () -> Unit = {},
    onSaveAsTemplate: ((ButtonConfig) -> Unit)? = null,
    metrics: ButtonEffortMetrics? = null,
    historyEvents: List<ButtonUsageEvent> = emptyList(),
    recommendations: List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation> = emptyList(),
    loadMarkovSuccessors: suspend (String) -> List<Pair<String, Int>> = { emptyList() },
    onApplyRecommendation: ((com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation) -> Unit)? = null,
    defaultStartPageId: String? = null
) {
    val context = LocalContext.current
    val state = rememberButtonConfigDialogState(buttonConfig)

    val handleAutoSave: () -> Unit = {
        if (state.label.isNotBlank()) {
            onSave(state.buildConfig())
        }
    }

    val audioRecordingController = com.andreas_kratzer.ghosttalk.ui.pages.components.rememberAudioRecordingController(
        buttonConfigId = buttonConfig.id,
        state = state,
        onAutoSave = handleAutoSave
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (!granted) {
            Toast.makeText(context, R.string.permission_location_denied_weather, Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopTts?.invoke()
        }
    }

    var currentTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Einstellungen", "Vorschau", "Statistiken")

    val parsedCachedDevices = remember(hueCachedDevices) {
        val list = mutableListOf<HomeDevice>()
        if (hueCachedDevices.isNotBlank()) {
            try {
                val array = org.json.JSONArray(hueCachedDevices)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HomeDevice(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            type = obj.optString("type", "LIGHT")
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        list
    }

    var availableHomeDevices by remember { mutableStateOf<List<HomeDevice>>(parsedCachedDevices) }

    LaunchedEffect(parsedCachedDevices) {
        if (parsedCachedDevices.isNotEmpty() || availableHomeDevices.isEmpty()) {
            availableHomeDevices = parsedCachedDevices
        }
    }

    LaunchedEffect(state.smartHomeProvider, state.selectedActionType) {
        if (state.selectedActionType == ActionTypeId.PHILIPS_HUE && onRefreshHueCache != null) {
            onRefreshHueCache(true) { success ->
                // Silently refreshed cache if bridge is reachable
            }
        }
    }

    val saveWithAction: (ButtonAction) -> Unit = { action ->
        if (state.label.isNotBlank()) {
            val config = state.buildConfig().copy(buttonAction = action)
            onSave(config)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 800.dp)
            .fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            val actionBadgeText = when (state.selectedActionType) {
                ActionTypeId.NAVIGATE, ActionTypeId.NAVIGATE_BACK, ActionTypeId.NAVIGATE_TO_START_PAGE -> "Nav"
                ActionTypeId.GEMINI, ActionTypeId.GEMINI_SEARCH, ActionTypeId.GEMINI_VISION -> "KI"
                ActionTypeId.FREQUENT, ActionTypeId.PREVIOUS, ActionTypeId.SMART -> "Verlauf"
                ActionTypeId.WEATHER -> "Wetter"
                ActionTypeId.READ_NOTIFICATIONS, ActionTypeId.CLEAR_NOTIFICATIONS, ActionTypeId.SEND_MESSAGE, ActionTypeId.START_CALL, ActionTypeId.TOGGLE_AUTO_READ -> "Komm."
                ActionTypeId.SPOTIFY, ActionTypeId.YOUTUBE, ActionTypeId.YOUTUBE_MUSIC, ActionTypeId.AUDIBLE, ActionTypeId.MEDIA_PLAY_PAUSE, ActionTypeId.MEDIA_NEXT, ActionTypeId.MEDIA_PREVIOUS -> "Medien"
                ActionTypeId.PHILIPS_HUE, ActionTypeId.GOOGLE_HOME -> "Home"
                ActionTypeId.SPEAK -> "Sprechen"
                else -> "Gerät"
            }
            Text("[$actionBadgeText] Bearbeiten")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabTitles.size),
                            label = { Text(title, maxLines = 1) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.paddingSmall)
                ) {
                    when (currentTab) {
                        0 -> {
                            com.andreas_kratzer.ghosttalk.ui.pages.components.ButtonSettingsTabContent(
                                context = context,
                                state = state,
                                pages = pages,
                                templates = templates,
                                defaultStartPageId = defaultStartPageId,
                                featureGuard = featureGuard,
                                availableGeminiTools = availableGeminiTools,
                                spotifyPlaylists = spotifyPlaylists,
                                availableHomeDevices = availableHomeDevices,
                                permissionLauncher = permissionLauncher,
                                audioRecordingController = audioRecordingController,
                                onNavigateToPage = onNavigateToPage,
                                onCreatePage = onCreatePage,
                                onDismiss = onDismiss,
                                onPlayTts = onPlayTts,
                                onStopTts = onStopTts,
                                isTtsElevenLabs = isTtsElevenLabs,
                                isTextCached = isTextCached,
                                onPrefetchText = onPrefetchText,
                                onSuggestLabel = onSuggestLabel,
                                onRefreshHueCache = onRefreshHueCache,
                                isLoadingSpotifyPlaylists = isLoadingSpotifyPlaylists,
                                spotifyUserDisplayName = spotifyUserDisplayName,
                                onConnectSpotify = onConnectSpotify,
                                onDisconnectSpotify = onDisconnectSpotify,
                                onLoadSpotifyPlaylists = onLoadSpotifyPlaylists,
                                onAutoSave = handleAutoSave,
                                saveWithAction = saveWithAction
                            )
                        }
                        1 -> {
                            val resolver = remember(context) { ActionTypeResolver(context) }
                            PreviewTabContent(
                                selectedActionType = resolver.getLabel(state.selectedActionType),
                                spokenText = state.spokenText,
                                label = state.label,
                                geminiPrompt = state.geminiPrompt,
                                targetPageId = state.targetPageId,
                                deviceActionType = state.deviceActionType,
                                includeWeekday = state.includeWeekday,
                                offsetValue = state.offsetValue,
                                prefixText = state.prefixText,
                                suffixText = state.suffixText,
                                contactName = state.contactName,
                                contactPhone = state.contactPhone,
                                messageText = state.messageText,
                                smartHomeDeviceName = state.smartHomeDeviceName,
                                playActionAsAuditoryCue = state.playActionAsAuditoryCue,
                                auditoryCueText = state.auditoryCueText,
                                mediaProvider = state.mediaProvider,
                                mediaContentName = state.mediaContentName,
                                mediaReturnToAppDelaySec = state.mediaReturnToAppDelaySec,
                                rank = state.rank,
                                predictionType = state.predictionType
                            )
                        }
                        2 -> {
                            ButtonStatisticsTabContent(
                                metrics = metrics,
                                historyEvents = historyEvents,
                                recommendations = recommendations,
                                onApplyRecommendation = onApplyRecommendation,
                                buttonId = buttonConfig.id,
                                loadMarkovSuccessors = loadMarkovSuccessors,
                                allPages = pages,
                                onNavigateToPage = onNavigateToPage,
                                onDismissDialog = onDismiss
                            )
                        }
                    }
                }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

            // Action Bar (Fixed at the bottom)
            DialogActionBar(
                                buttonConfig = buttonConfig,
                                label = state.label,
                                spokenText = state.spokenText,
                                spokenTextMode = state.spokenTextMode,
                                audioFileName = state.audioFileName,
                                buildCurrentAction = { state.buildAction() },
                                onDismiss = onDismiss,
                                onTest = onTest,
                                onMove = onMove,
                                onDuplicate = onDuplicate,
                                onDelete = onDelete,
                                onSaveAsTemplate = onSaveAsTemplate
            )
        }
    },
    confirmButton = { },
    dismissButton = { }
)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
