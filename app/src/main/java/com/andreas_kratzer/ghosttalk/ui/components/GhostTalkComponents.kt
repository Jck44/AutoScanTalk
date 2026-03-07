package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@Composable
fun AppBrandHeader(
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    title: String? = null,
    subtitle: String? = null
) {
    val dimensions = LocalDimensions.current
    val titleStyle = if (isLandscape) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium
    val subtitleStyle = MaterialTheme.typography.titleMedium
    val logoSize = if (isLandscape) dimensions.logoSizeSmall else dimensions.logoSizeMedium

    val displayTitle = title ?: stringResource(R.string.app_name)
    val displaySubtitle = subtitle ?: stringResource(R.string.start_tagline)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(logoSize)
        )
        if (displayTitle.isNotBlank() || displaySubtitle.isNotBlank()) {
            Spacer(modifier = Modifier.width(if (isLandscape) 12.dp else dimensions.paddingLarge))
            Column {
                if (displayTitle.isNotBlank()) {
                    Text(
                        text = displayTitle,
                        style = titleStyle.copy(
                            fontWeight = FontWeight.Bold,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                    )
                }
                if (displaySubtitle.isNotBlank()) {
                    Text(
                        text = displaySubtitle,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GhostTalkCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val dimensions = LocalDimensions.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.cardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensions.paddingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(dimensions.iconSizeLarge),
                tint = iconColor
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}
