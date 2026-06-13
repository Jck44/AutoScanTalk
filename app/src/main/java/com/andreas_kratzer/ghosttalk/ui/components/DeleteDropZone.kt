package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R

@Composable
fun DeleteDropZone(modifier: Modifier = Modifier) {
    val state = LocalDragDropState.current
    val isHovered = state.currentHoveredTarget == DeleteTarget

    val containerColor = if (isHovered) MaterialTheme.colorScheme.error
                         else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
    val contentColor = if (isHovered) MaterialTheme.colorScheme.onError
                       else MaterialTheme.colorScheme.onErrorContainer
    val scale by animateFloatAsState(if (isHovered) 1.05f else 1f, label = "deleteZoneScale")

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .border(
                width = if (isHovered) 2.dp else 0.dp,
                color = if (isHovered) MaterialTheme.colorScheme.onError else Color.Transparent,
                shape = MaterialTheme.shapes.large
            )
            .dropTarget(key = DeleteTarget)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = stringResource(R.string.page_editor_delete_drop_zone),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
