@file:Suppress("DEPRECATION", "UNUSED_VALUE", "ASSIGNED_VALUE_IS_NEVER_READ", "UNUSED_PARAMETER")
package com.andreas_kratzer.ghosttalk.ui.pages.analytics

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalFilter
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsRecommendationsTab(
    context: Context,
    coroutineScope: CoroutineScope,
    isCalculatingRecommendations: Boolean,
    recommendations: List<ShortcutRecommendation>,
    layoutProposals: List<LayoutOptimizationProposal>,
    currentFilter: ProposalFilter,
    currentSort: ProposalSort,
    aiProposal: BookRestructureProposal?,
    isAiLoading: Boolean,
    aiHierarchy: BookHierarchyProposal?,
    isAiHierarchyLoading: Boolean,
    aiPageLayouts: Map<String, PageLayoutProposal>,
    isLoadingPageLayout: Map<String, Boolean>,
    unfilteredPages: List<Page>,
    selectedPageIds: Set<String>,
    activeTargetPageIds: Set<String>,
    aiRestructureScope: String,
    aiRestructureError: String?,
    isGeminiEnabled: Boolean,
    aiToastApplied: String,
    onNavigateBack: () -> Unit,
    // ViewModel Lambda Triggers
    onApplyShortcutRecommendation: (ShortcutRecommendation, (Boolean, String) -> Unit) -> Unit,
    onSetProposalFilter: (ProposalFilter) -> Unit,
    onSetProposalSort: (ProposalSort) -> Unit,
    onGeneratePageSplitProposal: (String) -> Unit,
    onChangePageScanPattern: (String, String) -> Unit,
    onChangeScanDelay: (Long) -> Unit,
    onApplySpacerRelocate: (String, String, String) -> Unit,
    onSelectActivePagesOnly: () -> Unit,
    onSelectAllPages: () -> Unit,
    onTogglePageSelection: (String) -> Unit,
    onSetAiRestructureScope: (String) -> Unit,
    onClearAiRestructureError: () -> Unit,
    onClearAiRestructureProposal: () -> Unit,
    onGenerateAiHierarchyProposal: (String?) -> Unit,
    onUpdateHierarchyManualEdit: (BookHierarchyProposal) -> Unit,
    onLoadPageLayoutProposal: (String) -> Unit,
    onLoadAllPageLayoutProposals: (() -> Unit) -> Unit,
    onApplyHierarchyProposal: ((String) -> Unit) -> Unit
) {
    var showAllRecommendations by remember { mutableStateOf(false) }
    var showAllLayoutProposals by remember { mutableStateOf(false) }

    // --- SECTION 1: SHORTCUT WIZARD ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🪄 Der Abkürzungs-Assistent",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (isCalculatingRecommendations && recommendations.isNotEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (recommendations.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.small
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isCalculatingRecommendations) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Berechne Abkürzungen...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Aktuell gibt es keine empfohlenen Abkürzungen.\nSammle mehr Klicks, damit das System Muster erkennen kann.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
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
                                onApplyShortcutRecommendation(recommendation) { _, msg ->
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
                AssistChip(
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
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle Vorschläge") },
                        onClick = {
                            onSetProposalFilter(ProposalFilter.ALL)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Aufteilungen") },
                        onClick = {
                            onSetProposalFilter(ProposalFilter.SPLIT_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Muster-Wechsel") },
                        onClick = {
                            onSetProposalFilter(ProposalFilter.PATTERN_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Startseite") },
                        onClick = {
                            onSetProposalFilter(ProposalFilter.START_PAGE_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Kritische (>8s)") },
                        onClick = {
                            onSetProposalFilter(ProposalFilter.CRITICAL_ONLY)
                            filterExpanded = false
                        }
                    )
                }
            }

            // Sort Selector
            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                AssistChip(
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
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Zeitlicher Gewinn") },
                        onClick = {
                            onSetProposalSort(ProposalSort.TIME_SAVED_DESC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Seitenname (A-Z)") },
                        onClick = {
                            onSetProposalSort(ProposalSort.PAGE_NAME_ASC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Kachel-Anzahl") },
                        onClick = {
                            onSetProposalSort(ProposalSort.BUTTONS_COUNT_DESC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Aktuelle Scanzeit") },
                        onClick = {
                            onSetProposalSort(ProposalSort.CURRENT_TIME_DESC)
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
                            is LayoutOptimizationProposal.SplitPageProposal -> {
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
                                        onGeneratePageSplitProposal(proposal.pageId)
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
                            is LayoutOptimizationProposal.ChangeScanPatternProposal -> {
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
                                        onChangePageScanPattern(proposal.pageId, "row_by_row")
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
                            is LayoutOptimizationProposal.ChangeScanDelayProposal -> {
                                val isSlowingDown = proposal.suggestedScanDelayMs > proposal.currentScanDelayMs
                                val title = if (isSlowingDown) {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_slowing_title)
                                } else {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_speeding_title)
                                }
                                val ratePct = (proposal.lateClickRate * 100).toInt()
                                val detailText = if (isSlowingDown) {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_slowing_desc, proposal.pageName, ratePct, proposal.currentScanDelayMs, proposal.suggestedScanDelayMs)
                                } else {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_speeding_desc, proposal.pageName, proposal.currentScanDelayMs, proposal.suggestedScanDelayMs)
                                }
                                val btnText = if (isSlowingDown) {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_slowing_btn)
                                } else {
                                    context.getString(SettingsR.string.settings_scan_delay_proposal_speeding_btn)
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = detailText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                                Button(
                                    onClick = {
                                        onChangeScanDelay(proposal.suggestedScanDelayMs)
                                        Toast.makeText(context, context.getString(SettingsR.string.settings_scan_delay_proposal_toast, proposal.suggestedScanDelayMs), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = btnText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is LayoutOptimizationProposal.SpacerRelocateProposal -> {
                                Text(
                                    text = "Tausch-Optimierung für '${proposal.pageName}'",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Der Benutzer klickt häufig unabsichtlich auf '${proposal.buttonLabel}' (${proposal.accidentalClickCount} Mal) statt '${proposal.intendedButtonLabel}'. Ein Tausch der Positionen beider Kacheln verringert Fehlklicks.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                                Button(
                                    onClick = {
                                        onApplySpacerRelocate(proposal.pageId, proposal.buttonId, proposal.intendedButtonId)
                                        Toast.makeText(context, "Kacheln '${proposal.buttonLabel}' und '${proposal.intendedButtonLabel}' erfolgreich getauscht!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "Kacheln tauschen",
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

    var showPageSelectionDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<HierarchyPageNode?>(null) }
    var editingNodeNewName by remember { mutableStateOf("") }
    var editingNodeNewDesc by remember { mutableStateOf("") }
    var editingNodeNewSubpages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTokenWarningDialog by remember { mutableStateOf(false) }

    if (showPageSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showPageSelectionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.analytics_ai_page_selection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { onSelectActivePagesOnly() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.analytics_ai_only_active_pages))
                        }
                        TextButton(
                            onClick = { onSelectAllPages() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.analytics_ai_all_pages))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        unfilteredPages.forEach { page ->
                            val isSelected = selectedPageIds.contains(page.id)
                            val isActive = activeTargetPageIds.contains(page.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTogglePageSelection(page.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onTogglePageSelection(page.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = page.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val badgeText = if (isActive) {
                                        stringResource(R.string.analytics_ai_active_badge)
                                    } else {
                                        stringResource(R.string.analytics_ai_inactive_badge)
                                    }
                                    val badgeColor = if (isActive) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    }
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSelectionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

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
            TextButton(onClick = { onClearAiRestructureProposal() }) {
                Text(stringResource(R.string.analytics_ai_reset))
            }
        }
    }

    if (isGeminiEnabled) {
        if (aiRestructureError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = aiRestructureError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onClearAiRestructureError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showTokenWarningDialog) {
            AlertDialog(
                onDismissRequest = { showTokenWarningDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.analytics_ai_token_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.analytics_ai_token_warning_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onSetAiRestructureScope("full")
                            showTokenWarningDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showTokenWarningDialog = false
                        }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_ai_scope_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val scopes = listOf(
                    "quick" to R.string.analytics_ai_scope_quick,
                    "detailed" to R.string.analytics_ai_scope_detailed,
                    "full" to R.string.analytics_ai_scope_full
                )
                scopes.forEach { (scopeKey, labelRes) ->
                    val isSelected = aiRestructureScope == scopeKey
                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (scopeKey == "full") {
                                    showTokenWarningDialog = true
                                } else {
                                    onSetAiRestructureScope(scopeKey)
                                }
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = containerColor,
                        contentColor = contentColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        val selectedPageCount = selectedPageIds.size
        val totalPageCount = unfilteredPages.size
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.analytics_ai_page_selection_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.analytics_ai_btn_select_pages, selectedPageCount, totalPageCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = { showPageSelectionDialog = true },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Auswählen")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        val hierarchy = aiHierarchy
        if (hierarchy == null) {
            Button(
                onClick = { onGenerateAiHierarchyProposal(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAiHierarchyLoading
            ) {
                if (isAiHierarchyLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Berechne Struktur...")
                } else {
                    Text("Gesamtkonzept erstellen")
                }
            }
        } else {
            if (editingNode != null) {
                val node = editingNode!!
                AlertDialog(
                    onDismissRequest = { editingNode = null },
                    title = { Text("Seite bearbeiten") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editingNodeNewName,
                                onValueChange = { editingNodeNewName = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editingNodeNewDesc,
                                onValueChange = { editingNodeNewDesc = it },
                                label = { Text("Beschreibung / Begründung") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Text("Unterseiten verknüpfen:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            val otherPages = hierarchy.pages.filter { it.name != node.name && it.name != "Hauptseite" }
                            Column(
                                modifier = Modifier.height(150.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                otherPages.forEach { other ->
                                    val isLinked = editingNodeNewSubpages.contains(other.name)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            editingNodeNewSubpages = if (isLinked) {
                                                editingNodeNewSubpages - other.name
                                            } else {
                                                editingNodeNewSubpages + other.name
                                            }
                                        },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isLinked,
                                            onCheckedChange = {
                                                editingNodeNewSubpages = if (isLinked) {
                                                    editingNodeNewSubpages - other.name
                                                } else {
                                                    editingNodeNewSubpages + other.name
                                                }
                                            }
                                        )
                                        Text(other.name)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val updatedPages = hierarchy.pages.map { page ->
                                    if (page.name == node.name) {
                                        page.copy(
                                            name = editingNodeNewName,
                                            description = editingNodeNewDesc,
                                            subpages = editingNodeNewSubpages.toList()
                                        )
                                    } else {
                                        val newSubpages = page.subpages.map { subName ->
                                            if (subName == node.name) editingNodeNewName else subName
                                        }
                                        page.copy(subpages = newSubpages)
                                    }
                                }
                                onUpdateHierarchyManualEdit(BookHierarchyProposal(updatedPages))
                                editingNode = null
                            }
                        ) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingNode = null }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                hierarchy.pages.forEach { node ->
                    val isPageLoading = isLoadingPageLayout[node.name] == true
                    val layoutProposal = aiPageLayouts[node.name]
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!node.sourcePageName.isNullOrBlank()) {
                                        Text(
                                            text = "(Original: ${node.sourcePageName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingNode = node
                                            editingNodeNewName = node.name
                                            editingNodeNewDesc = node.description
                                            editingNodeNewSubpages = node.subpages.toSet()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Bearbeiten",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    if (node.name != "Hauptseite") {
                                        IconButton(
                                            onClick = {
                                                val updatedList = hierarchy.pages.filter { it.name != node.name }
                                                    .map { page ->
                                                        page.copy(subpages = page.subpages.filter { it != node.name })
                                                    }
                                                onUpdateHierarchyManualEdit(BookHierarchyProposal(updatedList))
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Löschen",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                text = node.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (node.subpages.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Unterseiten:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    node.subpages.forEach { sub ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(sub, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (isPageLoading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Lade Knopflayout...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (layoutProposal != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.analytics_ai_layout_loaded, layoutProposal.actions.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    
                                    layoutProposal.actions.take(4).forEach { action ->
                                        val icon = when (action.type) {
                                            "MOVE_BUTTON" -> "📦"
                                            "CREATE_NAV_BUTTON" -> "➡️"
                                            else -> "⚙️"
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(icon, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = if (action.type == "MOVE_BUTTON") {
                                                    "Verschiebe „${action.buttonLabel}“ von „${action.sourcePageName ?: "Unbekannt"}“"
                                                } else {
                                                    "Navigationsknopf „${action.buttonLabel}“ zu „${action.targetPageName}“ erstellen"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Knopf-Belegung ausstehend",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Button(
                                        onClick = { onLoadPageLayoutProposal(node.name) },
                                        shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text(stringResource(R.string.analytics_ai_load_layout))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val allLayoutsLoaded = hierarchy.pages.all { aiPageLayouts.containsKey(it.name) }
                
                if (!allLayoutsLoaded) {
                    Button(
                        onClick = { onLoadAllPageLayoutProposals {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !isAiHierarchyLoading && isLoadingPageLayout.isEmpty()
                    ) {
                        Text(stringResource(R.string.analytics_ai_load_all_layouts))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                var feedbackText by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AI-Konzept verfeinern (Feedback)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            placeholder = { Text("Z.B.: 'Ernährung in Essen und Trinken aufteilen'") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                        Button(
                            onClick = {
                                onGenerateAiHierarchyProposal(feedbackText)
                                feedbackText = ""
                            },
                            enabled = feedbackText.isNotBlank() && !isAiHierarchyLoading,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Neu berechnen mit Feedback")
                        }
                    }
                }
                
                var isSavingAndLoadingLayouts by remember { mutableStateOf(false) }
                var savingProgressText by remember { mutableStateOf("") }
                
                if (isSavingAndLoadingLayouts) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("Struktur anwenden...") },
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator()
                                Text(savingProgressText)
                            }
                        }
                    )
                }
                
                Button(
                    onClick = {
                        val missingNodes = hierarchy.pages.filter { !aiPageLayouts.containsKey(it.name) }
                        if (missingNodes.isNotEmpty()) {
                            isSavingAndLoadingLayouts = true
                            coroutineScope.launch {
                                onApplyHierarchyProposal { msg ->
                                    isSavingAndLoadingLayouts = false
                                    Toast.makeText(context, aiToastApplied, Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                onApplyHierarchyProposal { _ ->
                                    Toast.makeText(context, aiToastApplied, Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isAiHierarchyLoading && !isAiLoading
                ) {
                    Text(stringResource(R.string.analytics_ai_btn_save_test))
                }
            }
        }
    }
}
