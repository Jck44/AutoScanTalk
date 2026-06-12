package com.andreas_kratzer.ghosttalk.ui.pages.analytics.buttonstats

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationHighDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationHighLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationLowDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationLowLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationMediumDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationMediumLight
import java.util.Locale

@Composable
fun ButtonKpiSection(
    metrics: ButtonEffortMetrics?,
    isNarrow: Boolean
) {
    if (isNarrow) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Click Count Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aufrufe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${metrics?.usageCount ?: 0}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Access Time Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zugriffszeit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (metrics != null) {
                            if (metrics.shortestPathTimeSec > metrics.accessTimeSec) {
                                "${metrics.accessTimeSec}s / ${metrics.shortestPathTimeSec}s"
                            } else {
                                "${metrics.accessTimeSec}s"
                            }
                        } else "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Frustration Index Card
            val frustrationVal = metrics?.frustrationIndex ?: 0f
            val isDark = isSystemInDarkTheme()
            val frustrationColor = when {
                frustrationVal > 0.6f -> if (isDark) FrustrationHighDark else FrustrationHighLight
                frustrationVal > 0.3f -> if (isDark) FrustrationMediumDark else FrustrationMediumLight
                else -> if (isDark) FrustrationLowDark else FrustrationLowLight
            }
            val frustrationLabel = when {
                frustrationVal > 0.6f -> "Hoch"
                frustrationVal > 0.3f -> "Mittel"
                frustrationVal > 0f -> "Niedrig"
                else -> "Keine"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frustrationsindex",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", frustrationVal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = frustrationColor
                        )
                        Text(
                            text = "($frustrationLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            color = frustrationColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Click Count Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Aufrufe",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${metrics?.usageCount ?: 0}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Access Time Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Zugriffszeit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (metrics != null) {
                            if (metrics.shortestPathTimeSec > metrics.accessTimeSec) {
                                "${metrics.accessTimeSec}s / ${metrics.shortestPathTimeSec}s (Global)"
                            } else {
                                "${metrics.accessTimeSec}s"
                            }
                        } else "--",
                        style = if (metrics != null && metrics.shortestPathTimeSec > metrics.accessTimeSec) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Frustration Index Card
            val frustrationVal = metrics?.frustrationIndex ?: 0f
            val isDark = isSystemInDarkTheme()
            val frustrationColor = when {
                frustrationVal > 0.6f -> if (isDark) FrustrationHighDark else FrustrationHighLight
                frustrationVal > 0.3f -> if (isDark) FrustrationMediumDark else FrustrationMediumLight
                else -> if (isDark) FrustrationLowDark else FrustrationLowLight
            }
            val frustrationLabel = when {
                frustrationVal > 0.6f -> "Hoch"
                frustrationVal > 0.3f -> "Mittel"
                frustrationVal > 0f -> "Niedrig"
                else -> "Keine"
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Frustrationsindex",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f", frustrationVal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = frustrationColor
                        )
                        Text(
                            text = "($frustrationLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            color = frustrationColor,
                            modifier = Modifier.padding(bottom = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Explanation text for caregiver
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Der Frustrationsindex setzt die Zugriffszeit und die Klick-Häufigkeit in Relation. Ein hoher Wert signalisiert, dass dieser häufig genutzte Button schwer zu erreichen ist und verschoben werden sollte (z.B. in Reihe 1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
