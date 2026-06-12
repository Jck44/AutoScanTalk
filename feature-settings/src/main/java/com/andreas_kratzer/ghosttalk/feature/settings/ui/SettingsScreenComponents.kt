package com.andreas_kratzer.ghosttalk.feature.settings.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.feature.settings.R

@Composable
fun Modifier.highlightSetting(label: String, highlightedKey: String?): Modifier {
    val isHighlighted = label == highlightedKey
    val alpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.6f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )
    return this.background(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun ProfileEditBanner(
    profileName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings_profile_edit_draft_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.settings_profile_edit_draft_desc, profileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ProfileNameEditCard(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_profile_name_hint),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_profile_enter_name_placeholder)) }
            )
        }
    }
}

@Composable
fun SettingsMainMenu(
    padding: PaddingValues,
    dimensions: Dimensions,
    sections: List<SettingsSection> = SettingsSection.entries,
    onSectionSelected: (SettingsSection) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(vertical = dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        contentPadding = PaddingValues(bottom = dimensions.paddingDoubleExtraLarge)
    ) {
        items(sections) { section ->
            Surface(
                onClick = { onSectionSelected(section) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_section_${section.name}")
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(section.getTitleRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = GhostTalkIcons.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(modifier = Modifier.height(dimensions.paddingDoubleExtraLarge))
                VersionInfo()
            }
        }
    }
}

@Composable
fun VersionInfo() {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
        "Unknown"
    }
    Text(
        text = "Version: $versionName",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}
