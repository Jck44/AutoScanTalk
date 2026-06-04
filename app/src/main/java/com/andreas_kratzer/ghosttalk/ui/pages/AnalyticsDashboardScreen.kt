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
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val locale = LocalConfiguration.current.locales[0]

    val historyEvents by pageViewModel.buttonHistory.collectAsState(emptyList())
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val recommendations by pageViewModel.shortcutRecommendations.collectAsState(emptyList())
    val userModeSessions by pageViewModel.userModeSessions.collectAsState(emptyList())
    val layoutProposals by pageViewModel.layoutOptimizationProposals.collectAsState(emptyList())
    val currentFilter by pageViewModel.currentProposalFilter.collectAsState()
    val currentSort by pageViewModel.currentProposalSort.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAllRecommendations by remember { mutableStateOf(false) }
    var showAllLayoutProposals by remember { mutableStateOf(false) }
    var showAllAiActions by remember { mutableStateOf(false) }
    var showAllUnusedPages by remember { mutableStateOf(false) }

    val isGeminiEnabled = pageViewModel.settingsRepository.isGeminiEnabled
    val aiProposal by pageViewModel.aiRestructureProposal.collectAsState()
    val isAiLoading by pageViewModel.isAiRestructureLoading.collectAsState()
    val aiToastApplied = stringResource(R.string.analytics_ai_toast_applied)

    val statisticsTimeframeText = remember(historyEvents, userModeSessions, locale) {
        val minEvent = historyEvents.minOfOrNull { it.timestamp } ?: Long.MAX_VALUE
        val minSession = userModeSessions.minOfOrNull { it.startTime } ?: Long.MAX_VALUE
        val minTime = minOf(minEvent, minSession)

        val maxEvent = historyEvents.maxOfOrNull { it.timestamp } ?: 0L
        val maxSession = userModeSessions.maxOfOrNull { it.endTime } ?: 0L
        val maxTime = maxOf(maxEvent, maxSession)

        if (minTime == Long.MAX_VALUE || maxTime == 0L) {
            "Keine Statistiken erfasst"
        } else {
            val dateForm = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
            "Statistiken erfasst von ${dateForm.format(Date(minTime))} bis ${dateForm.format(Date(maxTime))}"
        }
    }

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

    val avgDailyUsageTimeMs = remember(userModeSessions) {
        if (userModeSessions.isEmpty()) 0L
        else {
            val calendar = java.util.Calendar.getInstance()
            val dailySums = userModeSessions.groupBy { session ->
                calendar.timeInMillis = session.startTime
                val year = calendar.get(java.util.Calendar.YEAR)
                val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
                "$year-$day"
            }.map { (_, sessions) ->
                sessions.sumOf { it.endTime - it.startTime }
            }
            if (dailySums.isNotEmpty()) dailySums.average().toLong() else 0L
        }
    }

    val avgDailyHours = avgDailyUsageTimeMs / (1000 * 60 * 60)
    val avgDailyMinutes = (avgDailyUsageTimeMs / (1000 * 60)) % 60
    val avgDailySeconds = (avgDailyUsageTimeMs / 1000) % 60

    val avgDailyUsageText = when {
        userModeSessions.isEmpty() -> stringResource(R.string.analytics_kpi_avg_usage_time_empty)
        avgDailyHours > 0 -> stringResource(R.string.analytics_kpi_avg_usage_time_format_hours, avgDailyHours, avgDailyMinutes)
        avgDailyMinutes > 0 -> stringResource(R.string.analytics_kpi_avg_usage_time_format_minutes, avgDailyMinutes)
        avgDailySeconds > 0 -> stringResource(R.string.analytics_kpi_avg_usage_time_format_seconds, avgDailySeconds)
        else -> stringResource(R.string.analytics_kpi_avg_usage_time_less_than_minute)
    }

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
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.analytics_tab_overview)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        val recCount = recommendations.size + layoutProposals.size + (if (aiProposal != null && !isAiLoading) aiProposal!!.actions.size else 0)
                        if (recCount > 0) {
                            Text(stringResource(R.string.analytics_tab_recommendations) + " ($recCount)")
                        } else {
                            Text(stringResource(R.string.analytics_tab_recommendations))
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text(stringResource(R.string.analytics_tab_details)) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📅", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = statisticsTimeframeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

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
                                    modifier = Modifier.weight(1f).height(105.dp),
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
                                    modifier = Modifier.weight(1f).height(105.dp),
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
                                    modifier = Modifier.weight(1f).height(105.dp),
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
                                        Column {
                                            Text(
                                                text = stringResource(R.string.analytics_kpi_usage_time_format, totalHours, totalMinutes),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                text = avgDailyUsageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                // Communication Rate Card (Prio 4)
                                Card(
                                    modifier = Modifier.weight(1f).height(105.dp),
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
                    }

                    1 -> {
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
                                val visibleRecommendations = if (showAllRecommendations) recommendations else recommendations.take(3)
                                visibleRecommendations.forEach { recommendation ->
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
                                                    pageViewModel.applyShortcutRecommendation(recommendation) { _, msg ->
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

                                if (recommendations.size > 3) {
                                    TextButton(
                                        onClick = { showAllRecommendations = !showAllRecommendations },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            text = if (showAllRecommendations) {
                                                stringResource(R.string.analytics_show_less)
                                            } else {
                                                stringResource(R.string.analytics_show_more) + " (${recommendations.size - 3})"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // --- SECTION: LAYOUT OPTIMIZATION ---
                        if (layoutProposals.isNotEmpty() || currentFilter != ProposalFilter.ALL) {
                            Text(
                                text = "⚙️ Layout-Optimierung & Struktur-Tipps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Filter & Sort Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Filter Selector
                                var filterExpanded by remember { mutableStateOf(false) }
                                Box {
                                    androidx.compose.material3.AssistChip(
                                        onClick = { filterExpanded = true },
                                        label = {
                                            Text(
                                                when (currentFilter) {
                                                    ProposalFilter.ALL -> "Filter: Alle"
                                                    ProposalFilter.SPLIT_ONLY -> "Filter: Aufteilungen"
                                                    ProposalFilter.PATTERN_ONLY -> "Filter: Muster-Wechsel"
                                                    ProposalFilter.START_PAGE_ONLY -> "Filter: Nur Startseite"
                                                    ProposalFilter.CRITICAL_ONLY -> "Filter: Nur Kritische (>8s)"
                                                }
                                            )
                                        },
                                        trailingIcon = { Text("▼") }
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = filterExpanded,
                                        onDismissRequest = { filterExpanded = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Alle Vorschläge") },
                                            onClick = {
                                                pageViewModel.setProposalFilter(ProposalFilter.ALL)
                                                filterExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Nur Aufteilungen") },
                                            onClick = {
                                                pageViewModel.setProposalFilter(ProposalFilter.SPLIT_ONLY)
                                                filterExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Nur Muster-Wechsel") },
                                            onClick = {
                                                pageViewModel.setProposalFilter(ProposalFilter.PATTERN_ONLY)
                                                filterExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Nur Startseite") },
                                            onClick = {
                                                pageViewModel.setProposalFilter(ProposalFilter.START_PAGE_ONLY)
                                                filterExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Nur Kritische (>8s)") },
                                            onClick = {
                                                pageViewModel.setProposalFilter(ProposalFilter.CRITICAL_ONLY)
                                                filterExpanded = false
                                            }
                                        )
                                    }
                                }

                                // Sort Selector
                                var sortExpanded by remember { mutableStateOf(false) }
                                Box {
                                    androidx.compose.material3.AssistChip(
                                        onClick = { sortExpanded = true },
                                        label = {
                                            Text(
                                                when (currentSort) {
                                                    ProposalSort.TIME_SAVED_DESC -> "Sortiert: Zeitgewinn"
                                                    ProposalSort.PAGE_NAME_ASC -> "Sortiert: Name A-Z"
                                                    ProposalSort.BUTTONS_COUNT_DESC -> "Sortiert: Kacheln"
                                                    ProposalSort.CURRENT_TIME_DESC -> "Sortiert: Langsamste zuerst"
                                                }
                                            )
                                        },
                                        trailingIcon = { Text("▼") }
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = sortExpanded,
                                        onDismissRequest = { sortExpanded = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Zeitlicher Gewinn") },
                                            onClick = {
                                                pageViewModel.setProposalSort(ProposalSort.TIME_SAVED_DESC)
                                                sortExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Seitenname (A-Z)") },
                                            onClick = {
                                                pageViewModel.setProposalSort(ProposalSort.PAGE_NAME_ASC)
                                                sortExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Kachel-Anzahl") },
                                            onClick = {
                                                pageViewModel.setProposalSort(ProposalSort.BUTTONS_COUNT_DESC)
                                                sortExpanded = false
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Aktuelle Scanzeit") },
                                            onClick = {
                                                pageViewModel.setProposalSort(ProposalSort.CURRENT_TIME_DESC)
                                                sortExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (layoutProposals.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Keine Vorschläge entsprechen dem aktuellen Filter.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val visibleProposals = if (showAllLayoutProposals) layoutProposals else layoutProposals.take(3)
                                visibleProposals.forEach { proposal ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (proposal) {
                                                is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> {
                                                    Text(
                                                        text = "Seite '${proposal.pageName}' ist sehr groß",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    val curTime = String.format(java.util.Locale.US, "%.1f", proposal.currentAverageScanTimeSec)
                                                    val newTime = String.format(java.util.Locale.US, "%.1f", proposal.estimatedNewAverageScanTimeSec)
                                                    Text(
                                                        text = "Diese Seite enthält ${proposal.activeButtonsCount} aktive Kacheln. Das Scannen dauert im Durchschnitt ${curTime}s. Eine Aufteilung in Unterseiten würde die mittlere Scanzeit voraussichtlich auf ${newTime}s verringern.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                    )
                                                    Button(
                                                        onClick = {
                                                            pageViewModel.generatePageSplitProposal(proposal.pageId)
                                                            Toast.makeText(context, "Aufteilungs-Vorschlag generiert! Öffne den Assistenten im Editor.", Toast.LENGTH_LONG).show()
                                                        },
                                                        modifier = Modifier.align(Alignment.End),
                                                        shape = MaterialTheme.shapes.small
                                                    ) {
                                                        Text(
                                                            text = "Aufteilungs-Assistent starten",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> {
                                                    Text(
                                                        text = "Scan-Muster optimieren für '${proposal.pageName}'",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    val curTime = String.format(java.util.Locale.US, "%.1f", proposal.currentAverageScanTimeSec)
                                                    val newTime = String.format(java.util.Locale.US, "%.1f", proposal.estimatedNewAverageScanTimeSec)
                                                    Text(
                                                        text = "Diese Seite hat ${proposal.activeButtonsCount} Kacheln und scannt linear (durchschnittlich ${curTime}s). Durch die Umstellung auf zeilenweises Scannen verringert sich die Wartezeit auf ${newTime}s.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                    )
                                                    Button(
                                                        onClick = {
                                                            pageViewModel.changePageScanPattern(proposal.pageId, "row_by_row")
                                                            Toast.makeText(context, "Scan-Muster für '${proposal.pageName}' erfolgreich auf zeilenweise geändert.", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.align(Alignment.End),
                                                        shape = MaterialTheme.shapes.small
                                                    ) {
                                                        Text(
                                                            text = "Auf zeilenweise umstellen",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (layoutProposals.size > 3) {
                                    TextButton(
                                        onClick = { showAllLayoutProposals = !showAllLayoutProposals },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            text = if (showAllLayoutProposals) {
                                                stringResource(R.string.analytics_show_less)
                                            } else {
                                                stringResource(R.string.analytics_show_more) + " (${layoutProposals.size - 3})"
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                        }

                        // --- SECTION: AI BOOK RESTRUCTURING (Gemini) ---

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.analytics_ai_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (aiProposal != null && !isAiLoading) {
                                TextButton(onClick = { pageViewModel.clearAiRestructureProposal() }) {
                                    Text(stringResource(R.string.analytics_ai_reset))
                                }
                            }
                        }

                        if (!isGeminiEnabled) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.analytics_ai_disabled_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = stringResource(R.string.analytics_ai_disabled_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (isAiLoading) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = stringResource(R.string.analytics_ai_loading_text),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (aiProposal == null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.analytics_ai_intro_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.analytics_ai_intro_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { pageViewModel.generateAiRestructureProposal() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.analytics_ai_btn_calculate))
                                    }
                                }
                            }
                        } else {
                            val proposal = aiProposal!!
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (proposal.actions.isEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = stringResource(R.string.analytics_ai_no_proposals),
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val visibleAiActions = if (showAllAiActions) proposal.actions else proposal.actions.take(3)
                                    visibleAiActions.forEach { action ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val actionTitle = when (action.type) {
                                                    "MOVE_BUTTON" -> stringResource(R.string.analytics_ai_action_move, action.buttonLabel ?: "")
                                                    "DEACTIVATE_BUTTON" -> stringResource(R.string.analytics_ai_action_deactivate, action.buttonLabel ?: "")
                                                    "SPLIT_PAGE" -> stringResource(R.string.analytics_ai_action_split, action.sourcePageName ?: "")
                                                    else -> stringResource(R.string.analytics_ai_action_default)
                                                }
                                                val icon = when (action.type) {
                                                    "MOVE_BUTTON" -> "📦"
                                                    "DEACTIVATE_BUTTON" -> "🗑️"
                                                    "SPLIT_PAGE" -> "✂️"
                                                    else -> "⚙️"
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(icon, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        text = actionTitle,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Text(
                                                    text = action.rationale,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                when (action.type) {
                                                    "MOVE_BUTTON" -> {
                                                        Text(
                                                            text = stringResource(R.string.analytics_ai_details_move, action.sourcePageName ?: "", action.targetPageName ?: ""),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                    "SPLIT_PAGE" -> {
                                                        action.newCategories?.forEach { cat ->
                                                            Text(
                                                                text = stringResource(R.string.analytics_ai_details_split, cat.name, cat.buttonLabels.joinToString(", ")),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.padding(start = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (proposal.actions.size > 3) {
                                        TextButton(
                                            onClick = { showAllAiActions = !showAllAiActions },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Text(
                                                text = if (showAllAiActions) {
                                                    stringResource(R.string.analytics_show_less)
                                                } else {
                                                    stringResource(R.string.analytics_show_more) + " (${proposal.actions.size - 3})"
                                                }
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            pageViewModel.applyAiRestructureProposal(proposal) { _ ->
                                                Toast.makeText(context, aiToastApplied, Toast.LENGTH_LONG).show()
                                                onNavigateBack()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.analytics_ai_btn_save_test))
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
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
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.analytics_cleanup_recommendation_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val visibleUnusedPages = if (showAllUnusedPages) unusedPages else unusedPages.take(4)
                                            visibleUnusedPages.forEach { page ->
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
                                                TextButton(
                                                    onClick = { showAllUnusedPages = !showAllUnusedPages },
                                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                                ) {
                                                    Text(
                                                        text = if (showAllUnusedPages) {
                                                            stringResource(R.string.analytics_show_less)
                                                        } else {
                                                            stringResource(R.string.analytics_show_more) + " (${unusedPages.size - 4})"
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        UserModeSessionsSection(
                            sessions = userModeSessions,
                            historyEvents = historyEvents,
                            onClearSessions = { pageViewModel.clearUserModeSessions() }
                        )
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

