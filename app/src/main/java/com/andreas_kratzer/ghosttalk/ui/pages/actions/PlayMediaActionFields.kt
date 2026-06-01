package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem

@Composable
fun PlayMediaActionFields(
    selectedProvider: MediaProvider,
    onProviderSelected: (MediaProvider) -> Unit,
    contentUri: String = "",
    onContentUriChanged: (String) -> Unit = {},
    contentName: String = "",
    onContentNameChanged: (String) -> Unit = {},
    returnToAppDelaySec: String = "2",
    onReturnToAppDelaySecChanged: (String) -> Unit = {},
    forcePlayViaMediaSession: Boolean = true,
    onForcePlayViaMediaSessionChanged: (Boolean) -> Unit = {},
    spotifyPlaylists: List<SpotifyPlaylist> = emptyList(),
    isLoadingSpotifyPlaylists: Boolean = false,
    spotifyUserDisplayName: String? = null,
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    onLoadSpotifyPlaylists: () -> Unit = {},
    onAutoSave: () -> Unit = {},
    onlyShowSelector: Boolean = false,
    onlyShowConfig: Boolean = false
) {
    var useCustomSpotifyUri by remember { mutableStateOf(contentUri.isNotEmpty() && spotifyPlaylists.none { it.id == contentUri }) }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider == MediaProvider.SPOTIFY) {
            onLoadSpotifyPlaylists()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!onlyShowConfig) {
            // Media Provider Selection
            var expanded by remember { mutableStateOf(false) }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val dimensions = com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                Text(
                    text = stringResource(R.string.button_media_provider_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                )

                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(id = when (selectedProvider) {
                                    MediaProvider.SPOTIFY -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_spotify
                                    MediaProvider.YOUTUBE -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube
                                    MediaProvider.YOUTUBE_MUSIC -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube_music
                                    MediaProvider.AUDIBLE -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_audible
                                }),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        MediaProvider.entries.forEach { provider ->
                            androidx.compose.material3.DropdownMenuItem(
                                leadingIcon = {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = when (provider) {
                                            MediaProvider.SPOTIFY -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_spotify
                                            MediaProvider.YOUTUBE -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube
                                            MediaProvider.YOUTUBE_MUSIC -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube_music
                                            MediaProvider.AUDIBLE -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_audible
                                        }),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                text = { Text(provider.displayName, style = MaterialTheme.typography.bodyLarge) },
                                onClick = {
                                    onProviderSelected(provider)
                                    focusManager.clearFocus()
                                    expanded = false
                                    onAutoSave()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!onlyShowSelector) {
            // Specific fields based on provider
            when (selectedProvider) {
                MediaProvider.SPOTIFY -> {
                    if (spotifyUserDisplayName != null) {
                        if (useCustomSpotifyUri) {
                            SettingsEditTextItem(
                                label = stringResource(R.string.button_media_custom_uri_label),
                                value = contentUri,
                                onValueChange = {
                                    onContentUriChanged(it)
                                    onContentNameChanged("") // Reset display name for custom input
                                },
                                placeholder = "spotify:playlist:...",
                                onFocusLost = onAutoSave
                            )
                            OutlinedButton(
                                onClick = { useCustomSpotifyUri = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Playlist aus Liste auswählen")
                            }
                        } else {
                            val playlistOptions = spotifyPlaylists.map { playlist ->
                                playlist.name to {
                                    onContentUriChanged(playlist.id)
                                    onContentNameChanged(playlist.name)
                                    onAutoSave()
                                }
                            }

                            val selectedPlaylistName = spotifyPlaylists.find { it.id == contentUri }?.name 
                                ?: stringResource(R.string.button_no_playlist_selected) // fallback

                            if (isLoadingSpotifyPlaylists) {
                                Text("Lade Spotify Playlists...", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                SettingsDropdownItem(
                                    label = stringResource(R.string.button_media_spotify_playlist_label),
                                    selectedOption = selectedPlaylistName,
                                    options = playlistOptions
                                )

                                OutlinedButton(
                                    onClick = { useCustomSpotifyUri = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.button_media_custom_uri))
                                }
                            }
                        }
                    } else {
                        // Not connected
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.button_media_spotify_not_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onConnectSpotify,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.button_media_spotify_link_settings))
                            }
                        }
                    }
                }
                MediaProvider.YOUTUBE -> {
                    SettingsEditTextItem(
                        label = "YouTube Link oder Video-ID",
                        value = contentUri,
                        onValueChange = { input ->
                            // Parse share links: extract video ID or playlist URL
                            val parsed = parseYouTubeInput(input)
                            onContentUriChanged(parsed)
                            onContentNameChanged("YouTube")
                        },
                        placeholder = "z.B. Share-Link einfügen",
                        onFocusLost = onAutoSave
                    )
                }
                MediaProvider.YOUTUBE_MUSIC -> {
                    SettingsEditTextItem(
                        label = "YouTube Music Link",
                        value = contentUri,
                        onValueChange = { input ->
                            // Accept full URLs directly – the intent passes them through
                            onContentUriChanged(input.trim())
                            onContentNameChanged("YouTube Music")
                        },
                        placeholder = "z.B. Share-Link einfügen",
                        onFocusLost = onAutoSave
                    )
                }
                MediaProvider.AUDIBLE -> {
                    SettingsEditTextItem(
                        label = "Audible Hörbuch ASIN oder Link (optional)",
                        value = contentUri,
                        onValueChange = { onContentUriChanged(it) },
                        placeholder = "z.B. ASIN des Hörbuchs",
                        onFocusLost = onAutoSave
                    )
                }
            }

            // Return delay to GoSTalk
            SettingsEditTextItem(
                label = stringResource(R.string.button_media_delay_label),
                value = returnToAppDelaySec,
                onValueChange = onReturnToAppDelaySecChanged,
                placeholder = stringResource(R.string.button_media_delay_placeholder),
                numericOnly = true,
                onFocusLost = onAutoSave
            )

            // Force Play via Media Session toggle
            SettingsToggleItem(
                label = "Wiedergabe automatisch starten",
                checked = forcePlayViaMediaSession,
                onCheckedChange = {
                    onForcePlayViaMediaSessionChanged(it)
                    onAutoSave()
                }
            )
        }
    }
}

/**
 * Parses YouTube share links / URLs into the format expected by the intent handler.
 * - Full playlist URLs are kept as-is (the handler passes them through)
 * - Video share links (youtu.be/..., youtube.com/watch?v=..., youtube.com/shorts/...) → extract video ID
 * - Raw video IDs (11 chars) are kept as-is
 */
private fun parseYouTubeInput(input: String): String {
    val trimmed = input.trim()
    // Playlist URLs: keep the full URL so the intent can open it directly
    if (trimmed.contains("list=")) return trimmed
    // youtu.be/VIDEO_ID
    if (trimmed.contains("youtu.be/")) {
        return trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
    }
    // youtube.com/watch?v=VIDEO_ID
    if (trimmed.contains("v=")) {
        return trimmed.substringAfter("v=").substringBefore("&").substringBefore("#")
    }
    // youtube.com/shorts/VIDEO_ID
    if (trimmed.contains("/shorts/")) {
        return trimmed.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
    }
    // Already a raw ID or unknown format – pass through
    return trimmed
}
