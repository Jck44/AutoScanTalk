package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CALL_ANNOUNCEMENT_AS_CUE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CALL_AUTO_ENABLE_SPEAKERPHONE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CALL_DURATION_FEEDBACK_INTERVAL_SECONDS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CALL_HANG_UP_PRESSES_REQUIRED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_FILTER_CALLS_NOT_IN_CONTACTS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_ACTIVE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_INACTIVE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_INCOMING_CALL_DELAY_INACTIVE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_INCOMING_CALL_INTRO
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_INCOMING_CALL_SCAN_LIMIT_ACTIVE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_MAX_CALL_DURATION_SECONDS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_OUTGOING_CALL_INTRO
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SIMULATE_CALLS_ENABLED
import com.andreas_kratzer.ghosttalk.core.settings.CallSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), CallSettings {

    private val _maxCallDurationSeconds = IntSetting(KEY_MAX_CALL_DURATION_SECONDS, 0, isScoped = false)
    private val _callDurationFeedbackIntervalSeconds = IntSetting(KEY_CALL_DURATION_FEEDBACK_INTERVAL_SECONDS, 60, isScoped = false)
    private val _outgoingCallIntro = NonNullStringSetting(KEY_OUTGOING_CALL_INTRO, "", isScoped = false)
    private val _incomingCallIntro = NonNullStringSetting(KEY_INCOMING_CALL_INTRO, "", isScoped = false)
    private val _incomingCallScanLimitActive = IntSetting(KEY_INCOMING_CALL_SCAN_LIMIT_ACTIVE, 2, isScoped = false)
    private val _incomingCallAutoActionActive = NonNullStringSetting(KEY_INCOMING_CALL_AUTO_ACTION_ACTIVE, "NONE", isScoped = false)
    private val _incomingCallDelayInactive = IntSetting(KEY_INCOMING_CALL_DELAY_INACTIVE, 15, isScoped = false)
    private val _incomingCallAutoActionInactive = NonNullStringSetting(KEY_INCOMING_CALL_AUTO_ACTION_INACTIVE, "NONE", isScoped = false)
    private val _callAnnouncementAsCue = BooleanSetting(KEY_CALL_ANNOUNCEMENT_AS_CUE, true, isScoped = false)
    private val _callAutoEnableSpeakerphone = BooleanSetting(KEY_CALL_AUTO_ENABLE_SPEAKERPHONE, true, isScoped = false)
    private val _simulateCallsEnabled = BooleanSetting(KEY_SIMULATE_CALLS_ENABLED, false, isScoped = false)
    private val _hangUpPressesRequired = IntSetting(KEY_CALL_HANG_UP_PRESSES_REQUIRED, 2, isScoped = false)
    private val _filterCallsNotInContacts = BooleanSetting(KEY_FILTER_CALLS_NOT_IN_CONTACTS, false, isScoped = false)

    override val maxCallDurationSecondsFlow = _maxCallDurationSeconds.flow
    override val callDurationFeedbackIntervalSecondsFlow = _callDurationFeedbackIntervalSeconds.flow
    override val outgoingCallIntroFlow = _outgoingCallIntro.flow
    override val incomingCallIntroFlow = _incomingCallIntro.flow
    override val incomingCallScanLimitUserModeActiveFlow = _incomingCallScanLimitActive.flow
    override val incomingCallAutoActionUserModeActiveFlow = _incomingCallAutoActionActive.flow
    override val incomingCallDelayUserModeInactiveFlow = _incomingCallDelayInactive.flow
    override val incomingCallAutoActionUserModeInactiveFlow = _incomingCallAutoActionInactive.flow
    override val callAnnouncementAsCueFlow = _callAnnouncementAsCue.flow
    override val autoEnableSpeakerphoneFlow = _callAutoEnableSpeakerphone.flow
    override val simulateCallsEnabledFlow = _simulateCallsEnabled.flow
    override val hangUpPressesRequiredFlow = _hangUpPressesRequired.flow
    override val filterCallsNotInContactsFlow = _filterCallsNotInContacts.flow

    override var maxCallDurationSeconds: Int by _maxCallDurationSeconds
    override var callDurationFeedbackIntervalSeconds: Int by _callDurationFeedbackIntervalSeconds
    override var outgoingCallIntro: String by _outgoingCallIntro
    override var incomingCallIntro: String by _incomingCallIntro
    override var incomingCallScanLimitUserModeActive: Int by _incomingCallScanLimitActive
    override var incomingCallAutoActionUserModeActive: String by _incomingCallAutoActionActive
    override var incomingCallDelayUserModeInactive: Int by _incomingCallDelayInactive
    override var incomingCallAutoActionUserModeInactive: String by _incomingCallAutoActionInactive
    override var callAnnouncementAsCue: Boolean by _callAnnouncementAsCue
    override var autoEnableSpeakerphone: Boolean by _callAutoEnableSpeakerphone
    override var simulateCallsEnabled: Boolean by _simulateCallsEnabled
    override var hangUpPressesRequired: Int by _hangUpPressesRequired
    override var filterCallsNotInContacts: Boolean by _filterCallsNotInContacts

    override fun refresh() {
        _maxCallDurationSeconds.refresh()
        _callDurationFeedbackIntervalSeconds.refresh()
        _outgoingCallIntro.refresh()
        _incomingCallIntro.refresh()
        _incomingCallScanLimitActive.refresh()
        _incomingCallAutoActionActive.refresh()
        _incomingCallDelayInactive.refresh()
        _incomingCallAutoActionInactive.refresh()
        _callAnnouncementAsCue.refresh()
        _callAutoEnableSpeakerphone.refresh()
        _simulateCallsEnabled.refresh()
        _hangUpPressesRequired.refresh()
        _filterCallsNotInContacts.refresh()
    }
}
