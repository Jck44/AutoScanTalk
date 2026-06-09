package com.andreas_kratzer.ghosttalk.ui.setup.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@Composable
fun OverlayStepContent(
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val localDimensions = LocalDimensions.current
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(72.dp)
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingMedium))
    Text(
        text = stringResource(SettingsR.string.settings_permission_overlay),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = stringResource(R.string.setup_permission_overlay_explanation),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    PermissionStatusCard(isGranted = isGranted)

    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Button(
        onClick = onRequest,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        enabled = !isGranted
    ) {
        Text(if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_btn_enable_overlay))
    }
}
