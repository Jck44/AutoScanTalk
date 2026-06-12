package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val forceKeyboard by viewModel.forceSoftKeyboard.collectAsState()
    val simulateCallsEnabled by viewModel.call.simulateCallsEnabled.collectAsState()

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        val categoryCall = stringResource(R.string.settings_category_call)
        PreferenceCategory(categoryCall, modifier = Modifier.weight(1f)) {
            val maxCallDurationSeconds by viewModel.call.maxCallDurationSeconds.collectAsState()
            val callDurationFeedbackIntervalSeconds by viewModel.call.callDurationFeedbackIntervalSeconds.collectAsState()
            val outgoingCallIntro by viewModel.call.outgoingCallIntro.collectAsState()
            val incomingCallIntro by viewModel.call.incomingCallIntro.collectAsState()
            val incomingCallScanLimitActive by viewModel.call.incomingCallScanLimitUserModeActive.collectAsState()
            val incomingCallAutoActionActive by viewModel.call.incomingCallAutoActionUserModeActive.collectAsState()
            val incomingCallDelayInactive by viewModel.call.incomingCallDelayUserModeInactive.collectAsState()
            val incomingCallAutoActionInactive by viewModel.call.incomingCallAutoActionUserModeInactive.collectAsState()
            val callAnnouncementAsCue by viewModel.call.callAnnouncementAsCue.collectAsState()
            val autoEnableSpeakerphone by viewModel.call.autoEnableSpeakerphone.collectAsState()
            val hangUpPressesRequired by viewModel.call.hangUpPressesRequired.collectAsState()
            val filterCallsNotInContacts by viewModel.call.filterCallsNotInContacts.collectAsState()

            var isDefaultDialer by remember { mutableStateOf(false) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                        isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Text(
                text = stringResource(R.string.settings_dialer_register_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
            val activity = context.findActivity()
            if (activity != null) {
                Button(
                    onClick = { viewModel.call.requestDefaultDialer(activity) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isDefaultDialer
                ) {
                    if (isDefaultDialer) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(dimensions.paddingSmall))
                        Text(stringResource(R.string.settings_dialer_registered))
                    } else {
                        Text(stringResource(R.string.settings_dialer_register_title))
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            // Max. Anrufdauer Dropdown
            val maxDurationLabel = stringResource(R.string.settings_max_call_duration)
            val durationOptions = listOf(
                0 to "Aus (Keine Begrenzung)",
                60 to "1 Minute",
                120 to "2 Minuten",
                300 to "5 Minuten",
                600 to "10 Minuten"
            )
            val selectedDurationLabel = durationOptions.find { it.first == maxCallDurationSeconds }?.second ?: "$maxCallDurationSeconds Sek."
            SettingsDropdownItem(
                label = maxDurationLabel,
                selectedOption = selectedDurationLabel,
                options = durationOptions.map { (seconds, label) ->
                    label to { viewModel.call.setMaxCallDurationSeconds(seconds) }
                }
            )

            // Audio-Feedback zur Anrufdauer Dropdown
            val feedbackIntervalLabel = stringResource(R.string.settings_call_duration_feedback_interval)
            val feedbackIntervalOptions = listOf(
                0 to stringResource(R.string.settings_call_duration_feedback_off),
                15 to stringResource(R.string.settings_call_duration_feedback_15s),
                30 to stringResource(R.string.settings_call_duration_feedback_30s),
                60 to stringResource(R.string.settings_call_duration_feedback_1m),
                120 to stringResource(R.string.settings_call_duration_feedback_2m),
                300 to stringResource(R.string.settings_call_duration_feedback_5m)
            )
            val selectedFeedbackLabel = feedbackIntervalOptions.find { it.first == callDurationFeedbackIntervalSeconds }?.second 
                ?: "$callDurationFeedbackIntervalSeconds Sek."
            SettingsDropdownItem(
                label = feedbackIntervalLabel,
                selectedOption = selectedFeedbackLabel,
                options = feedbackIntervalOptions.map { (seconds, label) ->
                    label to { viewModel.call.setCallDurationFeedbackIntervalSeconds(seconds) }
                }
            )

            // Outgoing intro
            SettingsEditTextItem(
                label = stringResource(R.string.settings_call_intro_outgoing),
                value = outgoingCallIntro,
                onValueChange = { viewModel.call.setOutgoingCallIntro(it) },
                forceKeyboard = forceKeyboard
            )

            // Incoming intro
            SettingsEditTextItem(
                label = stringResource(R.string.settings_call_intro_incoming),
                value = incomingCallIntro,
                onValueChange = { viewModel.call.setIncomingCallIntro(it) },
                forceKeyboard = forceKeyboard
            )

            // Announcement as Auditory Cue Toggle
            SettingsToggleItem(
                label = stringResource(R.string.settings_call_announcement_as_cue),
                checked = callAnnouncementAsCue,
                onCheckedChange = { viewModel.call.setCallAnnouncementAsCue(it) }
            )

            // Auto Speakerphone Toggle
            SettingsToggleItem(
                label = stringResource(R.string.settings_call_auto_enable_speakerphone),
                checked = autoEnableSpeakerphone,
                onCheckedChange = { viewModel.call.setAutoEnableSpeakerphone(it) }
            )

            // Block calls not in contacts Toggle
            SettingsToggleItem(
                label = stringResource(R.string.settings_call_filter_not_in_contacts),
                description = stringResource(R.string.settings_call_filter_not_in_contacts_desc),
                checked = filterCallsNotInContacts,
                onCheckedChange = { viewModel.call.setFilterCallsNotInContacts(it) }
            )

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            // Active Call Behavior
            Text(
                text = stringResource(R.string.settings_call_behavior_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            val scanLimitLabel = stringResource(R.string.settings_call_scan_limit)
            val scanLimitOptions = listOf(1, 2, 3, 4, 5)
            SettingsDropdownItem(
                label = scanLimitLabel,
                selectedOption = "$incomingCallScanLimitActive Durchläufe",
                options = scanLimitOptions.map { limit ->
                    "$limit Durchläufe" to { viewModel.call.setIncomingCallScanLimitUserModeActive(limit) }
                }
            )

            val autoActionLabel = stringResource(R.string.settings_call_auto_action)
            val actionOptions = listOf(
                "NONE" to stringResource(R.string.settings_call_action_none),
                "ANSWER" to stringResource(R.string.settings_call_action_answer),
                "REJECT" to stringResource(R.string.settings_call_action_reject)
            )
            val selectedActionLabel = actionOptions.find { it.first == incomingCallAutoActionActive }?.second ?: incomingCallAutoActionActive
            SettingsDropdownItem(
                label = autoActionLabel,
                selectedOption = selectedActionLabel,
                options = actionOptions.map { (action, label) ->
                    label to { viewModel.call.setIncomingCallAutoActionUserModeActive(action) }
                }
            )

            val hangUpPressesLabel = stringResource(R.string.settings_call_hang_up_presses)
            val hangUpPressesOptions = listOf(1, 2, 3, 4, 5)
            val selectedHangUpOptionLabel = when (hangUpPressesRequired) {
                1 -> "1 (Sofort / Immediate)"
                2 -> "2 (Bestätigung / Confirmation)"
                3 -> "3"
                4 -> "4"
                5 -> "5"
                else -> hangUpPressesRequired.toString()
            }
            SettingsDropdownItem(
                label = hangUpPressesLabel,
                selectedOption = selectedHangUpOptionLabel,
                options = hangUpPressesOptions.map { presses ->
                    val optLabel = when (presses) {
                        1 -> "1 (Sofort / Immediate)"
                        2 -> "2 (Bestätigung / Confirmation)"
                        3 -> "3"
                        4 -> "4"
                        5 -> "5"
                        else -> presses.toString()
                    }
                    optLabel to { viewModel.call.setHangUpPressesRequired(presses) }
                }
            )

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            // Inactive Call Behavior
            Text(
                text = stringResource(R.string.settings_call_behavior_inactive),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            val delayLabel = stringResource(R.string.settings_call_delay)
            val delayOptions = listOf(5, 10, 15, 20, 30, 45, 60)
            SettingsDropdownItem(
                label = delayLabel,
                selectedOption = "$incomingCallDelayInactive Sekunden",
                options = delayOptions.map { seconds ->
                    "$seconds Sekunden" to { viewModel.call.setIncomingCallDelayUserModeInactive(seconds) }
                }
            )

            val autoActionInactiveLabel = stringResource(R.string.settings_call_auto_action_inactive)
            val selectedActionInactiveLabel = actionOptions.find { it.first == incomingCallAutoActionInactive }?.second ?: incomingCallAutoActionInactive
            SettingsDropdownItem(
                label = autoActionInactiveLabel,
                selectedOption = selectedActionInactiveLabel,
                options = actionOptions.map { (action, label) ->
                    label to { viewModel.call.setIncomingCallAutoActionUserModeInactive(action) }
                }
            )
        }

        PreferenceCategory(
            title = "Anruf-Simulation",
            modifier = Modifier.weight(1f)
        ) {
            var simName by rememberSaveable { mutableStateOf("Test Anrufer") }
            var simPhone by rememberSaveable { mutableStateOf("+49 123 456789") }

            Text(
                text = "Hier kannst du Anrufe simulieren, um das Verhalten der App und des Overlays zu testen, ohne eine echte Telefonverbindung aufzubauen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            SettingsToggleItem(
                label = "Ausgehende Anrufe bei Button-Klick simulieren",
                checked = simulateCallsEnabled,
                onCheckedChange = { viewModel.call.setSimulateCallsEnabled(it) }
            )

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            SettingsEditTextItem(
                label = "Name des Anrufers",
                value = simName,
                onValueChange = { simName = it },
                forceKeyboard = forceKeyboard
            )

            SettingsEditTextItem(
                label = "Telefonnummer",
                value = simPhone,
                onValueChange = { simPhone = it },
                forceKeyboard = forceKeyboard
            )

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            Button(
                onClick = { viewModel.call.simulateIncomingCall(simName, simPhone) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Eingehenden Anruf simulieren")
            }

            Spacer(modifier = Modifier.height(dimensions.paddingSmall))

            Button(
                onClick = { viewModel.call.simulateOutgoingCall(simName, simPhone) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Ausgehenden Anruf simulieren")
            }
        }
    }
}

internal fun Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
