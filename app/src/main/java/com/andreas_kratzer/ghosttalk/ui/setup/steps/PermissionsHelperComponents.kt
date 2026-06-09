package com.andreas_kratzer.ghosttalk.ui.setup.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@Composable
fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    description: String? = null
) {
    val localDimensions = LocalDimensions.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }
    
    val chipBg = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgLight
    }
    
    val chipContentColor = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextLight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .run {
                if (description != null) {
                    clickable { isExpanded = !isExpanded }
                } else this
            }
            .padding(horizontal = localDimensions.paddingLarge, vertical = localDimensions.paddingMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (description != null) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Surface(
                color = chipBg,
                contentColor = chipContentColor,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isGranted) stringResource(SettingsR.string.settings_permission_active) else stringResource(SettingsR.string.settings_permission_inactive),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        if (description != null) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusCard(isGranted: Boolean) {
    val localDimensions = LocalDimensions.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    val cardBg = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveBgLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveBgLight
    }
    
    val contentColor = if (isGranted) {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusActiveTextLight
    } else {
        if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.StatusInactiveTextLight
    }

    Surface(
        color = cardBg,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(localDimensions.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(localDimensions.paddingMedium))
            Text(
                text = if (isGranted) {
                    stringResource(R.string.setup_status_permission_granted)
                } else {
                    stringResource(R.string.setup_status_permission_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
