package com.andreas_kratzer.ghosttalk.ui.pages

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkEmptyState
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.UsageLocationRow
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.AnalyticsDetailsTab
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.AnalyticsOverviewTab
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.AnalyticsRecommendationsTab
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.AiRestructureActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.AiRestructureState
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.LayoutProposalsActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.LayoutProposalsState
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.ShortcutWizardActions
import com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations.ShortcutWizardState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

internal data class TransitionFlow(val from: String, val to: String, val count: Int)
internal data class PageUsage(val pageId: String, val name: String, val count: Int, val percentage: Float)



@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    pageViewModel: PageViewModel,
    pageSplitViewModel: PageSplitViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    bookRestructureViewModel: BookRestructureViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onEditPage: (String, Boolean) -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val locale = LocalConfiguration.current.locales[0]

    val historyEvents by pageViewModel.buttonHistory.collectAsState(emptyList())
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val recommendations by pageViewModel.shortcutRecommendations.collectAsState(emptyList())
    val isCalculatingRecommendations by pageViewModel.isCalculatingRecommendations.collectAsState()
    val userModeSessions by pageViewModel.userModeSessions.collectAsState(emptyList())
    val layoutProposals by pageSplitViewModel.layoutOptimizationProposals.collectAsState(emptyList())
    val currentFilter by pageSplitViewModel.currentProposalFilter.collectAsState()
    val currentSort by pageSplitViewModel.currentProposalSort.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val isGeminiEnabled = pageViewModel.isGeminiEnabled
    val aiProposal by bookRestructureViewModel.aiRestructureProposal.collectAsState()
    val isAiLoading by bookRestructureViewModel.isAiRestructureLoading.collectAsState()
    val aiToastApplied = stringResource(R.string.analytics_ai_toast_applied)
    val selectedPageIds by bookRestructureViewModel.selectedPageIds.collectAsState()
    val activeTargetPageIds by pageViewModel.activeTargetPageIds.collectAsState()
    val aiRestructureScope by bookRestructureViewModel.aiRestructureScope.collectAsState()
    val aiRestructureError by bookRestructureViewModel.aiRestructureError.collectAsState()

    val aiHierarchy by bookRestructureViewModel.aiHierarchyProposal.collectAsState()
    val isAiHierarchyLoading by bookRestructureViewModel.isAiHierarchyLoading.collectAsState()
    val aiPageLayouts by bookRestructureViewModel.aiPageLayoutProposals.collectAsState()
    val isLoadingPageLayout by bookRestructureViewModel.isLoadingPageLayout.collectAsState()

    val statisticsTimeframeText = remember(historyEvents, userModeSessions, locale) {
        val minEvent = historyEvents.minOfOrNull { it.timestamp } ?: Long.MAX_VALUE
        val minSession = userModeSessions.minOfOrNull { it.startTime } ?: Long.MAX_VALUE
        val minTime = minOf(minEvent, minSession)

        val maxEvent = historyEvents.maxOfOrNull { it.timestamp } ?: 0L
        val maxSession = userModeSessions.maxOfOrNull { it.endTime } ?: 0L
        val maxTime = maxOf(maxEvent, maxSession)

        if (minTime == Long.MAX_VALUE || maxTime == 0L) {
            context.getString(R.string.analytics_empty_stats)
        } else {
            val dateForm = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
            "Statistiken erfasst von ${dateForm.format(Date(minTime))} bis ${dateForm.format(Date(maxTime))}"
        }
    }

    // Aggregations & Trend calculations
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
    
    val pageToDelete = remember { mutableStateOf<Page?>(null) }
    val usagesToDelete = remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val page = pageToDelete.value
    if (page != null) {
        val usages = usagesToDelete.value
        AlertDialog(
            onDismissRequest = { 
                pageToDelete.value = null
                usagesToDelete.value = emptyList()
            },
            title = { Text(if (usages.isEmpty()) stringResource(R.string.page_dialog_delete_title) else "Seite wird verwendet") },
            text = { 
                Column {
                    if (usages.isEmpty()) {
                        Text(stringResource(R.string.page_dialog_delete_confirm, page.name))
                    } else {
                        Text("Die Seite \"${page.name}\" wird an folgenden Stellen zur Navigation verwendet:")
                        
                        val scrollState = rememberScrollState()
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .heightIn(max = 280.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Column {
                                for (usage in usages) {
                                    UsageLocationRow(usage = usage)
                                }
                            }
                        }
                        
                        Text("Beim Löschen werden auch alle Buttons entfernt, die auf diese Seite verweisen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pageViewModel.deletePage(page, deleteUsages = usages.isNotEmpty())
                        pageToDelete.value = null
                        usagesToDelete.value = emptyList()
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (usages.isEmpty()) stringResource(CoreR.string.action_delete) else "Alles Löschen")
                }
            },
            dismissButton = {
                Button(
                    onClick = { 
                        pageToDelete.value = null
                        usagesToDelete.value = emptyList()
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text(stringResource(CoreR.string.action_cancel))
                }
            }
        )
    }

    GhostTalkScaffold(
        title = stringResource(R.string.settings_analytics_dashboard),
        onNavigateBack = onNavigateBack,
        showTopBar = showTopBar
    ) { paddingValues ->
        if (historyEvents.isEmpty() && userModeSessions.isEmpty()) {
            GhostTalkEmptyState(
                icon = GhostTalkIcons.History,
                title = stringResource(R.string.analytics_empty_stats),
                description = stringResource(R.string.analytics_empty_stats_desc)
            )
        } else {
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
                        .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.screenPaddingVertical)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing)
                ) {
                when (selectedTabIndex) {
                    0 -> {
                        AnalyticsOverviewTab(
                            statisticsTimeframeText = statisticsTimeframeText,
                            totalClicks = totalClicks,
                            currentClicks = currentClicks,
                            previousClicks = previousClicks,
                            activeVocabCount = activeVocabCount,
                            currentVocab = currentVocab,
                            previousVocab = previousVocab,
                            totalHours = totalHours,
                            totalMinutes = totalMinutes,
                            currentUsageMs = currentUsageMs,
                            previousUsageMs = previousUsageMs,
                            avgDailyUsageText = avgDailyUsageText,
                            currentCommRate = currentCommRate,
                            previousCommRate = previousCommRate,
                            userModeSessions = userModeSessions,
                            historyEvents = historyEvents,
                            trendBadge = { current, previous, modifier ->
                                TrendBadge(current, previous, modifier)
                            }
                        )
                    }
                    1 -> {
                        val shortcutWizardState = remember(isCalculatingRecommendations, recommendations) {
                            ShortcutWizardState(isCalculatingRecommendations, recommendations)
                        }
                        val shortcutWizardActions = remember {
                            ShortcutWizardActions(
                                onApplyRecommendation = { rec, onRes ->
                                    pageViewModel.applyShortcutRecommendation(rec, onRes)
                                }
                            )
                        }
                        val layoutProposalsState = remember(layoutProposals, currentFilter, currentSort) {
                            LayoutProposalsState(layoutProposals, currentFilter, currentSort)
                        }
                        val layoutProposalsActions = remember {
                            LayoutProposalsActions(
                                onSetFilter = { pageSplitViewModel.setProposalFilter(it) },
                                onSetSort = { pageSplitViewModel.setProposalSort(it) },
                                onGeneratePageSplitProposal = { pageSplitViewModel.generatePageSplitProposal(it) },
                                onChangePageScanPattern = { id, pat -> pageSplitViewModel.changePageScanPattern(id, pat) },
                                onChangeScanDelay = { pageSplitViewModel.changeScanDelay(it) },
                                onApplySpacerRelocate = { pageId, b1, b2 -> pageSplitViewModel.applySpacerRelocate(pageId, b1, b2) },
                                onNavigateToEditorWithAssistant = { onEditPage(it, true) }
                            )
                        }
                        val aiRestructureState = remember(
                            aiProposal, isAiLoading, aiHierarchy, isAiHierarchyLoading,
                            aiPageLayouts, isLoadingPageLayout, unfilteredPages,
                            selectedPageIds, activeTargetPageIds, aiRestructureScope,
                            aiRestructureError, isGeminiEnabled, aiToastApplied
                        ) {
                            AiRestructureState(
                                proposal = aiProposal,
                                isLoading = isAiLoading,
                                hierarchy = aiHierarchy,
                                isHierarchyLoading = isAiHierarchyLoading,
                                pageLayouts = aiPageLayouts,
                                isLoadingPageLayout = isLoadingPageLayout,
                                unfilteredPages = unfilteredPages,
                                selectedPageIds = selectedPageIds,
                                activeTargetPageIds = activeTargetPageIds,
                                restructureScope = aiRestructureScope,
                                error = aiRestructureError,
                                isGeminiEnabled = isGeminiEnabled,
                                toastApplied = aiToastApplied
                            )
                        }
                        val aiRestructureActions = remember {
                            AiRestructureActions(
                                onSelectActivePagesOnly = { bookRestructureViewModel.selectActivePagesOnly() },
                                onSelectAllPages = { bookRestructureViewModel.selectAllPages() },
                                onTogglePageSelection = { bookRestructureViewModel.togglePageSelection(it) },
                                onSetScope = { bookRestructureViewModel.setAiRestructureScope(it) },
                                onClearError = { bookRestructureViewModel.clearAiRestructureError() },
                                onClearProposal = { bookRestructureViewModel.clearAiRestructureProposal() },
                                onGenerateHierarchyProposal = { bookRestructureViewModel.generateAiHierarchyProposal(it) },
                                onUpdateHierarchyManualEdit = { bookRestructureViewModel.updateHierarchyManualEdit(it) },
                                onLoadPageLayoutProposal = { bookRestructureViewModel.loadPageLayoutProposal(it) },
                                onLoadAllPageLayoutProposals = { bookRestructureViewModel.loadAllPageLayoutProposals(it) },
                                onApplyHierarchyProposal = { bookRestructureViewModel.applyHierarchyProposal(it) }
                            )
                        }

                        AnalyticsRecommendationsTab(
                            context = context,
                            coroutineScope = coroutineScope,
                            shortcutWizardState = shortcutWizardState,
                            shortcutWizardActions = shortcutWizardActions,
                            layoutProposalsState = layoutProposalsState,
                            layoutProposalsActions = layoutProposalsActions,
                            aiRestructureState = aiRestructureState,
                            aiRestructureActions = aiRestructureActions,
                            onNavigateBack = { onNavigateBack?.invoke() }
                        )
                    }
                    2 -> {
                        // Computed lazily here (only when the Details tab is open)
                        // so opening the dashboard on the Overview tab stays light.
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
                        AnalyticsDetailsTab(
                            commonTransitions = commonTransitions,
                            topUsedPages = topUsedPages,
                            unusedPages = unusedPages,
                            userModeSessions = userModeSessions,
                            historyEvents = historyEvents,
                            onClearSessions = { pageViewModel.clearUserModeSessions() },
                            onEditPage = { onEditPage(it, false) },
                            onDeletePageRequested = { unusedPage ->
                                coroutineScope.launch {
                                    val usages = pageViewModel.getPageUsages(unusedPage.id)
                                    usagesToDelete.value = usages
                                    pageToDelete.value = unusedPage
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
internal fun TrendBadge(
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
