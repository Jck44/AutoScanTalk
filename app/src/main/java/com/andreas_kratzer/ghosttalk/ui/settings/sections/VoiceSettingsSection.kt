package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.util.VoiceUtils
import java.util.Locale

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
    var expandedVoice by remember { mutableStateOf(false) }
    var expandedTtsDevice by remember { mutableStateOf(false) }
    var expandedCuesDevice by remember { mutableStateOf(false) }

    LaunchedEffect(expandedLanguage, expandedTtsDevice, expandedCuesDevice) {
        if ((expandedLanguage && availableLanguages.isEmpty()) || 
            (expandedTtsDevice && availableAudioDevices.isEmpty()) ||
            (expandedCuesDevice && availableAudioDevices.isEmpty())) {
            viewModel.refresh()
        }
    }

    if (!isGlobal) {
        PreferenceCategory(stringResource(R.string.settings_category_voice)) {
            // Language Select
            Box(modifier = Modifier.fillMaxWidth()) {
                val label = if (selectedLanguage == "default" || selectedLanguage.isNullOrEmpty()) {
                    "${stringResource(R.string.settings_system_default)} (${Locale.getDefault().displayName})"
                } else Locale.forLanguageTag(selectedLanguage!!).displayName
                
                SettingsClickableItem(
                    label = stringResource(R.string.settings_tts_language),
                    value = label,
                    onClick = { expandedLanguage = true }
                )
                DropdownMenu(expanded = expandedLanguage, onDismissRequest = { expandedLanguage = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_system_default), style = MaterialTheme.typography.bodyLarge) }, 
                        onClick = {
                            viewModel.setTtsLanguage("default")
                            expandedLanguage = false
                        }
                    )
                    availableLanguages.forEach { locale ->
                        DropdownMenuItem(
                            text = { Text(locale.displayName, style = MaterialTheme.typography.bodyLarge) }, 
                            onClick = {
                                viewModel.setTtsLanguage(locale.toLanguageTag())
                                expandedLanguage = false
                            }
                        )
                    }
                }
            }

            // Voice Select
            Box(modifier = Modifier.fillMaxWidth()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                
                // Group voices by base name to give them the same number
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
                
                SettingsClickableItem(
                    label = stringResource(R.string.settings_select_voice),
                    value = voiceLabel,
                    onClick = { expandedVoice = true }
                )
                DropdownMenu(expanded = expandedVoice, onDismissRequest = { expandedVoice = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_voice_default), style = MaterialTheme.typography.bodyLarge) }, 
                        onClick = {
                            viewModel.setTtsVoice(null)
                            expandedVoice = false
                        }
                    )
                    availableVoices.forEach { voice ->
                        val baseName = VoiceUtils.formatVoiceName(voice.name)
                        val groupIndex = voiceGroups.indexOf(baseName)
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = VoiceUtils.formatVoiceDisplay(context, voice, if (groupIndex >= 0) groupIndex else 0),
                                    style = MaterialTheme.typography.bodyLarge
                                ) 
                            },
                            onClick = {
                                viewModel.setTtsVoice(voice.name)
                                expandedVoice = false
                            }
                        )
                    }
                }
            }

            // (Sliders removed)
        }

        PreferenceCategory(stringResource(R.string.settings_category_audio_hardware)) {
            // TTS Audio Device Select
            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsClickableItem(
                    label = stringResource(R.string.settings_audio_tts),
                    value = viewModel.getResolvedDeviceName(selectedTtsAddress),
                    onClick = { expandedTtsDevice = true }
                )
                DropdownMenu(expanded = expandedTtsDevice, onDismissRequest = { expandedTtsDevice = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_audio_default), style = MaterialTheme.typography.bodyLarge) }, 
                        onClick = {
                            viewModel.setTtsAudioDevice(null)
                            expandedTtsDevice = false
                        }
                    )
                    availableAudioDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name, style = MaterialTheme.typography.bodyLarge) }, 
                            onClick = {
                                viewModel.setTtsAudioDevice(device.address)
                                expandedTtsDevice = false
                            }
                        )
                    }
                }
            }

            // Cues Audio Device Select
            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsClickableItem(
                    label = stringResource(R.string.settings_audio_cues),
                    value = viewModel.getResolvedDeviceName(selectedCuesAddress),
                    onClick = { expandedCuesDevice = true }
                )
                DropdownMenu(expanded = expandedCuesDevice, onDismissRequest = { expandedCuesDevice = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_audio_default), style = MaterialTheme.typography.bodyLarge) }, 
                        onClick = {
                            viewModel.setCuesAudioDevice(null)
                            expandedCuesDevice = false
                        }
                    )
                    availableAudioDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name, style = MaterialTheme.typography.bodyLarge) }, 
                            onClick = {
                                viewModel.setCuesAudioDevice(device.address)
                                expandedCuesDevice = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (isGlobal) {
        // Global hardware settings moved to book-bound section
    }
}

// VolumeSlider removed
