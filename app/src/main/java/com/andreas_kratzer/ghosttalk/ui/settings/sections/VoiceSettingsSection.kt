package com.andreas_kratzer.ghosttalk.ui.settings.sections

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
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.util.VoiceUtils
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val selectedLanguage by viewModel.selectedLanguageTag.collectAsState("default")
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState(null)
    val availableVoices by viewModel.availableVoices.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
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
            PreferenceCategory(stringResource(R.string.settings_category_voice), modifier = Modifier.weight(1f)) {
                // Language Select
                val context = androidx.compose.ui.platform.LocalContext.current
                val currentLangLabel = if (selectedLanguage == "default" || selectedLanguage.isNullOrEmpty()) {
                    "${stringResource(R.string.settings_system_default)} (${Locale.getDefault().displayName})"
                } else Locale.forLanguageTag(selectedLanguage!!).displayName

                val languageOptions = mutableListOf<Pair<String, () -> Unit>>()
                languageOptions.add(stringResource(R.string.settings_system_default) to { viewModel.setTtsLanguage("default") })
                availableLanguages.forEach { locale ->
                    languageOptions.add(locale.displayName to { viewModel.setTtsLanguage(locale.toLanguageTag()) })
                }

                com.andreas_kratzer.ghosttalk.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_tts_language),
                    selectedOption = currentLangLabel,
                    options = languageOptions
                )

                // Voice Select
                val voiceGroups = remember(availableVoices) {
                    availableVoices.map { it.name }
                        .distinctBy { VoiceUtils.formatVoiceName(it) }
                        .map { VoiceUtils.formatVoiceName(it) }
                }

                val voiceLabel = if (selectedVoiceName.isNullOrEmpty()) {
                    stringResource(R.string.settings_voice_default)
                } else {
                    val currentVoice = availableVoices.find { it.name == selectedVoiceName }
                    if (currentVoice != null) {
                        val baseName = VoiceUtils.formatVoiceName(currentVoice.name)
                        val groupIndex = voiceGroups.indexOf(baseName)
                        VoiceUtils.formatVoiceDisplay(context, currentVoice, if (groupIndex >= 0) groupIndex else 0)
                    } else {
                        VoiceUtils.formatVoiceName(selectedVoiceName!!)
                    }
                }

                val voiceOptions = mutableListOf<Pair<String, () -> Unit>>()
                voiceOptions.add(stringResource(R.string.settings_voice_default) to { viewModel.setTtsVoice(null) })
                availableVoices.forEach { voice ->
                    val baseName = VoiceUtils.formatVoiceName(voice.name)
                    val groupIndex = voiceGroups.indexOf(baseName)
                    val display = VoiceUtils.formatVoiceDisplay(context, voice, if (groupIndex >= 0) groupIndex else 0)
                    voiceOptions.add(display to { viewModel.setTtsVoice(voice.name) })
                }

                com.andreas_kratzer.ghosttalk.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_select_voice),
                    selectedOption = voiceLabel,
                    options = voiceOptions
                )
            }

            PreferenceCategory(stringResource(R.string.settings_category_audio_hardware), modifier = Modifier.weight(1f)) {
                // TTS Audio Device Select
                val ttsOptions = mutableListOf<Pair<String, () -> Unit>>()
                ttsOptions.add(stringResource(R.string.settings_audio_default) to { viewModel.setTtsAudioDevice(null) })
                availableAudioDevices.forEach { device ->
                    ttsOptions.add(device.name to { viewModel.setTtsAudioDevice(device.address) })
                }

                com.andreas_kratzer.ghosttalk.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_audio_tts),
                    selectedOption = viewModel.getResolvedDeviceName(selectedTtsAddress),
                    options = ttsOptions
                )

                // Cues Audio Device Select
                val cuesOptions = mutableListOf<Pair<String, () -> Unit>>()
                cuesOptions.add(stringResource(R.string.settings_audio_default) to { viewModel.setCuesAudioDevice(null) })
                availableAudioDevices.forEach { device ->
                    cuesOptions.add(device.name to { viewModel.setCuesAudioDevice(device.address) })
                }

                com.andreas_kratzer.ghosttalk.ui.components.SettingsDropdownItem(
                    label = stringResource(R.string.settings_audio_cues),
                    selectedOption = viewModel.getResolvedDeviceName(selectedCuesAddress),
                    options = cuesOptions
                )
            }
        }
    }
}
