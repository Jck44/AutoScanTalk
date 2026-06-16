package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun ProfileSettingsSection(
    viewModel: SettingsViewModel
) {
    val activeProfileId by viewModel.activeProfileIdFlow.collectAsState()
    val allProfiles by viewModel.allSettingsProfilesFlow.collectAsState()
    val editingProfileId by viewModel.editingProfileId.collectAsState()
    val showCreateProfileDialog = remember { mutableStateOf(false) }
    val newProfileName = remember { mutableStateOf("") }
    val showDeleteConfirmDialog = remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.SettingsProfile?>(null) }
    val showActivateConfirmDialog = remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.SettingsProfile?>(null) }
    val dimensions = LocalDimensions.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        PreferenceCategory(
            title = stringResource(R.string.settings_category_profile),
            isCloudProfile = true
        ) {
            Text(
                text = "Profile verwalten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            allProfiles.forEach { profile ->
                val isActive = profile.id == activeProfileId
                val isEditingThis = profile.id == editingProfileId

                val cardBorder = if (isActive) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else if (isEditingThis) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }

                val cardBg = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                } else if (isEditingThis) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surface
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = cardBorder,
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Profile Avatar Icon Circle
                        val avatarBg = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else if (isEditingThis) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }

                        val avatarTint = if (isActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else if (isEditingThis) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val avatarIcon = if (isEditingThis) Icons.Default.Edit else Icons.Default.Person

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(avatarBg),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Icon(
                                imageVector = avatarIcon,
                                contentDescription = null,
                                tint = avatarTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Profile Info Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isActive) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Aktiv",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else if (isEditingThis) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Entwurf wird bearbeitet",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            } else {
                                Text(
                                    text = "Inaktiv",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.startEditingProfile(profile.id) },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                                border = BorderStroke(1.dp, if (isEditingThis) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bearbeiten",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            var showDropdownMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showDropdownMenu = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = GhostTalkIcons.MoreVert,
                                        contentDescription = "Optionen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showDropdownMenu,
                                    onDismissRequest = { showDropdownMenu = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Als aktives Profil anwenden") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showDropdownMenu = false
                                            showActivateConfirmDialog.value = profile
                                        },
                                        enabled = !isActive
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Profil löschen") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = if (profile.id != "profile-default" && allProfiles.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showDropdownMenu = false
                                            showDeleteConfirmDialog.value = profile
                                        },
                                        enabled = profile.id != "profile-default" && allProfiles.size > 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showCreateProfileDialog.value = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_profile_create),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (showCreateProfileDialog.value) {
            GhostTalkDialog(
                title = stringResource(R.string.settings_profile_create_title),
                confirmText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_confirm),
                dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel),
                onConfirm = {
                    if (newProfileName.value.isNotBlank()) {
                        viewModel.createNewProfile(newProfileName.value)
                        newProfileName.value = ""
                        showCreateProfileDialog.value = false
                    }
                },
                onDismiss = { showCreateProfileDialog.value = false },
                confirmEnabled = newProfileName.value.isNotBlank(),
                content = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newProfileName.value,
                        onValueChange = { newProfileName.value = it },
                        label = { Text(stringResource(R.string.settings_profile_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        if (showActivateConfirmDialog.value != null) {
            val profileToActivate = showActivateConfirmDialog.value!!
            GhostTalkDialog(
                title = stringResource(R.string.settings_profile_switch_title),
                confirmText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_confirm),
                dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel),
                onConfirm = {
                    viewModel.setActiveProfileId(profileToActivate.id)
                    showActivateConfirmDialog.value = null
                },
                onDismiss = { showActivateConfirmDialog.value = null },
                content = {
                    Text(stringResource(R.string.settings_profile_switch_confirm, profileToActivate.name))
                }
            )
        }

        if (showDeleteConfirmDialog.value != null) {
            val profileToDelete = showDeleteConfirmDialog.value!!
            GhostTalkDialog(
                title = stringResource(R.string.settings_profile_delete),
                confirmText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_confirm),
                dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel),
                isDestructive = true,
                onConfirm = {
                    viewModel.deleteProfile(profileToDelete)
                    showDeleteConfirmDialog.value = null
                },
                onDismiss = { showDeleteConfirmDialog.value = null },
                content = {
                    Text(stringResource(R.string.settings_profile_delete_confirm, profileToDelete.name))
                }
            )
        }
    }
}
