package com.andreas_kratzer.ghosttalk.ui.pages.analytics.buttonstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.Page
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("UNUSED_PARAMETER")
@Composable
fun ButtonStatisticsTabContent(
    metrics: ButtonEffortMetrics?,
    historyEvents: List<ButtonUsageEvent>,
    recommendations: List<ShortcutRecommendation> = emptyList(),
    onApplyRecommendation: ((ShortcutRecommendation) -> Unit)? = null,
    buttonId: String = "",
    loadMarkovSuccessors: (suspend (String) -> List<Pair<String, Int>>)? = null,
    allPages: List<Page> = emptyList(),
    onNavigateToPage: ((String) -> Unit)? = null,
    onDismissDialog: (() -> Unit)? = null
) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }
    val successorStats by produceState<List<Pair<String, Int>>>(initialValue = emptyList(), buttonId) {
        value = loadMarkovSuccessors?.invoke(buttonId) ?: emptyList()
    }

    val contextStats = remember(historyEvents) {
        ButtonStatsCalculations.computeContextStats(historyEvents)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 480.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // KPI Cards Layout (Responsive)
            ButtonKpiSection(metrics = metrics, isNarrow = isNarrow)

            // Smart Prediction KPIs
            SmartPredictionKpiSection(
                successorStats = successorStats,
                metrics = metrics,
                contextStats = contextStats,
                allPages = allPages,
                onNavigateToPage = onNavigateToPage,
                isNarrow = isNarrow
            )

            // Shortcut recommendations
            ButtonRecommendationsSection(
                recommendations = recommendations,
                onApplyRecommendation = onApplyRecommendation
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // History / Activity list
            ButtonHistorySection(
                historyEvents = historyEvents,
                dateFormatter = dateFormatter
            )
        }
    }
}
