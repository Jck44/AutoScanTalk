package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.SpeechSettings
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.core.settings.*
import kotlinx.coroutines.flow.StateFlow

/**
 * A facade interface that combines all specialized settings interfaces.
 * This maintains compatibility with the existing application architecture
 * while allowing for a modular implementation behind the scenes.
 */
interface SettingsRepository : SecuritySettings, KeyEventSettings, AudioSettings, 
    ControlDeviceSettings, SpeechSettings, GenAiSettings, DatabaseSettings, 
    TtsSettings, ImportExportSettings, CloudSettings, ScanningSettings, FeatureSettings,
    GeneralSettings, UserSettings, AdvancedSettings, NotificationSettings {
    
    override var activeBookId: String
    override val activeBookIdFlow: StateFlow<String?>
}
