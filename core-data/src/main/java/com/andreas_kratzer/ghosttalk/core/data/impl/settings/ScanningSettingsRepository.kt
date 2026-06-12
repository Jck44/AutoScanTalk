package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_AUTO_START_SCANNING
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BLUETOOTH_DELAY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_DEFAULT_SCAN_PATTERN
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_HOLDING_TIME_MILLIS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_RESUME_SCANNING_FROM_START
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SCAN_DELAY_MILLIS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SWITCH_ACTIVATION_KEY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_VOCAL_SWITCH_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_VOLUME_KEYS_ACTIVATE
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ScanningSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager,
    private val voiceSettingsRepository: Provider<VoiceSettingsRepository>
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), ScanningSettings {

    override var cuesAudioDeviceAddress: String?
        get() = voiceSettingsRepository.get().cuesAudioDeviceAddress
        set(value) { voiceSettingsRepository.get().cuesAudioDeviceAddress = value }
    override val cuesAudioDeviceAddressFlow: StateFlow<String?>
        get() = voiceSettingsRepository.get().cuesAudioDeviceAddressFlow

    init {
        // Migration/Cleanup: remove the deprecated static_row_scan_pattern key if it exists
        val keysToRemove = mutableListOf<String>()
        prefs.all.keys.forEach { key ->
            if (key.endsWith("static_row_scan_pattern")) {
                keysToRemove.add(key)
            }
        }
        if (keysToRemove.isNotEmpty()) {
            prefs.edit {
                keysToRemove.forEach { remove(it) }
            }
        }
    }

    private val _autoStartScanning = BooleanSetting(KEY_AUTO_START_SCANNING, true)
    private val _scanDelay = LongSetting(KEY_SCAN_DELAY_MILLIS, 3000L)
    private val _resumeScanningFromStart = BooleanSetting(KEY_RESUME_SCANNING_FROM_START, true)
    private val _holdingTimeMillis = LongSetting(KEY_HOLDING_TIME_MILLIS, 250L)
    private val _switchActivationKey = NonNullStringSetting(KEY_SWITCH_ACTIVATION_KEY, "~3", isScoped = false)
    private val _volumeKeysActivate = BooleanSetting(KEY_VOLUME_KEYS_ACTIVATE, false)
    private val _defaultScanPattern = NonNullStringSetting(KEY_DEFAULT_SCAN_PATTERN, "linear")
    private val _bluetoothDelay = LongSetting(KEY_BLUETOOTH_DELAY, 100L, isScoped = false)
    private val _limitScanCycles = BooleanSetting(SettingsConstants.KEY_LIMIT_SCAN_CYCLES, false)
    private val _scanCycleLimit = IntSetting(SettingsConstants.KEY_SCAN_CYCLE_LIMIT, 2)
    private val _blockVolumeKeys = BooleanSetting(SettingsConstants.KEY_BLOCK_VOLUME_KEYS, false, isScoped = false)
    private val _speakerVolume = IntSetting(SettingsConstants.KEY_SPEAKER_VOLUME, 100, isScoped = false)
    private val _headphoneVolume = IntSetting(SettingsConstants.KEY_HEADPHONE_VOLUME, 100, isScoped = false)
    private val _staticRowEnabled = BooleanSetting(SettingsConstants.KEY_STATIC_ROW_ENABLED, false)
    private val _lateClickThreshold = LongSetting(SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, 250L)
    private val _vocalSwitchEnabled = BooleanSetting(KEY_VOCAL_SWITCH_ENABLED, false)

    override val autoStartScanningFlow = _autoStartScanning.flow
    override val scanDelayFlow = _scanDelay.flow
    override val resumeScanningFromStartFlow = _resumeScanningFromStart.flow
    override val holdingTimeMillisFlow = _holdingTimeMillis.flow
    override val switchActivationKeyFlow = _switchActivationKey.flow
    override val volumeKeysActivateFlow = _volumeKeysActivate.flow
    override val defaultScanPatternFlow = _defaultScanPattern.flow
    override val bluetoothDelayFlow = _bluetoothDelay.flow
    override val limitScanCyclesFlow = _limitScanCycles.flow
    override val scanCycleLimitFlow = _scanCycleLimit.flow
    val blockVolumeKeysFlow = _blockVolumeKeys.flow
    val speakerVolumeFlow = _speakerVolume.flow
    val headphoneVolumeFlow = _headphoneVolume.flow
    override val staticRowEnabledFlow = _staticRowEnabled.flow
    override val lateClickThresholdFlow = _lateClickThreshold.flow
    val vocalSwitchEnabledFlow = _vocalSwitchEnabled.flow

    override var autoStartScanning: Boolean by _autoStartScanning
    override var scanDelayMillis: Long by _scanDelay
    override var resumeScanningFromStart: Boolean by _resumeScanningFromStart
    override var holdingTimeMillis: Long by _holdingTimeMillis
    override var switchActivationKey: String by _switchActivationKey
    override var volumeKeysActivate: Boolean by _volumeKeysActivate
    override var defaultScanPattern: String by _defaultScanPattern
    override var bluetoothDelay: Long by _bluetoothDelay
    override var limitScanCycles: Boolean by _limitScanCycles
    override var scanCycleLimit: Int by _scanCycleLimit
    var blockVolumeKeys: Boolean by _blockVolumeKeys
    var speakerVolume: Int by _speakerVolume
    var headphoneVolume: Int by _headphoneVolume
    override var staticRowEnabled: Boolean by _staticRowEnabled
    override var lateClickThresholdMillis: Long by _lateClickThreshold
    var vocalSwitchEnabled: Boolean by _vocalSwitchEnabled


    override fun refresh() {
        _autoStartScanning.refresh()
        _scanDelay.refresh()
        _resumeScanningFromStart.refresh()
        _holdingTimeMillis.refresh()
        _switchActivationKey.refresh()
        _volumeKeysActivate.refresh()
        _defaultScanPattern.refresh()
        _bluetoothDelay.refresh()
        _limitScanCycles.refresh()
        _scanCycleLimit.refresh()
        _blockVolumeKeys.refresh()
        _speakerVolume.refresh()
        _headphoneVolume.refresh()
        _staticRowEnabled.refresh()
        _lateClickThreshold.refresh()
        _vocalSwitchEnabled.refresh()
    }
}
