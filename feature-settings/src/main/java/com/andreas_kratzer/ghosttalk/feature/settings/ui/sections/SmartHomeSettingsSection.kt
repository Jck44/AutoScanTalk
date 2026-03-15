package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun SmartHomeSettingsSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean
) {
    val dimensions = LocalDimensions.current
    
    val googleHomeProjectId by viewModel.googleHomeProjectId.collectAsState("")
    val hueBridgeIp by viewModel.hueBridgeIp.collectAsState("")
    val hueUsername by viewModel.hueUsername.collectAsState("")
    val hueAccessToken by viewModel.hueAccessToken.collectAsState("")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        // --- Google Home Section ---
        PreferenceCategory(stringResource(R.string.smart_home_provider_google_home)) {
            SettingsEditTextItem(
                label = stringResource(R.string.settings_google_home_project_id),
                value = googleHomeProjectId,
                onValueChange = { viewModel.setGoogleHomeProjectId(it) }
            )
            Text(
                text = stringResource(R.string.settings_google_home_project_id_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        // --- Philips Hue Section ---
        PreferenceCategory(stringResource(R.string.smart_home_provider_philips_hue)) {
            val isHueConnected = hueAccessToken.isNotEmpty()
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = if (isHueConnected) 
                        stringResource(R.string.settings_hue_status_connected) 
                    else 
                        stringResource(R.string.settings_hue_status_disconnected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isHueConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    if (!isHueConnected) {
                        Button(onClick = { viewModel.startHueOAuth() }) {
                            Text(stringResource(R.string.settings_hue_connect))
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setHueAccessToken("") }) {
                            Text(stringResource(R.string.settings_hue_disconnect))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedButton(onClick = { viewModel.discoverHueBridges() }) {
                        Text(stringResource(R.string.settings_hue_discover_bridges))
                    }
                }
            }

            if (!isHueConnected) {
                Text(
                    text = "Alternative (Local):",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                )
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_hue_bridge_ip),
                    value = hueBridgeIp,
                    onValueChange = { viewModel.setHueBridgeIp(it) }
                )
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_hue_username),
                    value = hueUsername,
                    onValueChange = { viewModel.setHueUsername(it) }
                )
            }
        }


    }
}

@Composable
fun SmartHomeProvider.getDisplayName(): String {
    return when (this) {
        SmartHomeProvider.GOOGLE_HOME -> stringResource(R.string.smart_home_provider_google_home)
        SmartHomeProvider.PHILIPS_HUE -> stringResource(R.string.smart_home_provider_philips_hue)
    }
}
