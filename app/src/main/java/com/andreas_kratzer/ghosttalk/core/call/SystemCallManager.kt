package com.andreas_kratzer.ghosttalk.core.call

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.util.Log
import com.andreas_kratzer.ghosttalk.MainActivity
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import javax.inject.Inject
import javax.inject.Singleton

enum class CallState {
    NONE,
    RINGING,
    DIALING,
    ACTIVE,
    DISCONNECTED
}

@Singleton
class SystemCallManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appStateRepository: AppStateRepository,
    private val ttsHelper: TextToSpeechHelper
) : CallActionProxy {

    private val _callState = MutableStateFlow(CallState.NONE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isInCall = MutableStateFlow(false)
    override val isInCall: StateFlow<Boolean> = _isInCall.asStateFlow()

    private var currentCallState: CallState
        get() = _callState.value
        set(value) {
            Log.i(TAG, "Setting call state: $value (previous: ${_callState.value})")
            _callState.value = value
            _isInCall.value = (value != CallState.NONE)
        }

    companion object {
        private const val TAG = "SystemCallManager"
    }

    private val _callerName = MutableStateFlow<String?>(null)
    val callerName: StateFlow<String?> = _callerName.asStateFlow()

    private val _callerPhone = MutableStateFlow<String?>(null)
    val callerPhone: StateFlow<String?> = _callerPhone.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isOutgoing = MutableStateFlow(false)
    val isOutgoing: StateFlow<Boolean> = _isOutgoing.asStateFlow()

    private val _isSimulatedFlow = MutableStateFlow(false)
    val isSimulatedFlow: StateFlow<Boolean> = _isSimulatedFlow.asStateFlow()

    private var activeCall: Call? = null
    private val handler = Handler(Looper.getMainLooper())
    private var durationTimer: Timer? = null
    private var isSimulated = false
        set(value) {
            field = value
            _isSimulatedFlow.value = value
        }
    
    private var scanCyclesCompleted = 0
    private var inactiveTimerRunnable: Runnable? = null

    private var inCallService: InCallService? = null

    fun setInCallService(service: InCallService?) {
        Log.i(TAG, "setInCallService: service=$service")
        this.inCallService = service
    }

    var onCallDisconnectedListener: (() -> Unit)? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateCallState(call, state)
        }
    }

    fun requestDefaultDialer(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                activity.startActivityForResult(intent, 123)
                return
            }
        }
        val telecomManager = activity.getSystemService(TelecomManager::class.java)
        if (telecomManager.defaultDialerPackage != activity.packageName) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, 123)
        }
    }

    override fun startCall(contactName: String, contactPhone: String) {
        if (contactPhone.isBlank()) return
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", contactPhone, null)
        val extras = Bundle()
        try {
            if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                // Set dialing state immediately to pause scanning and stop TTS
                currentCallState = CallState.DIALING
                _callerPhone.value = contactPhone
                _callerName.value = contactName.takeIf { it.isNotBlank() } ?: contactPhone
                _isOutgoing.value = true
                _callDurationSeconds.value = 0
                ttsHelper.stopAll()
                stopAutoActions()
                configureAudioForConnectedCall()

                telecomManager.placeCall(uri, extras)
            } else {
                Log.e("SystemCallManager", "CALL_PHONE permission is missing!")
            }
        } catch (e: Exception) {
            Log.e("SystemCallManager", "Error placing call: ${e.message}", e)
        }
    }

    fun answerCall() {
        if (isSimulated) {
            Log.i(TAG, "answerCall (simulated)")
            currentCallState = CallState.ACTIVE
            stopAutoActions()
            configureAudioForConnectedCall()
            startDurationTimer()
            playIntroSpeech()
            return
        }
        activeCall?.let {
            if (it.details?.state == Call.STATE_RINGING) {
                it.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            }
        }
    }

    override fun hangUp() {
        if (isSimulated) {
            Log.i(TAG, "hangUp (simulated)")
            isSimulated = false
            currentCallState = CallState.NONE
            _callerName.value = null
            _callerPhone.value = null
            _callDurationSeconds.value = 0
            _isOutgoing.value = false
            stopDurationTimer()
            stopAutoActions()
            revertAudioMode()
            onCallDisconnectedListener?.invoke()
            return
        }
        activeCall?.let {
            if (it.details?.state == Call.STATE_RINGING) {
                it.reject(false, null)
            } else {
                it.disconnect()
            }
        }
    }

    override fun simulateIncomingCall(contactName: String, contactPhone: String) {
        Log.i(TAG, "simulateIncomingCall: name=$contactName, phone=$contactPhone")
        isSimulated = true
        activeCall = null
        
        currentCallState = CallState.RINGING
        _callerPhone.value = contactPhone
        _callerName.value = contactName.takeIf { it.isNotBlank() } ?: contactPhone
        _isOutgoing.value = false
        _callDurationSeconds.value = 0

        ttsHelper.stopAll()
        stopAutoActions()
        announceCaller()
        setupRingingTimers()
        
        // Launch MainActivity so the call UI displays
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    override fun simulateOutgoingCall(contactName: String, contactPhone: String) {
        Log.i(TAG, "simulateOutgoingCall: name=$contactName, phone=$contactPhone")
        isSimulated = true
        activeCall = null

        currentCallState = CallState.DIALING
        _callerPhone.value = contactPhone
        _callerName.value = contactName.takeIf { it.isNotBlank() } ?: contactPhone
        _isOutgoing.value = true
        _callDurationSeconds.value = 0

        ttsHelper.stopAll()
        stopAutoActions()
        configureAudioForConnectedCall()

        // Launch MainActivity so the call UI displays
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
        
        // Simulate auto-connecting outgoing call after 3 seconds
        handler.postDelayed({
            if (isSimulated && _callState.value == CallState.DIALING) {
                simulateCallConnected()
            }
        }, 3000)
    }

    private fun simulateCallConnected() {
        if (!isSimulated || _callState.value != CallState.DIALING) return
        Log.i(TAG, "simulateCallConnected")
        currentCallState = CallState.ACTIVE
        stopAutoActions()
        configureAudioForConnectedCall()
        startDurationTimer()
        playIntroSpeech()
    }

    fun onCallAdded(call: Call) {
        Log.i(TAG, "onCallAdded: call=$call, state=${call.details?.state}")
        isSimulated = false
        activeCall = call

        val rawState = call.details?.state ?: Call.STATE_NEW
        if (rawState == Call.STATE_RINGING && settingsRepository.filterCallsNotInContacts) {
            val uri = call.details?.handle
            val rawPhone = uri?.schemeSpecificPart ?: ""
            val contactName = getContactName(context, rawPhone)
            if (contactName == null) {
                Log.i(TAG, "onCallAdded: Incoming call filtered (number not in contacts: $rawPhone). Rejecting immediately.")
                call.reject(false, null)
                return
            }
        }

        call.registerCallback(callCallback)
        updateCallState(call, rawState)

        // Launch MainActivity so the call UI displays
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    fun onCallRemoved(call: Call) {
        Log.i(TAG, "onCallRemoved: call=$call")
        call.unregisterCallback(callCallback)
        if (activeCall == call) {
            activeCall = null
            currentCallState = CallState.NONE
            _callerName.value = null
            _callerPhone.value = null
            _callDurationSeconds.value = 0
            _isOutgoing.value = false
            stopDurationTimer()
            stopAutoActions()
            revertAudioMode()
            onCallDisconnectedListener?.invoke()
        }
    }

    private fun updateCallState(call: Call, state: Int) {
        val mappedState = when (state) {
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.DIALING
            Call.STATE_ACTIVE, Call.STATE_HOLDING -> CallState.ACTIVE
            Call.STATE_DISCONNECTED -> CallState.DISCONNECTED
            else -> CallState.NONE
        }

        Log.i(TAG, "updateCallState: rawState=$state, mappedState=$mappedState, previousState=${_callState.value}")

        if (mappedState != _callState.value) {
            currentCallState = mappedState

            val uri = call.details?.handle
            val rawPhone = uri?.schemeSpecificPart ?: ""
            _callerPhone.value = rawPhone
            val displayName = call.details?.callerDisplayName
            _callerName.value = if (!displayName.isNullOrBlank()) {
                displayName
            } else {
                getContactName(context, rawPhone) ?: rawPhone
            }
            _isOutgoing.value = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || (state == Call.STATE_ACTIVE && _isOutgoing.value)

            Log.i(TAG, "updateCallState: callerName=${_callerName.value}, callerPhone=$rawPhone, isOutgoing=${_isOutgoing.value}")

            when (mappedState) {
                CallState.RINGING -> {
                    Log.i(TAG, "Call RINGING - stopping TTS and setting up timers")
                    ttsHelper.stopAll()
                    stopAutoActions()
                    announceCaller()
                    setupRingingTimers()
                }
                CallState.DIALING -> {
                    Log.i(TAG, "Call DIALING - stopping TTS")
                    ttsHelper.stopAll()
                    stopAutoActions()
                    configureAudioForConnectedCall()
                }
                CallState.ACTIVE -> {
                    Log.i(TAG, "Call ACTIVE - configuring audio and starting timer")
                    stopAutoActions()
                    configureAudioForConnectedCall()
                    startDurationTimer()
                    playIntroSpeech()
                }
                CallState.DISCONNECTED -> {
                    Log.i(TAG, "Call DISCONNECTED")
                    onCallRemoved(call)
                }
                else -> {}
            }
        }
    }

    private fun setupRingingTimers() {
        stopAutoActions()
        val isUserMode = appStateRepository.isUserModeActive.value
        if (isUserMode) {
            scanCyclesCompleted = 0
        } else {
            val delaySeconds = settingsRepository.incomingCallDelayUserModeInactive
            val autoAction = settingsRepository.incomingCallAutoActionUserModeInactive
            if (autoAction != "NONE") {
                inactiveTimerRunnable = Runnable {
                    if (_callState.value == CallState.RINGING) {
                        Log.i("SystemCallManager", "Inactive timer reached limit, triggering: $autoAction")
                        if (autoAction == "ANSWER") {
                            answerCall()
                        } else if (autoAction == "REJECT") {
                            hangUp()
                        }
                    }
                }
                handler.postDelayed(inactiveTimerRunnable!!, delaySeconds * 1000L)
            }
        }
    }

    fun incrementScanCycle() {
        if (_callState.value != CallState.RINGING) return
        val isUserMode = appStateRepository.isUserModeActive.value
        if (isUserMode) {
            scanCyclesCompleted++
            val limit = settingsRepository.incomingCallScanLimitUserModeActive
            val autoAction = settingsRepository.incomingCallAutoActionUserModeActive
            if (scanCyclesCompleted >= limit && autoAction != "NONE") {
                Log.i("SystemCallManager", "Active scan cycles reached limit ($limit), triggering: $autoAction")
                if (autoAction == "ANSWER") {
                    answerCall()
                } else if (autoAction == "REJECT") {
                    hangUp()
                }
            }
        }
    }

    private fun stopAutoActions() {
        inactiveTimerRunnable?.let {
            handler.removeCallbacks(it)
            inactiveTimerRunnable = null
        }
        scanCyclesCompleted = 0
    }

    private fun announceCaller() {
        val name = _callerName.value ?: "Unbekannter Anrufer"
        val text = "Anruf von $name"
        
        val deviceAddress = if (settingsRepository.callAnnouncementAsCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }
        
        ttsHelper.speakRouted(text, deviceAddress)
    }

    private fun playIntroSpeech() {
        val introText = if (_isOutgoing.value) {
            settingsRepository.outgoingCallIntro
        } else {
            settingsRepository.incomingCallIntro
        }
        if (introText.isNotBlank()) {
            ttsHelper.speak(introText)
        }
    }

    private fun configureAudioForConnectedCall() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "configureAudioForConnectedCall: current mode=${audioManager.mode}, autoSpeakerphone=${settingsRepository.autoEnableSpeakerphone}")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (settingsRepository.autoEnableSpeakerphone) {
            // Delay speakerphone activation slightly to ensure the audio route is established
            handler.postDelayed({
                Log.i(TAG, "configureAudioForConnectedCall: activating speakerphone (delayed)")
                setSpeakerphoneEnabled(true)
                // Second retry after another delay for robustness
                handler.postDelayed({
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    @Suppress("DEPRECATION")
                    if (!am.isSpeakerphoneOn) {
                        Log.w(TAG, "Speakerphone still not on after first attempt, retrying...")
                        setSpeakerphoneEnabled(true)
                    }
                }, 500)
            }, 200)
        }
    }

    private fun revertAudioMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setSpeakerphoneEnabled(false)
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneEnabled(enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "setSpeakerphoneEnabled: enabled=$enabled, SDK=${Build.VERSION.SDK_INT}, currentMode=${audioManager.mode}")
        if (enabled) {
            inCallService?.let { service ->
                Log.i(TAG, "setSpeakerphoneEnabled: Calling InCallService.setAudioRoute(ROUTE_SPEAKER)")
                service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        } else {
            inCallService?.let { service ->
                Log.i(TAG, "setSpeakerphoneEnabled: Calling InCallService.setAudioRoute(ROUTE_EARPIECE)")
                service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            Log.i(TAG, "setSpeakerphoneEnabled: available devices: ${devices.map { "${it.type}(${it.productName})" }}")
            val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (enabled) {
                if (speakerDevice != null) {
                    val result = audioManager.setCommunicationDevice(speakerDevice)
                    Log.i(TAG, "setSpeakerphoneEnabled: setCommunicationDevice result=$result")
                }
                // Always also set the legacy flag as a fallback
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                @Suppress("DEPRECATION")
                Log.i(TAG, "setSpeakerphoneEnabled: isSpeakerphoneOn after set=${audioManager.isSpeakerphoneOn}")
            } else {
                audioManager.clearCommunicationDevice()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }

    private val durationRunnable = object : Runnable {
        override fun run() {
            val newDuration = _callDurationSeconds.value + 1
            _callDurationSeconds.value = newDuration
            
            val interval = settingsRepository.callDurationFeedbackIntervalSeconds
            if (interval > 0 && newDuration > 0 && newDuration % interval == 0) {
                val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
                val isEn = locale?.language?.equals("en", ignoreCase = true) == true

                val text = if (isEn) {
                    if (newDuration < 60) {
                        "Call duration is $newDuration seconds."
                    } else {
                        val minutes = newDuration / 60
                        val seconds = newDuration % 60
                        val minStr = if (minutes == 1) "minute" else "minutes"
                        if (seconds == 0) {
                            "Call has been active for $minutes $minStr."
                        } else {
                            "Call has been active for $minutes $minStr and $seconds seconds."
                        }
                    }
                } else {
                    if (newDuration < 60) {
                        "Telefonat dauert seit $newDuration Sekunden."
                    } else {
                        val minutes = newDuration / 60
                        val seconds = newDuration % 60
                        val minStr = if (minutes == 1) "Minute" else "Minuten"
                        if (seconds == 0) {
                            "Telefonat dauert seit $minutes $minStr."
                        } else {
                            "Telefonat dauert seit $minutes $minStr und $seconds Sekunden."
                        }
                    }
                }
                ttsHelper.speak(text)
            }
            
            val maxSec = settingsRepository.maxCallDurationSeconds
            if (maxSec in 1..newDuration) {
                stopDurationTimer()
                val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
                val isEn = locale?.language?.equals("en", ignoreCase = true) == true
                val maxReachedText = if (isEn) {
                    "Maximum call duration reached. Ending the call."
                } else {
                    "Maximale Anrufdauer erreicht. Der Anruf wird beendet."
                }
                ttsHelper.speak(maxReachedText, onDone = {
                    hangUp()
                }, onError = {
                    hangUp()
                })
            } else {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        _callDurationSeconds.value = 0
        handler.postDelayed(durationRunnable, 1000L)
    }

    private fun stopDurationTimer() {
        handler.removeCallbacks(durationRunnable)
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) {
            Log.d(TAG, "getContactName: phone number is blank")
            return null
        }
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "getContactName: READ_CONTACTS permission is NOT granted!")
            return null
        }
        val cleanNumber = android.telephony.PhoneNumberUtils.stripSeparators(phoneNumber) ?: phoneNumber
        Log.d(TAG, "getContactName: Attempting lookup for raw='$phoneNumber', clean='$cleanNumber'")
        
        // 1. Primary Lookup: Use PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI to search personal and work contacts
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(cleanNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            Log.d(TAG, "getContactName: Querying PhoneLookup with URI: $uri")
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                Log.d(TAG, "getContactName: PhoneLookup cursor row count = ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        Log.i(TAG, "getContactName: Found name '$name' via PhoneLookup")
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactName: Exception during PhoneLookup", e)
        }

        // 2. Fallback: If PhoneLookup returned nothing, try CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI
        val fallbackUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(cleanNumber))
        val fallbackProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        try {
            Log.d(TAG, "getContactName: Querying CommonDataKinds.Phone fallback with URI: $fallbackUri")
            context.contentResolver.query(fallbackUri, fallbackProjection, null, null, null)?.use { cursor ->
                Log.d(TAG, "getContactName: Fallback cursor row count = ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        Log.i(TAG, "getContactName: Found name '$name' via Fallback Lookup")
                        return name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getContactName: Exception during Fallback Lookup", e)
        }
        
        Log.i(TAG, "getContactName: No contact found for '$cleanNumber'")
        return null
    }
}
