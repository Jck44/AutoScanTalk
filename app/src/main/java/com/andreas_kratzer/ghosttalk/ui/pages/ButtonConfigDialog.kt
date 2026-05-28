package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsGroupedDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.DropdownGroup
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import java.io.File

@Composable
fun ButtonConfigDialog(
    buttonConfig: ButtonConfig,
    pages: List<Page>,
    templates: List<PageTemplate>,
    onSave: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit,
    onTest: (ButtonConfig) -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null,
    currentPageId: String? = null,
    isTextCached: ((String) -> Boolean)? = null,
    onPrefetchText: ((String, () -> Unit) -> Unit)? = null,
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
    onSaveAsTemplate: ((ButtonConfig) -> Unit)? = null
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(buttonConfig.label) }
    var spokenText by remember { mutableStateOf(buttonConfig.spokenText ?: "") }
    var spokenTextMode by remember { mutableStateOf(buttonConfig.spokenTextMode) }
    var audioFileNameState by remember { mutableStateOf(buttonConfig.audioFileName) }

    val audioRecorder = remember(context) { com.andreas_kratzer.ghosttalk.core.audio.AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val dir = context.filesDir.resolve("audio_recordings")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val recordingFile = File(dir, "audio_${buttonConfig.id}.ogg")
                audioRecorder.startRecording(recordingFile)
                isRecording = true
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler bei der Aufnahme: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, R.string.error_microphone_permission_missing, Toast.LENGTH_LONG).show()
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (!granted) {
            Toast.makeText(context, R.string.permission_location_denied_weather, Toast.LENGTH_LONG).show()
        }
    }
    var auditoryCueText by remember { 
        mutableStateOf((buttonConfig.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text ?: "") 
    }
    var isActive by remember { mutableStateOf(buttonConfig.isActive) }
    var playActionAsAuditoryCue by remember { mutableStateOf(buttonConfig.playActionAsAuditoryCue) }
    var playingField by remember { mutableStateOf<String?>(null) }

    val isElevenLabs = remember { isTtsElevenLabs() }

    // Label cache state
    var isLabelPrefetching by remember { mutableStateOf(false) }
    var isLabelCached by remember(label, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(label) ?: false) else false)
    }

    // SpokenText cache state
    var isSpokenTextPrefetching by remember { mutableStateOf(false) }
    var isSpokenTextCached by remember(spokenText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(spokenText) ?: false) else false)
    }

    // AuditoryCueText cache state
    var isAuditoryCueTextPrefetching by remember { mutableStateOf(false) }
    var isAuditoryCueTextCached by remember(auditoryCueText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(auditoryCueText) ?: false) else false)
    }


    DisposableEffect(Unit) {
        onDispose {
            onStopTts?.invoke()
            audioRecorder.stopRecording()
            mediaPlayer?.release()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Einstellungen", "Vorschau")

    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeDevice = stringResource(R.string.button_action_control_device)
    val actionTypeWeather = stringResource(R.string.button_action_weather)
    val actionTypeSmartHome = stringResource(R.string.button_action_smart_home)
    val actionTypeGeminiVision = "Gemini Vision (KI Auge)"
    val actionTypePrevious = stringResource(R.string.action_previous_action)

    var selectedActionType by remember {
        mutableStateOf(
            when (val action = buttonConfig.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGeminiNano
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> actionTypeGeminiVision
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is PreviousActionButtonAction -> actionTypePrevious
                is ControlDeviceButtonAction -> actionTypeDevice
                is WeatherButtonAction -> actionTypeWeather
                is SmartHomeButtonAction -> actionTypeSmartHome
                else -> actionTypeSpeak
            }
        )
    }

    // Navigation specific state
    var targetPageId by remember {
        mutableStateOf((buttonConfig.buttonAction as? NavigateToPageButtonAction)?.pageId ?: "")
    }

    // Gemini specific state
    var geminiPrompt by remember {
        mutableStateOf(
            when(val action = buttonConfig.buttonAction) {
                is GeminiButtonAction -> action.prompt
                is GeminiSearchButtonAction -> action.prompt
                is GeminiNanoButtonAction -> action.intent
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> action.prompt
                else -> ""
            }
        )
    }

    var geminiVisionUseCloud by remember {
        mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction)?.useCloud ?: false)
    }

    var geminiVisionPlayShutterSound by remember {
        mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction)?.playShutterSound ?: true)
    }

    // Frequent/Smart specific state
    var rank by remember {
        mutableIntStateOf(
            when(val action = buttonConfig.buttonAction) {
                is FrequentActionButtonAction -> action.rank
                is SmartPredictionButtonAction -> action.rank
                is PreviousActionButtonAction -> action.rank
                else -> 1
            }
        )
    }

    // Device control specific state
    var deviceActionType by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.actionType ?: DeviceActionType.READ_TIME)
    }
    var volumeValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.volumeValue ?: "50")
    }
    var contactName by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactName ?: "")
    }
    var contactPhone by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactPhone ?: "")
    }
    var messageText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.messageText ?: "")
    }
    var includeWeekday by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.includeWeekday ?: false)
    }
    var prefixText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.prefixText ?: "")
    }
    var suffixText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.suffixText ?: "")
    }
    var offsetValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.offsetValue?.toString() ?: "0")
    }

    // Smart Home specific state
    var smartHomeProvider by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.provider ?: SmartHomeProvider.GOOGLE_HOME)
    }
    var smartHomeDeviceId by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.deviceId ?: "")
    }
    var smartHomeDeviceName by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.deviceName ?: "")
    }
    var smartHomeIntent by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.intent ?: "")
    }
    var smartHomeValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.value ?: "")
    }
    
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
    var isFetchingDevices by remember { mutableStateOf(false) }

    LaunchedEffect(parsedCachedDevices) {
        if (parsedCachedDevices.isNotEmpty() || availableHomeDevices.isEmpty()) {
            availableHomeDevices = parsedCachedDevices
        }
    }

    LaunchedEffect(smartHomeProvider) {
        if (smartHomeProvider == SmartHomeProvider.PHILIPS_HUE && onRefreshHueCache != null) {
            onRefreshHueCache(true) { success ->
                // Silently refreshed cache if bridge is reachable
            }
        }
    }
    val scope = rememberCoroutineScope()

    val buildCurrentAction = {
        when (selectedActionType) {
            actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
            actionTypeGemini -> GeminiButtonAction(geminiPrompt)
            actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
            actionTypeGeminiNano -> GeminiNanoButtonAction(geminiPrompt)
            actionTypeGeminiVision -> com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(geminiPrompt, geminiVisionUseCloud, geminiVisionPlayShutterSound)
            actionTypeFrequent -> FrequentActionButtonAction(rank)
            actionTypePrevious -> PreviousActionButtonAction(rank)
            actionTypeSmart -> SmartPredictionButtonAction(rank)
            actionTypeWeather -> WeatherButtonAction()
            actionTypeDevice -> ControlDeviceButtonAction(
                actionType = deviceActionType,
                volumeValue = volumeValue,
                contactName = contactName,
                contactPhone = contactPhone,
                messageText = messageText,
                includeWeekday = includeWeekday,
                prefixText = prefixText.takeIf { it.isNotBlank() },
                suffixText = suffixText.takeIf { it.isNotBlank() },
                offsetValue = offsetValue.toIntOrNull() ?: 0
            )
            actionTypeSmartHome -> SmartHomeButtonAction(
                provider = smartHomeProvider,
                deviceId = smartHomeDeviceId,
                deviceName = smartHomeDeviceName,
                intent = smartHomeIntent,
                value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
            )
            else -> SpeakTextButtonAction()
        }
    }

    val handleAutoSave: () -> Unit = {
        if (label.isNotBlank()) {
            val action = buildCurrentAction()

            val config = buttonConfig.copy(
                label = label,
                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                spokenTextMode = spokenTextMode,
                audioFileName = audioFileNameState,
                auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                isActive = isActive,
                playActionAsAuditoryCue = playActionAsAuditoryCue,
                buttonAction = action
            )
            onSave(config)
        }
    }

    val saveWithAction: (ButtonAction) -> Unit = { action ->
        if (label.isNotBlank()) {
            val config = buttonConfig.copy(
                label = label,
                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                spokenTextMode = spokenTextMode,
                audioFileName = audioFileNameState,
                auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                isActive = isActive,
                playActionAsAuditoryCue = playActionAsAuditoryCue,
                buttonAction = action
            )
            onSave(config)
        }
    }

    fun startVoiceRecording() {
        try {
            val dir = context.filesDir.resolve("audio_recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val recordingFile = File(dir, "audio_${buttonConfig.id}.ogg")
            audioRecorder.startRecording(recordingFile)
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler bei der Aufnahme: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopVoiceRecording() {
        try {
            audioRecorder.stopRecording()
            isRecording = false
            audioFileNameState = "audio_${buttonConfig.id}.ogg"
            handleAutoSave()
            Toast.makeText(context, R.string.button_audio_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler beim Stoppen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun playRecording(file: File) {
        if (isPlayingAudio) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingAudio = false
            return
        }

        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlayingAudio = false
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
            mediaPlayer = player
            isPlayingAudio = true
        } catch (e: Exception) {
            android.util.Log.e("ButtonConfigDialog", "Error playing recording", e)
            Toast.makeText(context, "Fehler beim Abspielen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun handlePlayClick(
        fieldName: String,
        text: String,
        isCached: Boolean,
        setCached: (Boolean) -> Unit,
        setPrefetching: (Boolean) -> Unit,
        play: (String, () -> Unit) -> Unit
    ) {
        if (playingField == fieldName) {
            onStopTts?.invoke()
            playingField = null
        } else {
            onStopTts?.invoke()
            if (isElevenLabs && !isCached && text.isNotBlank()) {
                setPrefetching(true)
                onPrefetchText?.invoke(text) {
                    setPrefetching(false)
                    setCached(isTextCached?.invoke(text) ?: false)
                    playingField = fieldName
                    play(text) {
                        if (playingField == fieldName) {
                            playingField = null
                        }
                    }
                }
            } else {
                playingField = fieldName
                play(text) {
                    if (playingField == fieldName) {
                        playingField = null
                    }
                }
            }
        }
    }

    fun handleFocusLost(
        text: String,
        setCached: (Boolean) -> Unit,
        setPrefetching: (Boolean) -> Unit
    ) {
        handleAutoSave()
        if (isElevenLabs && text.isNotBlank() && isTextCached?.invoke(text) == false) {
            setPrefetching(true)
            onPrefetchText?.invoke(text) {
                setPrefetching(false)
                setCached(isTextCached(text))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 800.dp)
            .fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            val actionBadgeText = when (selectedActionType) {
                actionTypeNavigate -> "Nav"
                actionTypeGemini, actionTypeGeminiSearch, actionTypeGeminiNano, actionTypeGeminiVision -> "KI"
                actionTypeFrequent, actionTypePrevious, actionTypeSmart -> "Verlauf"
                actionTypeWeather -> "Wetter"
                actionTypeDevice -> "Gerät"
                actionTypeSmartHome -> "Home"
                else -> "Sprechen"
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
                        0 -> {                            SettingsEditTextItem(
                                label = stringResource(R.string.button_label_field),
                                value = label,
                                onValueChange = { label = it },
                                onFocusLost = {
                                    handleFocusLost(label, { isLabelCached = it }, { isLabelPrefetching = it })
                                },
                                isPlaying = playingField == "label",
                                isLoading = isLabelPrefetching,
                                playPauseIconTint = if (isLabelCached) MaterialTheme.colorScheme.primary else null,
                                onPlayPauseClick = onPlayTts?.let { play ->
                                    {
                                        handlePlayClick(
                                            fieldName = "label",
                                            text = label,
                                            isCached = isLabelCached,
                                            setCached = { isLabelCached = it },
                                            setPrefetching = { isLabelPrefetching = it },
                                            play = play
                                        )
                                    }
                                }
                            )
                            
                            Text(
                                text = stringResource(R.string.button_spoken_text_field),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                            androidx.compose.material3.OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalDimensions.current.paddingSmall),
                                colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                                    ) {
                                        SingleChoiceSegmentedButtonRow(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val modes = listOf(SpokenTextMode.TTS, SpokenTextMode.AUDIO)
                                            modes.forEachIndexed { index, mode ->
                                                SegmentedButton(
                                                    selected = spokenTextMode == mode,
                                                    onClick = { 
                                                        spokenTextMode = mode
                                                        handleAutoSave()
                                                    },
                                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                                    label = { 
                                                        Text(
                                                            text = if (mode == SpokenTextMode.TTS) {
                                                                stringResource(R.string.button_spoken_text_mode_tts)
                                                            } else {
                                                                stringResource(R.string.button_spoken_text_mode_audio)
                                                            },
                                                            maxLines = 1
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (spokenTextMode == SpokenTextMode.TTS) {
                                            SettingsEditTextItem(
                                                label = "",
                                                placeholder = stringResource(R.string.button_spoken_text_placeholder),
                                                value = spokenText,
                                                onValueChange = { spokenText = it },
                                                onFocusLost = {
                                                    handleFocusLost(spokenText, { isSpokenTextCached = it }, { isSpokenTextPrefetching = it })
                                                },
                                                isPlaying = playingField == "spokenText",
                                                isLoading = isSpokenTextPrefetching,
                                                playPauseIconTint = if (isSpokenTextCached) MaterialTheme.colorScheme.primary else null,
                                                onPlayPauseClick = onPlayTts?.let { play ->
                                                    {
                                                        handlePlayClick(
                                                            fieldName = "spokenText",
                                                            text = spokenText,
                                                            isCached = isSpokenTextCached,
                                                            setCached = { isSpokenTextCached = it },
                                                            setPrefetching = { isSpokenTextPrefetching = it },
                                                            play = play
                                                        )
                                                    }
                                                },
                                                borderless = true
                                            )
                                        } else {
                                            val audioFileExists = remember(audioFileNameState) {
                                                if (audioFileNameState.isNullOrBlank()) false
                                                else File(context.filesDir.resolve("audio_recordings"), audioFileNameState!!).exists()
                                            }

                                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                            val pulseAlpha by if (isRecording) {
                                                infiniteTransition.animateFloat(
                                                    initialValue = 0.4f,
                                                    targetValue = 1f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(durationMillis = 800, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "pulseAlpha"
                                                )
                                            } else {
                                                remember { mutableFloatStateOf(1f) }
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (isRecording) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.button_audio_recording),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    } else if (audioFileExists) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.button_audio_saved),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.button_audio_ready),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            if (isRecording) {
                                                                stopVoiceRecording()
                                                            } else {
                                                                val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                                if (hasMicPermission) {
                                                                    startVoiceRecording()
                                                                } else {
                                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isRecording) GhostTalkIcons.Stop else GhostTalkIcons.RecordVoiceOver,
                                                            contentDescription = null,
                                                            modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                                        )
                                                        Text(
                                                            text = if (isRecording) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_record)
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val file = File(context.filesDir.resolve("audio_recordings"), audioFileNameState ?: "")
                                                            playRecording(file)
                                                        },
                                                        enabled = audioFileExists && !isRecording,
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = if (isPlayingAudio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlayingAudio) GhostTalkIcons.Stop else Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                                        )
                                                        Text(
                                                            text = if (isPlayingAudio) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_play)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { showDeleteConfirmation = true },
                                                        enabled = audioFileExists && !isRecording,
                                                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = stringResource(R.string.button_audio_delete)
                                                        )
                                                    }
                                                }
                                            }

                                            if (showDeleteConfirmation) {
                                                AlertDialog(
                                                    onDismissRequest = { showDeleteConfirmation = false },
                                                    title = { Text(stringResource(R.string.button_audio_delete)) },
                                                    text = { Text(stringResource(R.string.button_audio_delete_confirm)) },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showDeleteConfirmation = false
                                                                val file = File(context.filesDir.resolve("audio_recordings"), audioFileNameState ?: "")
                                                                if (file.exists()) {
                                                                    file.delete()
                                                                }
                                                                audioFileNameState = null
                                                                handleAutoSave()
                                                                Toast.makeText(context, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                        ) {
                                                            Text(stringResource(R.string.button_audio_delete))
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showDeleteConfirmation = false }) {
                                                            Text(stringResource(CoreR.string.dialog_close))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsEditTextItem(
                                label = stringResource(R.string.button_auditory_cue_field),
                                value = auditoryCueText,
                                onValueChange = { auditoryCueText = it },
                                onFocusLost = {
                                    handleFocusLost(auditoryCueText, { isAuditoryCueTextCached = it }, { isAuditoryCueTextPrefetching = it })
                                },
                                isPlaying = playingField == "auditoryCueText",
                                isLoading = isAuditoryCueTextPrefetching,
                                playPauseIconTint = if (isAuditoryCueTextCached) MaterialTheme.colorScheme.primary else null,
                                onPlayPauseClick = onPlayTts?.let { play ->
                                    {
                                        handlePlayClick(
                                            fieldName = "auditoryCueText",
                                            text = auditoryCueText,
                                            isCached = isAuditoryCueTextCached,
                                            setCached = { isAuditoryCueTextCached = it },
                                            setPrefetching = { isAuditoryCueTextPrefetching = it },
                                            play = play
                                        )
                                    }
                                }
                            )

                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalDimensions.current.paddingSmall),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { 
                                                isActive = !isActive 
                                                handleAutoSave()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        androidx.compose.material3.Switch(
                                            checked = isActive,
                                            onCheckedChange = { 
                                                isActive = it
                                                handleAutoSave()
                                            },
                                            thumbContent = if (isActive) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)) }
                                            } else null
                                        )
                                        Text(
                                            text = stringResource(R.string.button_is_active_label),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(32.dp))
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { 
                                                playActionAsAuditoryCue = !playActionAsAuditoryCue
                                                handleAutoSave()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        androidx.compose.material3.Switch(
                                            checked = playActionAsAuditoryCue,
                                            onCheckedChange = { 
                                                playActionAsAuditoryCue = it
                                                handleAutoSave()
                                            }
                                        )
                                        Text(
                                            text = stringResource(R.string.button_play_as_cue_short),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            featureGuard?.let { guard ->
                                val currentAction = buttonConfig.buttonAction
                                val isActionEnabled = guard.isActionEnabled(currentAction)
                                if (!isActionEnabled) {
                                    val featureName = when (currentAction) {
                                        is GeminiButtonAction, is GeminiSearchButtonAction -> "Gemini Cloud"
                                        is GeminiNanoButtonAction -> "Gemini Nano"
                                        is SmartHomeButtonAction -> "Smart Home"
                                        is SmartPredictionButtonAction, is FrequentActionButtonAction -> "Smart Prediction"
                                        is WeatherButtonAction -> "Wetter"
                                        is ControlDeviceButtonAction -> {
                                            if (currentAction.actionType == com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.READ_NOTIFICATIONS) "Benachrichtigungen" else ""
                                        }
                                        else -> ""
                                    }
                                    if (featureName.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.feature_disabled_warning, featureName),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            val rawGroups = listOf(
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_BASIS to listOf(
                                    actionTypeSpeak to SpeakTextButtonAction(),
                                    actionTypeNavigate to NavigateToPageButtonAction()
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_KI_ASSISTENZ to listOf(
                                    actionTypeGemini to GeminiButtonAction(),
                                    actionTypeGeminiSearch to GeminiSearchButtonAction(),
                                    actionTypeGeminiNano to GeminiNanoButtonAction(),
                                    actionTypeGeminiVision to com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction()
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_GERAETE_SMART_HOME to listOf(
                                    actionTypeWeather to WeatherButtonAction(),
                                    actionTypeSmartHome to SmartHomeButtonAction(),
                                    actionTypeDevice to ControlDeviceButtonAction()
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_DYNAMISCHE_AKTIONEN to listOf(
                                    actionTypeFrequent to FrequentActionButtonAction(),
                                    actionTypePrevious to PreviousActionButtonAction(),
                                    actionTypeSmart to SmartPredictionButtonAction()
                                )
                            )

                            val dropdownGroups = rawGroups.map { (groupName, actionList) ->
                                val enabledItems = actionList.filter { (_, action) ->
                                    featureGuard?.isActionEnabled(action) ?: true
                                }.map { (label, action) ->
                                    label to {
                                        selectedActionType = label
                                        // Permission check for Weather
                                        if (action is WeatherButtonAction) {
                                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            if (!hasFine && !hasCoarse) {
                                                permissionLauncher.launch(
                                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                                )
                                            }
                                        }
                                    }
                                }
                                DropdownGroup(name = groupName, items = enabledItems)
                            }.filter { it.items.isNotEmpty() }

                            SettingsGroupedDropdownItem(
                                label = stringResource(R.string.button_action_label),
                                selectedOption = selectedActionType,
                                groups = dropdownGroups,
                                iconProvider = { actionType ->
                                    val icon = when (actionType) {
                                        actionTypeSpeak -> Icons.Default.PlayArrow
                                        actionTypeNavigate -> com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.ArrowForward
                                        actionTypeGemini, actionTypeGeminiSearch, actionTypeGeminiNano, actionTypeGeminiVision -> com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.AutoAwesome
                                        actionTypeWeather -> com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Cloud
                                        actionTypeSmartHome -> Icons.Default.Home
                                        actionTypeDevice -> Icons.Default.Settings
                                        actionTypeFrequent, actionTypePrevious, actionTypeSmart -> com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.History
                                        else -> null
                                    }
                                    if (icon != null) {
                                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onValueChangeFinished = handleAutoSave
                            )

                            ActionConfigFields(
                                selectedActionType = selectedActionType,
                                pages = pages,
                                templates = templates,
                                targetPageId = targetPageId,
                                onTargetPageIdChange = { targetPageId = it },
                                geminiPrompt = geminiPrompt,
                                onGeminiPromptChange = { geminiPrompt = it },
                                rank = rank,
                                onRankChange = { rank = it },
                                availableGeminiTools = availableGeminiTools,
                                deviceActionType = deviceActionType,
                                onDeviceActionTypeChange = { deviceActionType = it },
                                volumeValue = volumeValue,
                                onVolumeValueChange = { volumeValue = it },
                                contactName = contactName,
                                onContactNameChange = { contactName = it },
                                contactPhone = contactPhone,
                                onContactPhoneChange = { contactPhone = it },
                                onContactSelected = { name, phone ->
                                    contactName = name
                                    contactPhone = phone
                                    val updatedAction = ControlDeviceButtonAction(
                                        actionType = deviceActionType,
                                        volumeValue = volumeValue,
                                        contactName = name,
                                        contactPhone = phone,
                                        messageText = messageText,
                                        includeWeekday = includeWeekday,
                                        prefixText = prefixText.takeIf { it.isNotBlank() },
                                        suffixText = suffixText.takeIf { it.isNotBlank() },
                                        offsetValue = offsetValue.toIntOrNull() ?: 0
                                    )
                                    saveWithAction(updatedAction)
                                },
                                messageText = messageText,
                                onMessageTextChange = { messageText = it },
                                includeWeekday = includeWeekday,
                                onIncludeWeekdayChange = { includeWeekday = it },
                                prefixText = prefixText,
                                onPrefixTextChange = { prefixText = it },
                                suffixText = suffixText,
                                onSuffixTextChange = { suffixText = it },
                                offsetValue = offsetValue,
                                onOffsetValueChange = { offsetValue = it },
                                smartHomeProvider = smartHomeProvider,
                                onSmartHomeProviderChange = { smartHomeProvider = it },
                                smartHomeDeviceId = smartHomeDeviceId,
                                onSmartHomeDeviceIdChange = { smartHomeDeviceId = it },
                                smartHomeDeviceName = smartHomeDeviceName,
                                onSmartHomeDeviceNameChange = { smartHomeDeviceName = it },
                                smartHomeIntent = smartHomeIntent,
                                onSmartHomeIntentChange = { smartHomeIntent = it },
                                smartHomeValue = smartHomeValue,
                                onSmartHomeValueChange = { smartHomeValue = it },
                                availableHomeDevices = availableHomeDevices,
                                isFetchingDevices = isFetchingDevices,
                                onFetchDevices = {
                                    if (smartHomeProvider == SmartHomeProvider.PHILIPS_HUE) {
                                        if (onRefreshHueCache != null) {
                                            isFetchingDevices = true
                                            onRefreshHueCache(false) { success ->
                                                isFetchingDevices = false
                                            }
                                        } else if (philipsHueManager != null) {
                                            scope.launch {
                                                isFetchingDevices = true
                                                val list = philipsHueManager.getLocalLights(hueBridgeIp, hueUsername)
                                                if (list.isNotEmpty()) {
                                                    availableHomeDevices = list
                                                }
                                                isFetchingDevices = false
                                            }
                                        }
                                    }
                                },
                                onNavigateToPage = onNavigateToPage,
                                onCreatePage = onCreatePage,
                                onDismissDialog = onDismiss,
                                useCloud = geminiVisionUseCloud,
                                onUseCloudChange = { 
                                    geminiVisionUseCloud = it
                                    handleAutoSave()
                                },
                                isCloudEnabled = featureGuard?.isActionEnabled(GeminiButtonAction()) ?: true,
                                playShutterSound = geminiVisionPlayShutterSound,
                                onPlayShutterSoundChange = { 
                                    geminiVisionPlayShutterSound = it
                                    handleAutoSave()
                                },
                                onAutoSave = handleAutoSave
                            )
                        }
                        1 -> {
                            PreviewTabContent(
                                selectedActionType = selectedActionType,
                                spokenText = spokenText,
                                label = label,
                                geminiPrompt = geminiPrompt,
                                targetPageId = targetPageId,
                                deviceActionType = deviceActionType,
                                includeWeekday = includeWeekday,
                                offsetValue = offsetValue,
                                prefixText = prefixText,
                                suffixText = suffixText,
                                contactName = contactName,
                                contactPhone = contactPhone,
                                messageText = messageText,
                                smartHomeDeviceName = smartHomeDeviceName,
                                playActionAsAuditoryCue = playActionAsAuditoryCue,
                                auditoryCueText = auditoryCueText
                            )
                        }
                    }
                }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

            // Action Bar (Fixed at the bottom)
            DialogActionBar(
                                buttonConfig = buttonConfig,
                                label = label,
                                spokenText = spokenText,
                                spokenTextMode = spokenTextMode,
                                audioFileName = audioFileNameState,
                                buildCurrentAction = buildCurrentAction,
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

@Composable
private fun CacheStatusRow(
    textToCache: String,
    isTextCached: ((String) -> Boolean)?,
    onPrefetchText: ((String, () -> Unit) -> Unit)?
) {
    if (textToCache.isBlank()) return

    var isCached by remember(textToCache, isTextCached) { 
        mutableStateOf(isTextCached?.invoke(textToCache) ?: false) 
    }
    var isPrefetching by remember(textToCache) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isCached) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (isCached) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
            Text(
                text = if (isCached) "Im Cache (Offline verfügbar)" else "Nicht im Cache (Benötigt Internet)",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (isCached) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }
        
        if (!isCached && onPrefetchText != null) {
            if (isPrefetching) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                androidx.compose.material3.TextButton(
                    onClick = {
                        isPrefetching = true
                        onPrefetchText(textToCache) {
                            isPrefetching = false
                            isCached = isTextCached?.invoke(textToCache) ?: false
                        }
                    }
                ) {
                    Text("Jetzt cachen")
                }
            }
        }
    }
}

@Composable
private fun PreviewTabContent(
    selectedActionType: String,
    spokenText: String,
    label: String,
    geminiPrompt: String,
    targetPageId: String,
    deviceActionType: DeviceActionType,
    includeWeekday: Boolean,
    offsetValue: String,
    prefixText: String,
    suffixText: String,
    contactName: String,
    contactPhone: String,
    messageText: String,
    smartHomeDeviceName: String,
    playActionAsAuditoryCue: Boolean,
    auditoryCueText: String,
    actionTypeSpeak: String = stringResource(R.string.button_action_speak_text),
    actionTypeNavigate: String = stringResource(R.string.button_action_navigate_page),
    actionTypeGemini: String = stringResource(R.string.button_action_gemini),
    actionTypeGeminiSearch: String = stringResource(R.string.button_action_gemini_search),
    actionTypeGeminiNano: String = stringResource(R.string.button_action_gemini_nano),
    actionTypeGeminiVision: String = stringResource(R.string.button_action_gemini_vision),
    actionTypeWeather: String = stringResource(R.string.button_action_weather),
    actionTypeDevice: String = stringResource(R.string.button_action_control_device),
    actionTypeSmartHome: String = stringResource(R.string.button_action_smart_home)
) {
    val isSpeech = selectedActionType == actionTypeSpeak
    val speakTextToUse = if (isSpeech) {
        spokenText.takeIf { it.isNotBlank() } ?: label
    } else ""

    val speakDescription = remember(
        selectedActionType, spokenText, label, geminiPrompt, targetPageId,
        deviceActionType, includeWeekday, offsetValue, prefixText, suffixText,
        contactName, contactPhone, messageText, smartHomeDeviceName
    ) {
        when {
            isSpeech -> {
                if (spokenText.isNotBlank()) {
                    "🗣️ Text vorlesen:\n\"$spokenText\"\n\n(Eigener Sprechtext wird verwendet)"
                } else {
                    "🗣️ Text vorlesen (Fallback auf Label):\n\"$label\"\n\n(Da der Sprechtext leer ist, wird die Kachel-Beschriftung gesprochen)"
                }
            }
            else -> {
                when (selectedActionType) {
                    actionTypeNavigate -> {
                        if (spokenText.isNotBlank()) {
                            "🗣️ Feedback vorlesen:\n\"$spokenText\"\n\n➡️ Navigation:\nÖffnet danach die Seite \"$targetPageId\""
                        } else {
                            "➡️ Navigation:\nÖffnet die Seite \"$targetPageId\""
                        }
                    }
                    actionTypeGemini -> "✨ KI (Gemini Cloud):\nSendet Prompt \"$geminiPrompt\" an Gemini und liest die Antwort vor."
                    actionTypeGeminiSearch -> "🔍 KI Search:\nSucht Google nach \"$geminiPrompt\" ab und liest Zusammenfassung vor."
                    actionTypeGeminiNano -> "📱 KI Nano (Offline):\nVerarbeitet Intent \"$geminiPrompt\" lokal auf dem Gerät."
                    actionTypeGeminiVision -> "📷 KI Vision (Auge):\nAnalysiert Kamerabild und liest die Beschreibung vor."
                    actionTypeDevice -> {
                        val actionName = when (deviceActionType) {
                            DeviceActionType.READ_TIME -> "Uhrzeit vorlesen"
                            DeviceActionType.READ_DATE -> "Datum vorlesen"
                            DeviceActionType.READ_BATTERY -> "Batteriestand vorlesen"
                            DeviceActionType.READ_CALENDAR_ENTRIES -> "Kalender vorlesen"
                            DeviceActionType.SEND_MESSAGE -> "SMS senden"
                            DeviceActionType.START_CALL -> "Anruf starten"
                            DeviceActionType.VOLUME_MEDIA -> "Medien-Lautstärke ändern"
                            DeviceActionType.VOLUME_NOTIFICATION -> "Benachrichtigungs-Lautstärke ändern"
                            DeviceActionType.VOLUME_ALARM -> "Wecker-Lautstärke ändern"
                            DeviceActionType.VOLUME_CALL -> "Anruf-Lautstärke ändern"
                            DeviceActionType.STATUS_SILENT -> "Modus: Lautlos"
                            DeviceActionType.STATUS_VIBRATE -> "Modus: Vibration"
                            DeviceActionType.STATUS_LOUD -> "Modus: Laut"
                            DeviceActionType.MEDIA_PLAY_PAUSE -> "Musik abspielen/pausieren"
                            DeviceActionType.MEDIA_NEXT -> "Nächstes Lied abspielen"
                            DeviceActionType.MEDIA_PREVIOUS -> "Vorheriges Lied abspielen"
                            DeviceActionType.TOGGLE_SCANNING -> "Scannen pausieren/fortsetzen"
                            DeviceActionType.READ_NOTIFICATIONS -> "Benachrichtigungen vorlesen"
                            DeviceActionType.CLEAR_NOTIFICATIONS -> "Benachrichtigungen löschen"
                        }
                        val specificText = try {
                            val calendar = java.util.Calendar.getInstance()
                            val offsetInt = offsetValue.toIntOrNull() ?: 0
                            if (deviceActionType == DeviceActionType.READ_TIME) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.MINUTE, offsetInt)
                                }
                                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val timeString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"$prefix$timeString$suffix\""
                            } else if (deviceActionType == DeviceActionType.READ_DATE) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetInt)
                                }
                                val pattern = if (includeWeekday) "EEEE, dd. MMMM yyyy" else "dd. MMMM yyyy"
                                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                                val dateString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"$prefix$dateString$suffix\""
                            } else if (deviceActionType == DeviceActionType.READ_CALENDAR_ENTRIES) {
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"${prefix}[Termine]${suffix}\" (Liest $offsetInt Kalendereinträge vor)"
                            } else if (deviceActionType == DeviceActionType.READ_BATTERY) {
                                "\n\nGesprochener Text:\n\"Batteriestand ist bei 85 Prozent\""
                            } else if (deviceActionType == DeviceActionType.SEND_MESSAGE) {
                                "\n\nSendet SMS an $contactName:\n\"$messageText\""
                            } else if (deviceActionType == DeviceActionType.START_CALL) {
                                "\n\nRuft $contactName an ($contactPhone)"
                            } else {
                                ""
                            }
                        } catch (e: Exception) {
                            ""
                        }
                        "📱 Geräte-Funktion: $actionName$specificText"
                    }
                    actionTypeWeather -> "🌤️ Wetteransage:\nRuft aktuellen Wetterbericht ab und spricht ihn laut vor."
                    actionTypeSmartHome -> "🏠 Smart Home:\nSchaltet Gerät \"$smartHomeDeviceName\"."
                    else -> "🔄 Führt dynamische Aktion aus (Verlauf / Prediction)."
                }
            }
        }
    }

    val cueDescription = remember(playActionAsAuditoryCue, isSpeech, speakTextToUse, auditoryCueText, label) {
        when {
            playActionAsAuditoryCue -> {
                if (isSpeech) {
                    "🔊 Spielt Aktionstext leise vor (Cue):\n\"$speakTextToUse\"\n\n(Option \"Aktionstext direkt vorlesen\" ist aktiv. Spricht denselben Text wie beim Tippen, aber leise im Scanning-Kanal)"
                } else {
                    "🔊 Spielt Aktion leise vor (Cue):\nFührt die Aktion (z.B. Navigations-Beschreibung) leise im Scanning-Kanal aus."
                }
            }
            auditoryCueText.isNotBlank() -> {
                "🔊 Spricht leise (Benutzerdefinierter Cue):\n\"$auditoryCueText\"\n\n(Eigener Hinweistext wird verwendet)"
            }
            else -> {
                "🔊 Spricht leise (Fallback auf Label):\n\"$label\"\n\n(Da der Hinweistext leer ist, wird die Kachel-Beschriftung als Scanning-Cue verwendet)"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "📢 Laut sprechen / Aktion ausführen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = speakDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "🎧 Flüsterton / Scanning-Hinweis",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = cueDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DialogActionBar(
    buttonConfig: ButtonConfig,
    label: String,
    spokenText: String,
    spokenTextMode: SpokenTextMode,
    audioFileName: String?,
    buildCurrentAction: () -> ButtonAction,
    onDismiss: () -> Unit,
    onTest: (ButtonConfig) -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSaveAsTemplate: ((ButtonConfig) -> Unit)? = null,
    context: Context = androidx.compose.ui.platform.LocalContext.current
) {
    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val showTest = availableWidth > 420.dp
        val showMove = availableWidth > 550.dp
        val showDuplicate = availableWidth > 680.dp
        val showDelete = availableWidth > 810.dp

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            // Always show Close
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = 96.dp)
            ) {
                Text(stringResource(CoreR.string.dialog_close))
            }

            // Test Button
            if (showTest) {
                OutlinedButton(
                    onClick = {
                        val currentAction = buildCurrentAction()
                        onTest(buttonConfig.copy(
                            label = label,
                            spokenText = if (spokenText.isNotBlank()) spokenText else null,
                            spokenTextMode = spokenTextMode,
                            audioFileName = audioFileName,
                            buttonAction = currentAction
                        ))
                        Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.button_action_test))
                }
            }

            // Move Button
            if (showMove) {
                OutlinedButton(
                    onClick = onMove,
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Text(stringResource(R.string.button_action_move))
                }
            }

            // Duplicate Button
            if (showDuplicate) {
                OutlinedButton(
                    onClick = onDuplicate,
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.action_duplicate))
                }
            }

            // Delete Button
            if (showDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.widthIn(min = 96.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(CoreR.string.action_delete))
                }
            }

            // Overflow Menu
            val hasHiddenItems = !showTest || !showMove || !showDuplicate || !showDelete
            if (hasHiddenItems) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onSaveAsTemplate != null) {
                            DropdownMenuItem(
                                text = { Text("Als Vorlage speichern") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val currentAction = buildCurrentAction()
                                    onSaveAsTemplate(buttonConfig.copy(
                                        label = label,
                                        spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                        spokenTextMode = spokenTextMode,
                                        audioFileName = audioFileName,
                                        buttonAction = currentAction
                                    ))
                                }
                            )
                        }
                        if (!showTest) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.button_action_test)) },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val currentAction = buildCurrentAction()
                                    onTest(buttonConfig.copy(
                                        label = label,
                                        spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                        spokenTextMode = spokenTextMode,
                                        audioFileName = audioFileName,
                                        buttonAction = currentAction
                                    ))
                                    Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (!showMove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.button_action_move)) },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                }
                            )
                        }
                        if (!showDuplicate) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDuplicate()
                                }
                            )
                        }
                        if (!showDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(CoreR.string.action_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
