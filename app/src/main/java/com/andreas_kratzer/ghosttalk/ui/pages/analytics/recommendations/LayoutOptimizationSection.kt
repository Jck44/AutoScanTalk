package com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalFilter
import com.andreas_kratzer.ghosttalk.ui.pages.ProposalSort
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@Composable
fun LayoutOptimizationSection(
    context: Context,
    state: LayoutProposalsState,
    actions: LayoutProposalsActions
) {
    var showAllLayoutProposals by remember { mutableStateOf(false) }

    if (state.proposals.isNotEmpty() || state.currentFilter != ProposalFilter.ALL) {
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
                            when (state.currentFilter) {
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
                            actions.onSetFilter(ProposalFilter.ALL)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Aufteilungen") },
                        onClick = {
                            actions.onSetFilter(ProposalFilter.SPLIT_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Muster-Wechsel") },
                        onClick = {
                            actions.onSetFilter(ProposalFilter.PATTERN_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Startseite") },
                        onClick = {
                            actions.onSetFilter(ProposalFilter.START_PAGE_ONLY)
                            filterExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nur Kritische (>8s)") },
                        onClick = {
                            actions.onSetFilter(ProposalFilter.CRITICAL_ONLY)
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
                            when (state.currentSort) {
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
                            actions.onSetSort(ProposalSort.TIME_SAVED_DESC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Seitenname (A-Z)") },
                        onClick = {
                            actions.onSetSort(ProposalSort.PAGE_NAME_ASC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Kachel-Anzahl") },
                        onClick = {
                            actions.onSetSort(ProposalSort.BUTTONS_COUNT_DESC)
                            sortExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Aktuelle Scanzeit") },
                        onClick = {
                            actions.onSetSort(ProposalSort.CURRENT_TIME_DESC)
                            sortExpanded = false
                        }
                    )
                }
            }
        }

        if (state.proposals.isEmpty()) {
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
            val visibleProposals = if (showAllLayoutProposals) state.proposals else state.proposals.take(3)
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
                                        actions.onGeneratePageSplitProposal(proposal.pageId)
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
                                        actions.onChangePageScanPattern(proposal.pageId, "row_by_row")
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
                                        actions.onChangeScanDelay(proposal.suggestedScanDelayMs)
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
                                        actions.onApplySpacerRelocate(proposal.pageId, proposal.buttonId, proposal.intendedButtonId)
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

            if (state.proposals.size > 3) {
                TextButton(
                    onClick = { showAllLayoutProposals = !showAllLayoutProposals },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showAllLayoutProposals) {
                            stringResource(R.string.analytics_show_less)
                        } else {
                            stringResource(R.string.analytics_show_more) + " (${state.proposals.size - 3})"
                        }
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
