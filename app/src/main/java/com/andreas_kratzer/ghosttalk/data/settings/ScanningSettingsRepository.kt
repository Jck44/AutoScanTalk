package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_AUTO_START_SCANNING
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_BLUETOOTH_DELAY
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_DEFAULT_SCAN_PATTERN
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_HOLDING_TIME_MILLIS
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_RESUME_SCANNING_FROM_START
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SCAN_DELAY_MILLIS
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SWITCH_ACTIVATION_KEY
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_VOLUME_KEYS_ACTIVATE
import kotlinx.coroutines.flow.StateFlow

class ScanningSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _autoStartScanning = BooleanSetting(KEY_AUTO_START_SCANNING, true)
    private val _scanDelay = LongSetting(KEY_SCAN_DELAY_MILLIS, 3000L)
    private val _resumeScanningFromStart = BooleanSetting(KEY_RESUME_SCANNING_FROM_START, true)
    private val _holdingTimeMillis = LongSetting(KEY_HOLDING_TIME_MILLIS, 250L)
    private val _switchActivationKey = NonNullStringSetting(KEY_SWITCH_ACTIVATION_KEY, "~3")
    private val _volumeKeysActivate = BooleanSetting(KEY_VOLUME_KEYS_ACTIVATE, false)
    private val _defaultScanPattern = NonNullStringSetting(KEY_DEFAULT_SCAN_PATTERN, "linear")
    private val _bluetoothDelay = LongSetting(KEY_BLUETOOTH_DELAY, 100L, isScoped = false)

    val autoStartScanningFlow = _autoStartScanning.flow
    val scanDelayFlow = _scanDelay.flow
    val resumeScanningFromStartFlow = _resumeScanningFromStart.flow
    val holdingTimeMillisFlow = _holdingTimeMillis.flow
    val switchActivationKeyFlow = _switchActivationKey.flow
    val volumeKeysActivateFlow = _volumeKeysActivate.flow
    val defaultScanPatternFlow = _defaultScanPattern.flow
    val bluetoothDelayFlow = _bluetoothDelay.flow

    var autoStartScanning: Boolean
        get() = _autoStartScanning.value
        set(value) { _autoStartScanning.value = value }

    var scanDelayMillis: Long
        get() = _scanDelay.value
        set(value) { _scanDelay.value = value }

    var resumeScanningFromStart: Boolean
        get() = _resumeScanningFromStart.value
        set(value) { _resumeScanningFromStart.value = value }

    var holdingTimeMillis: Long
        get() = _holdingTimeMillis.value
        set(value) { _holdingTimeMillis.value = value }

    var switchActivationKey: String
        get() = _switchActivationKey.value
        set(value) { _switchActivationKey.value = value }

    var volumeKeysActivate: Boolean
        get() = _volumeKeysActivate.value
        set(value) { _volumeKeysActivate.value = value }

    var defaultScanPattern: String
        get() = _defaultScanPattern.value
        set(value) { _defaultScanPattern.value = value }

    var bluetoothDelay: Long
        get() = _bluetoothDelay.value
        set(value) { _bluetoothDelay.value = value }

    override fun refresh() {
        _autoStartScanning.refresh()
        _scanDelay.refresh()
        _resumeScanningFromStart.refresh()
        _holdingTimeMillis.refresh()
        _switchActivationKey.refresh()
        _volumeKeysActivate.refresh()
        _defaultScanPattern.refresh()
        _bluetoothDelay.refresh()
    }
}
