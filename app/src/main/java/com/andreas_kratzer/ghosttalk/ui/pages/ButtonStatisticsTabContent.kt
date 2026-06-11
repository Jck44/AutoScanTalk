package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationLowLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationLowDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationMediumLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationMediumDark
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationHighLight
import com.andreas_kratzer.ghosttalk.core.ui.theme.FrustrationHighDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("UNUSED_PARAMETER")
@Composable
fun ButtonStatisticsTabContent(
    metrics: ButtonEffortMetrics?,
    historyEvents: List<ButtonUsageEvent>,
    recommendations: List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation> = emptyList(),
    onApplyRecommendation: ((com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation) -> Unit)? = null,
    buttonId: String = "",
    loadMarkovSuccessors: (suspend (String) -> List<Pair<String, Int>>)? = null,
    allPages: List<com.andreas_kratzer.ghosttalk.core.model.Page> = emptyList(),
    onNavigateToPage: ((String) -> Unit)? = null,
    onDismissDialog: (() -> Unit)? = null
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }
    val successorStats by produceState<List<Pair<String, Int>>>(initialValue = emptyList(), buttonId) {
        value = loadMarkovSuccessors?.invoke(buttonId) ?: emptyList()
    }

    val contextStats = remember(historyEvents) {
        if (historyEvents.isEmpty()) null
        else {
            val sdfDay = SimpleDateFormat("EEEE", Locale.getDefault())
            val sdfHour = SimpleDateFormat("H", Locale.getDefault())
            
            val dayCounts = historyEvents.groupBy { sdfDay.format(Date(it.timestamp)) }
                .mapValues { it.value.size }
            val topDay = dayCounts.maxByOrNull { it.value }?.key
            
            val hourCounts = historyEvents.groupBy { 
                val hour = sdfHour.format(Date(it.timestamp)).toIntOrNull() ?: 0
                val start = hour
                val end = hour + 1
                "$start:00 - $end:00 Uhr"
            }.mapValues { it.value.size }
            val topHour = hourCounts.maxByOrNull { it.value }?.key
            
            val locationEvents = historyEvents.filter { 
                // Using reflection or checking if fields exist
                try {
                    val latField = it.javaClass.getDeclaredField("latitude")
                    latField.isAccessible = true
                    latField.get(it) != null
                } catch(e: Exception) {
                    false
                }
            }
            val topLocation = if (locationEvents.isNotEmpty()) {
                "GPS Koordinaten vorhanden"
            } else {
                "Zu Hause / Indoor (Kein GPS)"
            }
            
            Triple(topDay, topHour, topLocation)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 480.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- KPI Cards Layout (Responsive) ---
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

            // --- Smart Prediction KPIs ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                ),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🧠", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Smarte Vorhersage-KPIs (Lokale Statistik)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (successorStats.isNotEmpty()) {
                        Text(
                            text = "Häufige Nachfolger-Kacheln (Markov-Kette):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalFollows = successorStats.sumOf { it.second }
                        successorStats.forEach { (label, count) ->
                            val pct = if (totalFollows > 0) (count * 100) / totalFollows else 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "➔  $label",
                                    style = if (isNarrow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$pct% ($count Klicks)",
                                    style = if (isNarrow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Noch nicht genügend Verlaufsdaten für Nachfolger-Vorhersagen vorhanden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    if (metrics != null && metrics.shortestPathRoute.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Kürzester Navigationspfad (Startseite ➔ Kachel):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                metrics.shortestPathRoute.forEachIndexed { idx, pageName ->
                                    if (idx > 0) {
                                        Text(
                                            text = "➔",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                    }
                                    
                                    val isLast = idx == metrics.shortestPathRoute.lastIndex
                                    val targetPage = if (!isLast) {
                                        allPages.find { it.name.equals(pageName, ignoreCase = true) }
                                    } else null
                                    val isClickable = targetPage != null && onNavigateToPage != null

                                    Text(
                                        text = pageName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (isClickable) TextDecoration.Underline else null
                                        ),
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.primary
                                        } else if (isClickable) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = if (isClickable) {
                                            Modifier.clickable {
                                                onNavigateToPage.invoke(targetPage.id)
                                            }
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }

                    contextStats?.let { (day, hour, loc) ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Häufigster Ort",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = loc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Aktivster Zeitraum",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (day != null && hour != null) "$day um $hour" else "--",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Häufigster Ort",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = loc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Aktivster Zeitraum",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (day != null && hour != null) "$day um $hour" else "--",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Shortcut recommendations for this button ---
            recommendations.forEach { recommendation ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("💡", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Empfohlene Abkürzung",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = stringResource(R.string.analytics_recommendation_savings, recommendation.estimatedTimeSavedSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.button_stats_recommended_shortcut_desc, recommendation.sourcePageName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )

                        Button(
                            onClick = { onApplyRecommendation?.invoke(recommendation) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.align(Alignment.End),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.button_stats_btn_create_shortcut, recommendation.sourcePageName),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // --- History / Activity list ---
            Text(
                text = "⏳ Letzte Aktivitäten (Verlauf)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (historyEvents.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Verlaufsdaten für diesen Button vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Display history entries
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    historyEvents.take(10).forEach { event ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormatter.format(Date(event.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = event.actionType,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = "Kachel-Label: ${event.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!event.geminiResponse.isNullOrBlank()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "🤖 Gemini Antwort:\n${event.geminiResponse}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

