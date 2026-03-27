package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.tts.VoiceUtils
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val selectedLanguage by viewModel.selectedLanguageTag.collectAsState("default")
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState(null)
    val availableVoices by viewModel.availableVoices.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val cachedAudioDevices by viewModel.cachedAudioDevices.collectAsState()
    val selectedTtsAddress by viewModel.selectedTtsAudioDeviceAddress.collectAsState(null)
    val selectedCuesAddress by viewModel.selectedCuesAudioDeviceAddress.collectAsState(null)

    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedTtsDevice by remember { mutableStateOf(false) }
    var expandedCuesDevice by remember { mutableStateOf(false) }

    LaunchedEffect(expandedLanguage, expandedTtsDevice, expandedCuesDevice) {
        if ((expandedLanguage && availableLanguages.isEmpty()) || 
            (expandedTtsDevice && availableAudioDevices.isEmpty()) ||
            (expandedCuesDevice && availableAudioDevices.isEmpty())) {
            viewModel.refresh()
        }
    }

    val dimensions = LocalDimensions.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        if (!isGlobal) {
            val ttsEngine by viewModel.ttsEngine.collectAsState("google")
            val elevenLabsModel by viewModel.elevenLabsModel.collectAsState("eleven_multilingual_v2")
            
            PreferenceCategory(stringResource(R.string.settings_category_voice), modifier = Modifier.weight(1f)) {
                // Engine Select
                val engineOptions = listOf(
                    "google" to R.string.settings_tts_engine_google,
                    "elevenlabs" to R.string.settings_tts_engine_elevenlabs
                ).map { (id, resId) ->
                    stringResource(resId) to { viewModel.setTtsEngine(id) }
                }

                val currentEngineLabel = when (ttsEngine) {
                    "elevenlabs" -> stringResource(R.string.settings_tts_engine_elevenlabs)
                    else -> stringResource(R.string.settings_tts_engine_google)
                }

                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_tts_engine),
                    selectedOption = currentEngineLabel,
                    options = engineOptions
                )

                if (ttsEngine == "elevenlabs") {
                    val modelOptions = listOf(
                        "eleven_multilingual_v2" to R.string.settings_elevenlabs_model_multilingual,
                        "eleven_turbo_v2_5" to R.string.settings_elevenlabs_model_turbo,
                        "eleven_flash_v2_5" to R.string.settings_elevenlabs_model_flash
                    ).map { (id, resId) ->
                        stringResource(resId) to { viewModel.setElevenLabsModel(id) }
                    }

                    val currentModelLabel = when (elevenLabsModel) {
                        "eleven_turbo_v2_5" -> stringResource(R.string.settings_elevenlabs_model_turbo)
                        "eleven_flash_v2_5" -> stringResource(R.string.settings_elevenlabs_model_flash)
                        else -> stringResource(R.string.settings_elevenlabs_model_multilingual)
                    }

                    com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                        label = stringResource(R.string.settings_elevenlabs_model),
                        selectedOption = currentModelLabel,
                        options = modelOptions
                    )
                }

                // Language Select
                val context = androidx.compose.ui.platform.LocalContext.current
                val currentLangLabel = if (selectedLanguage == "default" || selectedLanguage.isNullOrEmpty()) {
                    "${stringResource(R.string.settings_system_default)} (${Locale.getDefault().displayName})"
                } else Locale.forLanguageTag(selectedLanguage!!).displayName

                val languageOptions = mutableListOf<Pair<String, () -> Unit>>()
                languageOptions.add(stringResource(R.string.settings_system_default) to { viewModel.setTtsLanguage("default") })
                
                // Ensure alphabetical sorting in UI
                availableLanguages.sortedBy { it.displayName }.forEach { locale ->
                    languageOptions.add(locale.displayName to { viewModel.setTtsLanguage(locale.toLanguageTag()) })
                }

                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_tts_language),
                    selectedOption = currentLangLabel,
                    options = languageOptions
                )

                // Voice Select - FILTERED by selected language
                val filteredVoices = remember(availableVoices, selectedLanguage, ttsEngine) {
                    if (ttsEngine == "elevenlabs") {
                        // ElevenLabs voices are currently multilingual or not strictly tied to system locales in our mapping
                        availableVoices
                    } else {
                        val targetLocale = if (selectedLanguage == "default" || selectedLanguage.isNullOrEmpty()) {
                            Locale.getDefault()
                        } else {
                            Locale.forLanguageTag(selectedLanguage!!)
                        }
                        availableVoices.filter { it.locale.language == targetLocale.language }
                    }
                }

                val voiceGroups = remember(filteredVoices) {
                    filteredVoices.map { it.name }
                        .distinctBy { VoiceUtils.formatVoiceName(it) }
                        .map { VoiceUtils.formatVoiceName(it) }
                        .sorted() // Sort base names alphabetically
                }

                val voiceLabel = if (selectedVoiceName.isNullOrEmpty()) {
                    stringResource(R.string.settings_voice_default)
                } else {
                    val currentVoice = availableVoices.find { it.id == selectedVoiceName }
                    if (currentVoice != null) {
                        val baseName = VoiceUtils.formatVoiceName(currentVoice.name)
                        val groupIndex = voiceGroups.indexOf(baseName)
                        VoiceUtils.formatVoiceDisplay(context, currentVoice, maxOf(0, groupIndex))
                    } else {
                        VoiceUtils.formatVoiceName(selectedVoiceName!!)
                    }
                }

                val voiceOptions = mutableListOf<Pair<String, () -> Unit>>()
                voiceOptions.add(Pair(stringResource(R.string.settings_voice_default), { viewModel.setTtsVoice(null) }))
                
                // Sort voices alphabetically by their display name
                filteredVoices.map { voice ->
                    val baseName = VoiceUtils.formatVoiceName(voice.name)
                    val groupIndex = voiceGroups.indexOf(baseName)
                    val display = VoiceUtils.formatVoiceDisplay(context, voice, maxOf(0, groupIndex))
                    display to voice.id
                }
                .sortedBy { it.first }
                .forEach { (display, voiceId) ->
                    voiceOptions.add(Pair(display, { viewModel.setTtsVoice(voiceId) }))
                }

                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_select_voice),
                    selectedOption = voiceLabel,
                    options = voiceOptions
                )
            }

            PreferenceCategory(stringResource(R.string.settings_category_audio_hardware), modifier = Modifier.weight(1f)) {
                // TTS Audio Device Select
                val ttsOptions = mutableListOf<Pair<String, () -> Unit>>()
                ttsOptions.add(stringResource(R.string.settings_audio_default) to { viewModel.setTtsAudioDevice(null) })
                val activeDeviceIds = availableAudioDevices.mapNotNull { it.address.split("|").getOrNull(1) }.toSet()
                
                // Add currently available devices
                availableAudioDevices.forEach { device ->
                    ttsOptions.add(device.name to { viewModel.setTtsAudioDevice(device.address) })
                }
                
                // Add cached devices that are not currently available
                cachedAudioDevices.forEach { (persistentId, name) ->
                    if (!activeDeviceIds.contains(persistentId)) {
                        val displayName = "$name (Offline)"
                        val fallbackAddress = "0|$persistentId" // Dummy ID, persistentId is used for lookup later
                        ttsOptions.add(displayName to { viewModel.setTtsAudioDevice(fallbackAddress) })
                    }
                }

                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_audio_tts),
                    selectedOption = viewModel.getResolvedDeviceName(selectedTtsAddress),
                    options = ttsOptions
                )

                // Cues Audio Device Select
                val cuesOptions = mutableListOf<Pair<String, () -> Unit>>()
                cuesOptions.add(stringResource(R.string.settings_audio_default) to { viewModel.setCuesAudioDevice(null) })
                
                // Add currently available devices
                availableAudioDevices.forEach { device ->
                    cuesOptions.add(device.name to { viewModel.setCuesAudioDevice(device.address) })
                }
                
                // Add cached devices that are not currently available
                cachedAudioDevices.forEach { (persistentId, name) ->
                    if (!activeDeviceIds.contains(persistentId)) {
                        val displayName = "$name (Offline)"
                        val fallbackAddress = "0|$persistentId"
                        cuesOptions.add(displayName to { viewModel.setCuesAudioDevice(fallbackAddress) })
                    }
                }

                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_audio_cues),
                    selectedOption = viewModel.getResolvedDeviceName(selectedCuesAddress),
                    options = cuesOptions
                )
            }
        }
    }
}
