package com.andreas_kratzer.ghosttalk.ui.pages

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    val historyEvents by pageViewModel.buttonHistory.collectAsState(emptyList())
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val recommendations by pageViewModel.shortcutRecommendations.collectAsState(emptyList())
    val userModeSessions by pageViewModel.userModeSessions.collectAsState(emptyList())

    // Aggregations & Trend calculations (Prio 2 & 4)
    val now = System.currentTimeMillis()
    val oneWeekMs = 7L * 24L * 60L * 60L * 1000L
    val twoWeeksMs = 14L * 24L * 60L * 60L * 1000L

    val currentPeriodEvents = remember(historyEvents) {
        historyEvents.filter { it.timestamp >= now - oneWeekMs }
    }
    val previousPeriodEvents = remember(historyEvents) {
        historyEvents.filter { it.timestamp in (now - twoWeeksMs)..<(now - oneWeekMs) }
    }

    val currentPeriodSessions = remember(userModeSessions) {
        userModeSessions.filter { it.startTime >= now - oneWeekMs }
    }
    val previousPeriodSessions = remember(userModeSessions) {
        userModeSessions.filter { it.startTime in (now - twoWeeksMs)..<(now - oneWeekMs) }
    }

    // Clicks KPI
    val totalClicks = historyEvents.size
    val currentClicks = currentPeriodEvents.size.toDouble()
    val previousClicks = previousPeriodEvents.size.toDouble()

    // Vocab KPI
    val activeVocabCount = remember(historyEvents) {
        historyEvents.map { it.label.trim().lowercase() }.distinct().size
    }
    val currentVocab = remember(currentPeriodEvents) {
        currentPeriodEvents.map { it.label.trim().lowercase() }.distinct().size
    }
    val previousVocab = remember(previousPeriodEvents) {
        previousPeriodEvents.map { it.label.trim().lowercase() }.distinct().size
    }

    // Usage Time KPI
    val totalUsageTimeMs = remember(userModeSessions) {
        userModeSessions.sumOf { it.endTime - it.startTime }
    }
    val currentUsageMs = remember(currentPeriodSessions) {
        currentPeriodSessions.sumOf { it.endTime - it.startTime }.toDouble()
    }
    val previousUsageMs = remember(previousPeriodSessions) {
        previousPeriodSessions.sumOf { it.endTime - it.startTime }.toDouble()
    }
    
    val totalHours = totalUsageTimeMs / (1000 * 60 * 60)
    val totalMinutes = (totalUsageTimeMs / (1000 * 60)) % 60

    // Communication Rate KPI (Buttons per Minute)
    val currentUsageMinutes = currentUsageMs / (1000.0 * 60.0)
    val currentCommRate = if (currentUsageMinutes > 0.0) currentClicks / currentUsageMinutes else 0.0

    val previousUsageMinutes = previousUsageMs / (1000.0 * 60.0)
    val previousCommRate = if (previousUsageMinutes > 0.0) previousClicks / previousUsageMinutes else 0.0
    
    // Transition flows: Page A -> Page B
    data class TransitionFlow(val from: String, val to: String, val count: Int)
    val commonTransitions = remember(historyEvents, unfilteredPages) {
        if (historyEvents.size < 2) emptyList<TransitionFlow>()
        else {
            historyEvents.zipWithNext()
                .filter { (prev, curr) ->
                    prev.pageId != null && curr.pageId != null && prev.pageId != curr.pageId &&
                    !prev.label.equals("Zurück", true) && !prev.label.equals("Startseite", true) &&
                    !curr.label.equals("Zurück", true) && !curr.label.equals("Startseite", true)
                }
                .groupBy { (prev, curr) -> Pair(prev.pageId!!, curr.pageId!!) }
                .mapNotNull { (pair, list) ->
                    val fromName = unfilteredPages.find { it.id == pair.first }?.name ?: return@mapNotNull null
                    val toName = unfilteredPages.find { it.id == pair.second }?.name ?: return@mapNotNull null
                    TransitionFlow(fromName, toName, list.size)
                }
                .sortedByDescending { it.count }
                .take(5)
        }
    }

    // Page-Level Analytics Calculations (Option B)
    data class PageUsage(val pageId: String, val name: String, val count: Int, val percentage: Float)
    val topUsedPages = remember(historyEvents, unfilteredPages) {
        val totalPagesClicks = historyEvents.filter { it.pageId != null }.size
        if (totalPagesClicks == 0) emptyList<PageUsage>()
        else {
            historyEvents.filter { it.pageId != null }
                .groupBy { it.pageId!! }
                .mapNotNull { (pageId, events) ->
                    val pageName = unfilteredPages.find { it.id == pageId }?.name ?: return@mapNotNull null
                    val pct = (events.size.toFloat() / totalPagesClicks)
                    PageUsage(pageId, pageName, events.size, pct)
                }
                .sortedByDescending { it.count }
                .take(5)
        }
    }

    val unusedPages = remember(historyEvents, unfilteredPages) {
        val usedPageIds = historyEvents.mapNotNull { it.pageId }.toSet()
        unfilteredPages.filter { !usedPageIds.contains(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text(stringResource(R.string.settings_analytics_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreR.string.back_button_content_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensions.paddingLarge)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // --- KPI OVERVIEW CARDS (2x2 Symmetrical Grid) ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Click count card
                    Card(
                        modifier = Modifier.weight(1f).height(95.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gesamtaufrufe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                TrendBadge(current = currentClicks, previous = previousClicks)
                            }
                            Text("$totalClicks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Active Vocab card
                    Card(
                        modifier = Modifier.weight(1f).height(95.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Aktiver Wortschatz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                TrendBadge(current = currentVocab.toDouble(), previous = previousVocab.toDouble())
                            }
                            Text("$activeVocabCount Wörter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Usage Time Card
                    Card(
                        modifier = Modifier.weight(1f).height(95.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.analytics_kpi_usage_time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                                TrendBadge(current = currentUsageMs, previous = previousUsageMs)
                            }
                            Text(
                                text = stringResource(R.string.analytics_kpi_usage_time_format, totalHours, totalMinutes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Communication Rate Card (Prio 4)
                    Card(
                        modifier = Modifier.weight(1f).height(95.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kommunikationsrate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                TrendBadge(current = currentCommRate, previous = previousCommRate)
                            }
                            val displayRate = String.format(java.util.Locale.US, "%.1f", currentCommRate)
                            Text(
                                text = "$displayRate Klicks/Min",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- USAGE CHART & DETAILS ---
            UsageDurationBarChart(sessions = userModeSessions)

            ReactionTimeFatigueChart(
                sessions = userModeSessions,
                historyEvents = historyEvents
            )

            UserModeSessionsSection(
                sessions = userModeSessions,
                historyEvents = historyEvents,
                onClearSessions = { pageViewModel.clearUserModeSessions() }
            )

            // Info Card
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
                        text = stringResource(R.string.analytics_dashboard_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // --- SECTION 1: SHORTCUT WIZARD ---
            Text(
                text = "🪄 Der Abkürzungs-Assistent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (recommendations.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aktuell gibt es keine empfohlenen Abkürzungen.\nSammle mehr Klicks, damit das System Muster erkennen kann.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    recommendations.forEach { recommendation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
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
                                    Text(
                                        text = stringResource(R.string.analytics_recommendation_tip_title, recommendation.targetButtonConfig.label),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
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
                                    text = stringResource(R.string.analytics_recommendation_shortcut_desc, recommendation.sourcePageName, recommendation.targetButtonConfig.label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )

                                Button(
                                    onClick = {
                                        pageViewModel.applyShortcutRecommendation(recommendation) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.align(Alignment.End),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.analytics_recommendation_btn_create, recommendation.sourcePageName),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION 2: TRANSITIONS TIMELINE ---
            Text(
                text = "🔄 Häufige Navigations-Wege",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (commonTransitions.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Noch nicht genügend Navigationsdaten vorhanden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commonTransitions.forEach { flow ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = flow.from,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "➡️",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = flow.to,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "${flow.count} Übergänge",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- SECTION 3: PAGE-LEVEL ANALYTICS & CLEANUP ASSISTANT (Option B) ---
            Text(
                text = "📄 Seiten-Analyse & Aufräum-Assistent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top Used Pages
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "📊 Meistgenutzte Seiten",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (topUsedPages.isEmpty()) {
                            Text(
                                text = "Keine Klickdaten für Seiten vorhanden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            topUsedPages.forEach { pageUsage ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pageUsage.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${pageUsage.count} Klicks (${(pageUsage.percentage * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { pageUsage.percentage },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Cleanup helper
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "⚠️ Aufräum-Empfehlungen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (unusedPages.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✅ Hervorragend! Alle Seiten in diesem Buch werden aktiv verwendet. Es gibt keine ungenutzten Seiten.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.analytics_cleanup_recommendation_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                unusedPages.take(4).forEach { page ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        Text(
                                            text = page.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (unusedPages.size > 4) {
                                    Text(
                                        text = "...und ${unusedPages.size - 4} weitere ungenutzte Seiten.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 12.dp)
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

@Composable
private fun TrendBadge(
    current: Double,
    previous: Double,
    modifier: Modifier = Modifier
) {
    if (previous <= 0.0) return // No baseline comparison available

    val percentChange = ((current - previous) / previous * 100.0)
    val isPositive = percentChange > 0.0
    val isNeutral = kotlin.math.abs(percentChange) < 0.1

    val badgeColor = when {
        isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        isPositive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when {
        isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
        isPositive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    val arrow = when {
        isNeutral -> "→"
        isPositive -> "↑"
        else -> "↓"
    }

    val formattedPercent = String.format(java.util.Locale.US, "%+.1f%%", percentChange)

    Surface(
        color = badgeColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Text(
            text = "$formattedPercent $arrow",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

