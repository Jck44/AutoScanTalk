package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun RankActionFields(
    rank: String,
    onRankChanged: (String) -> Unit,
    labelOverride: String? = null,
    onAutoSave: () -> Unit = {}
) {
    val dimensions = LocalDimensions.current
    val currentRankValue = rank.toIntOrNull()?.coerceIn(1, 15) ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
    ) {
        Text(
            text = labelOverride ?: stringResource(R.string.button_smart_prediction_rank_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = currentRankValue.toFloat(),
                onValueChange = { newValue ->
                    val roundedValue = newValue.toInt().coerceIn(1, 15)
                    if (roundedValue != currentRankValue) {
                        onRankChanged(roundedValue.toString())
                    }
                },
                valueRange = 1f..15f,
                steps = 13, // 15 - 1 - 1 = 13 discrete steps
                modifier = Modifier.weight(1f),
                onValueChangeFinished = onAutoSave
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .width(48.dp)
            ) {
                Text(
                    text = currentRankValue.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
