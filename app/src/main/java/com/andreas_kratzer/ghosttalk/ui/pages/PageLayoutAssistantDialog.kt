package com.andreas_kratzer.ghosttalk.ui.pages

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import kotlin.math.roundToInt

@Composable
fun PageLayoutAssistantDialog(
    page: Page,
    pageSplitViewModel: PageSplitViewModel,
    onStartPageSplit: () -> Unit,
    onStartMagicCleanup: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val proposals by pageSplitViewModel.layoutOptimizationProposals.collectAsState()
    val pageProposals = remember(proposals, page.id) {
        proposals.filter { it.pageId == page.id }
    }

    var homeInterval by remember { mutableFloatStateOf(5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = GhostTalkIcons.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Layout- & Struktur-Assistent",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Optimiere das Layout der Seite '${page.name}' automatisch anhand von Nutzungsdaten und Richtlinien für Unterstützte Kommunikation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // MAGIC CLEAN UP CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = GhostTalkIcons.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Magische Bereinigung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "Führt eine vollautomatische Optimierung durch: Inaktive Buttons löschen, nach Klicks sortieren, Startseite alle 5 Buttons verteilen, Raster minimieren, Scan-Muster optimieren und Zeilennamen generieren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = {
                                onStartMagicCleanup()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Bereinigung starten", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider()

                // SECTION 1: STATISTIC RECOMMENDATIONS (IF ANY)
                if (pageProposals.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "💡 Empfehlungen für diese Seite",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        pageProposals.forEach { proposal ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (proposal) {
                                        is LayoutOptimizationProposal.SplitPageProposal -> {
                                            Text(
                                                text = "Seite ist sehr voll",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            val curTime = String.format(java.util.Locale.US, "%.1f", proposal.currentAverageScanTimeSec)
                                            val newTime = String.format(java.util.Locale.US, "%.1f", proposal.estimatedNewAverageScanTimeSec)
                                            Text(
                                                text = "Diese Seite enthält ${proposal.activeButtonsCount} aktive Kacheln. Mittlere Scanzeit: ${curTime}s. Eine Aufteilung in Unterseiten würde diese voraussichtlich auf ${newTime}s verringern.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                            Button(
                                                onClick = {
                                                    onDismiss()
                                                    onStartPageSplit()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.align(Alignment.End),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text("Aufteilungs-Assistent starten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        is LayoutOptimizationProposal.ChangeScanPatternProposal -> {
                                            Text(
                                                text = "Scan-Muster optimieren",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            val curTime = String.format(java.util.Locale.US, "%.1f", proposal.currentAverageScanTimeSec)
                                            val newTime = String.format(java.util.Locale.US, "%.1f", proposal.estimatedNewAverageScanTimeSec)
                                            Text(
                                                text = "Die Seite scannt linear (${curTime}s). Die Umstellung auf zeilenweises Scannen verringert die Wartezeit auf ${newTime}s.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                            Button(
                                                onClick = {
                                                    pageSplitViewModel.changePageScanPattern(proposal.pageId, "row_by_row")
                                                    Toast.makeText(context, "Scan-Muster erfolgreich auf zeilenweise geändert.", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.align(Alignment.End),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text("Auf zeilenweise umstellen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        is LayoutOptimizationProposal.ChangeScanDelayProposal -> {
                                            val isSlowingDown = proposal.suggestedScanDelayMs > proposal.currentScanDelayMs
                                            val title = if (isSlowingDown) "Scan-Geschwindigkeit verlangsamen" else "Scan-Geschwindigkeit erhöhen"
                                            val ratePct = (proposal.lateClickRate * 100).toInt()
                                            val desc = if (isSlowingDown) {
                                                "Der Benutzer klickt häufig zu spät (${ratePct}% Fehlklicks). Erhöhen der Verzögerung von ${proposal.currentScanDelayMs}ms auf ${proposal.suggestedScanDelayMs}ms gibt mehr Zeit zum Reagieren."
                                            } else {
                                                "Der Benutzer reagiert sehr schnell. Verringern der Verzögerung von ${proposal.currentScanDelayMs}ms auf ${proposal.suggestedScanDelayMs}ms beschleunigt das System."
                                            }
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                            Button(
                                                onClick = {
                                                    pageSplitViewModel.changeScanDelay(proposal.suggestedScanDelayMs)
                                                    Toast.makeText(context, "Scan-Verzögerung auf ${proposal.suggestedScanDelayMs}ms geändert.", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.align(Alignment.End),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(if (isSlowingDown) "Verlangsamen" else "Beschleunigen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        is LayoutOptimizationProposal.SpacerRelocateProposal -> {
                                            Text(
                                                text = "Tausch-Optimierung",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Der Benutzer klickt häufig unabsichtlich auf '${proposal.buttonLabel}' statt '${proposal.intendedButtonLabel}' (${proposal.accidentalClickCount} Fehlklicks). Ein Positionstausch verringert Fehler.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                            Button(
                                                onClick = {
                                                    pageSplitViewModel.applySpacerRelocate(proposal.pageId, proposal.buttonId, proposal.intendedButtonId)
                                                    Toast.makeText(context, "Kacheln getauscht!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.align(Alignment.End),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text("Kacheln tauschen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // SECTION 2: GENERAL OPTIMIZATION TOOLS
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "⚙️ Werkzeuge",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Card 1: Reorder Buttons
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = GhostTalkIcons.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nach Klicks sortieren",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Platziert häufig genutzte Tasten am Anfang der Seite (oben links), um die Scanning-Wartezeit zu verringern.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pageSplitViewModel.reorderByClickStats(page.id) {
                                        Toast.makeText(context, "Buttons nach Klicks sortiert und neu angeordnet.", Toast.LENGTH_LONG).show()
                                    }
                                    onDismiss()
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Sortieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Card 2: Insert Home Navigation
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Startseiten-Navigation verteilen",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Fügt nach jeweils X Buttons eine 'Startseite'-Kachel ein und bereinigt alte Startseiten-Verweise.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Intervall: Alle ${homeInterval.roundToInt()} Buttons",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Slider(
                                        value = homeInterval,
                                        onValueChange = { homeInterval = it },
                                        valueRange = 2f..10f,
                                        steps = 7,
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        pageSplitViewModel.insertHomeNavigationEveryX(page.id, homeInterval.roundToInt()) {
                                            Toast.makeText(context, "Startseiten-Buttons alle ${homeInterval.roundToInt()} Kacheln verteilt.", Toast.LENGTH_LONG).show()
                                        }
                                        onDismiss()
                                    },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("Verteilen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Card 3: Shrink Grid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = GhostTalkIcons.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rastergröße minimieren",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Schrumpft das Raster (Zeilen/Spalten) auf das Minimum, um leere Kacheln vollständig zu eliminieren.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pageSplitViewModel.shrinkGridToMinimum(page.id) {
                                        Toast.makeText(context, "Rastergröße auf das Minimum geschrumpft.", Toast.LENGTH_LONG).show()
                                    }
                                    onDismiss()
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Minimieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Card 4: Delete Deactivated
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Deaktivierte Buttons löschen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Entfernt alle inaktiven (deaktivierten) Kacheln dauerhaft von der Seite.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pageSplitViewModel.deleteDeactivatedButtons(page.id) {
                                        Toast.makeText(context, "Deaktivierte Buttons gelöscht.", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Löschen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}
