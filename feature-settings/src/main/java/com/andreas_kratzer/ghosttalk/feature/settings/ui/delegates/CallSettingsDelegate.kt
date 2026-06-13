package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.app.Application
import android.content.Context
import android.content.Intent
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.ui.ProfileDraftCoordinator
import dagger.Lazy
import kotlinx.coroutines.flow.StateFlow

class CallSettingsDelegate(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val draftCoordinator: ProfileDraftCoordinator,
    private val callActionProxy: Lazy<CallActionProxy>
) {
    val maxCallDurationSeconds: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.maxCallDurationSecondsFlow) { it.maxCallDurationSeconds }
    val callDurationFeedbackIntervalSeconds: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.callDurationFeedbackIntervalSecondsFlow) { it.callDurationFeedbackIntervalSeconds }
    val outgoingCallIntro: StateFlow<String> = draftCoordinator.scopedFlow(settingsRepository.outgoingCallIntroFlow) { it.outgoingCallIntro }
    val incomingCallIntro: StateFlow<String> = draftCoordinator.scopedFlow(settingsRepository.incomingCallIntroFlow) { it.incomingCallIntro }
    val incomingCallScanLimitUserModeActive: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.incomingCallScanLimitUserModeActiveFlow) { it.incomingCallScanLimitUserModeActive }
    val incomingCallAutoActionUserModeActive: StateFlow<String> = draftCoordinator.scopedFlow(settingsRepository.incomingCallAutoActionUserModeActiveFlow) { it.incomingCallAutoActionUserModeActive }
    val incomingCallDelayUserModeInactive: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.incomingCallDelayUserModeInactiveFlow) { it.incomingCallDelayUserModeInactive }
    val incomingCallAutoActionUserModeInactive: StateFlow<String> = draftCoordinator.scopedFlow(settingsRepository.incomingCallAutoActionUserModeInactiveFlow) { it.incomingCallAutoActionUserModeInactive }
    val callAnnouncementAsCue: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.callAnnouncementAsCueFlow) { it.callAnnouncementAsCue }
    val autoEnableSpeakerphone: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.autoEnableSpeakerphoneFlow) { it.autoEnableSpeakerphone }
    val simulateCallsEnabled: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.simulateCallsEnabledFlow) { it.simulateCallsEnabled }
    val hangUpPressesRequired: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.hangUpPressesRequiredFlow) { it.hangUpPressesRequired }
    val hangUpPressWindowSeconds: StateFlow<Int> = draftCoordinator.scopedFlow(settingsRepository.hangUpPressWindowSecondsFlow) { it.hangUpPressWindowSeconds }
    val filterCallsNotInContacts: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.filterCallsNotInContactsFlow) { it.filterCallsNotInContacts }

    fun setMaxCallDurationSeconds(seconds: Int) = draftCoordinator.update({ 
        settingsRepository.maxCallDurationSeconds = seconds
    }) { it.copy(maxCallDurationSeconds = seconds) }

    fun setCallDurationFeedbackIntervalSeconds(seconds: Int) = draftCoordinator.update({ 
        settingsRepository.callDurationFeedbackIntervalSeconds = seconds
    }) { it.copy(callDurationFeedbackIntervalSeconds = seconds) }

    fun setOutgoingCallIntro(text: String) = draftCoordinator.update({ 
        settingsRepository.outgoingCallIntro = text
    }) { it.copy(outgoingCallIntro = text) }

    fun setIncomingCallIntro(text: String) = draftCoordinator.update({ 
        settingsRepository.incomingCallIntro = text
    }) { it.copy(incomingCallIntro = text) }

    fun setIncomingCallScanLimitUserModeActive(limit: Int) = draftCoordinator.update({ 
        settingsRepository.incomingCallScanLimitUserModeActive = limit
    }) { it.copy(incomingCallScanLimitUserModeActive = limit) }

    fun setIncomingCallAutoActionUserModeActive(action: String) = draftCoordinator.update({ 
        settingsRepository.incomingCallAutoActionUserModeActive = action
    }) { it.copy(incomingCallAutoActionUserModeActive = action) }

    fun setIncomingCallDelayUserModeInactive(seconds: Int) = draftCoordinator.update({ 
        settingsRepository.incomingCallDelayUserModeInactive = seconds
    }) { it.copy(incomingCallDelayUserModeInactive = seconds) }

    fun setIncomingCallAutoActionUserModeInactive(action: String) = draftCoordinator.update({ 
        settingsRepository.incomingCallAutoActionUserModeInactive = action
    }) { it.copy(incomingCallAutoActionUserModeInactive = action) }

    fun setCallAnnouncementAsCue(asCue: Boolean) = draftCoordinator.update({ 
        settingsRepository.callAnnouncementAsCue = asCue
    }) { it.copy(callAnnouncementAsCue = asCue) }

    fun setAutoEnableSpeakerphone(enable: Boolean) = draftCoordinator.update({ 
        settingsRepository.autoEnableSpeakerphone = enable
    }) { it.copy(autoEnableSpeakerphone = enable) }

    fun setSimulateCallsEnabled(enable: Boolean) = draftCoordinator.update({ 
        settingsRepository.simulateCallsEnabled = enable
    }) { it.copy(simulateCallsEnabled = enable) }

    fun setHangUpPressesRequired(presses: Int) = draftCoordinator.update({
        settingsRepository.hangUpPressesRequired = presses
    }) { it.copy(hangUpPressesRequired = presses) }

    fun setHangUpPressWindowSeconds(seconds: Int) = draftCoordinator.update({
        settingsRepository.hangUpPressWindowSeconds = seconds
    }) { it.copy(hangUpPressWindowSeconds = seconds) }

    fun setFilterCallsNotInContacts(filter: Boolean) = draftCoordinator.update({ 
        settingsRepository.filterCallsNotInContacts = filter
    }) { it.copy(filterCallsNotInContacts = filter) }

    val isDefaultDialer: Boolean
        get() {
            val telecomManager = application.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            return telecomManager.defaultDialerPackage == application.packageName
        }

    fun requestDefaultDialer(activity: android.app.Activity) {
        val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
            activity.startActivityForResult(intent, 123)
            return
        }
        val telecomManager = activity.getSystemService(android.telecom.TelecomManager::class.java)
        if (telecomManager != null && telecomManager.defaultDialerPackage != activity.packageName) {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, 123)
        }
    }

    fun simulateIncomingCall(name: String, phone: String) {
        callActionProxy.get().simulateIncomingCall(name, phone)
    }

    fun simulateOutgoingCall(name: String, phone: String) {
        callActionProxy.get().simulateOutgoingCall(name, phone)
    }
}
