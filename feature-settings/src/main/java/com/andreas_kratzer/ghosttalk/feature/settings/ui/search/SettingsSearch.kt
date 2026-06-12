package com.andreas_kratzer.ghosttalk.feature.settings.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.ProfileEditSections
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsSection
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsSearchItem

@Composable
fun getSearchableItems(): List<SettingsSearchItem> {
    return listOf(
        // GENERAL
        SettingsSearchItem(stringResource(R.string.settings_app_language), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_theme_mode), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_startup_behavior), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_force_soft_keyboard), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_persist_logs), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_screen_behavior), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),
        SettingsSearchItem(stringResource(R.string.settings_caregiver_mode), stringResource(R.string.settings_category_general), SettingsSection.GENERAL),

        // MANAGE BOOK
        SettingsSearchItem(stringResource(R.string.book_name_label), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),
        SettingsSearchItem(stringResource(R.string.settings_start_page), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),
        SettingsSearchItem(stringResource(R.string.book_delete_description), stringResource(R.string.settings_category_manage_book), SettingsSection.MANAGE_BOOK),

        // VOICE
        SettingsSearchItem(stringResource(R.string.settings_tts_engine), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_elevenlabs_model), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.elevenlabs_stability), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.elevenlabs_similarity_boost), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_tts_language), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_select_voice), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),
        SettingsSearchItem(stringResource(R.string.settings_tts_playback_speed), stringResource(R.string.settings_category_voice), SettingsSection.VOICE),

        // AUDIO HARDWARE
        SettingsSearchItem(stringResource(R.string.settings_audio_tts), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_audio_cues), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_recording_source), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_block_volume_keys), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_speaker_volume_label), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_headphone_volume_label), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),
        SettingsSearchItem(stringResource(R.string.settings_bluetooth_delay), stringResource(R.string.settings_category_audio_hardware), SettingsSection.AUDIO_HARDWARE),

        // SCANNING
        SettingsSearchItem(stringResource(R.string.settings_scan_delay), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_late_click_threshold), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_holding_time), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_scan_pattern), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_auto_scan), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_restart_scan), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_limit_scan_cycles), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_scan_cycle_limit), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_show_static_row), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),
        SettingsSearchItem(stringResource(R.string.settings_switch_key), stringResource(R.string.settings_category_scanning), SettingsSection.SCANNING),

        // VOCAL SWITCH
        SettingsSearchItem(stringResource(R.string.settings_category_vocal_switch), stringResource(R.string.settings_category_vocal_switch), SettingsSection.VOCAL_SWITCH),
        SettingsSearchItem(stringResource(R.string.settings_vocal_switch_training), stringResource(R.string.settings_category_vocal_switch), SettingsSection.VOCAL_SWITCH),

        // SECURITY
        SettingsSearchItem(stringResource(R.string.settings_security_set_pin_title), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_pin_timeout), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_pin_required_for_deletion), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_biometric_enabled), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_edit), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_settings), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),
        SettingsSearchItem(stringResource(R.string.settings_security_require_for_analytics), stringResource(R.string.settings_category_security), SettingsSection.SECURITY),

        // AI
        SettingsSearchItem(stringResource(R.string.settings_gemini_enable), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_gemini_api_key), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_gemini_timeout), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_gemini_redo_prediction), stringResource(R.string.settings_category_gemini), SettingsSection.AI),
        SettingsSearchItem(stringResource(R.string.settings_smart_prediction_enable), stringResource(R.string.settings_category_gemini), SettingsSection.AI),

        // CALLS
        SettingsSearchItem(stringResource(R.string.settings_max_call_duration), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_duration_feedback_interval), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_intro_outgoing), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_intro_incoming), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_filter_not_in_contacts), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_call_auto_enable_speakerphone), stringResource(R.string.settings_category_call), SettingsSection.CALLS),
        SettingsSearchItem(stringResource(R.string.settings_simulate_phone_calls), stringResource(R.string.settings_category_call), SettingsSection.CALLS),

        // SMART_INTEGRATION
        SettingsSearchItem(stringResource(R.string.settings_hue_bridge_ip), stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),
        SettingsSearchItem(stringResource(R.string.settings_hue_bridge_connection), stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),
        SettingsSearchItem(stringResource(R.string.settings_spotify_connection), stringResource(R.string.settings_category_smart_home), SettingsSection.SMART_INTEGRATION),

        // PROFILE
        SettingsSearchItem(stringResource(R.string.settings_category_profile), stringResource(R.string.settings_category_profile), SettingsSection.PROFILE),

        // CLOUD_SYNC (Synchronization)
        SettingsSearchItem(stringResource(R.string.settings_cloud_sync_enabled), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_sync_drive_location), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_cloud_sync_interval), stringResource(R.string.settings_category_cloud), SettingsSection.CLOUD_SYNC),
        SettingsSearchItem(stringResource(R.string.settings_category_local_backup), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),

        // ACCOUNTS
        SettingsSearchItem(stringResource(R.string.settings_category_cloud_account), stringResource(R.string.settings_category_accounts), SettingsSection.ACCOUNTS),
        SettingsSearchItem(stringResource(R.string.settings_elevenlabs_api_key), stringResource(R.string.settings_category_accounts), SettingsSection.ACCOUNTS),
        SettingsSearchItem(stringResource(R.string.settings_spotify_connection), stringResource(R.string.settings_category_accounts), SettingsSection.ACCOUNTS),

        // PERMISSIONS
        SettingsSearchItem(stringResource(R.string.settings_category_notifications), stringResource(R.string.settings_category_notifications), SettingsSection.PERMISSIONS),

        // MAINTENANCE
        SettingsSearchItem(stringResource(R.string.settings_category_experimental), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),
        SettingsSearchItem(stringResource(R.string.settings_category_button_history), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE),
        SettingsSearchItem(stringResource(R.string.settings_category_cloud_import), stringResource(R.string.settings_category_maintenance), SettingsSection.MAINTENANCE)
    )
}

@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.settings_search_label))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        },
        singleLine = true
    )
}

@Composable
fun SearchResultItem(
    result: SettingsSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = result.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = result.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    imageVector = result.section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun SettingsSearchResults(
    searchQuery: String,
    editingProfileId: String?,
    dimensions: Dimensions,
    onResultClick: (SettingsSection, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (searchQuery.isNotBlank()) {
        val searchItems = getSearchableItems()
        val visibleSearchItems = if (editingProfileId != null) {
            searchItems.filter { it.section in ProfileEditSections }
        } else {
            searchItems
        }
        val results = visibleSearchItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
        LazyColumn(
            modifier = modifier
        ) {
            if (results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(dimensions.paddingMedium)
                    )
                }
            } else {
                items(results) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = {
                            onResultClick(result.section, result.title)
                        }
                    )
                }
            }
        }
    }
}
