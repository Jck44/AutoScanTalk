package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph

@Stable
data class StructureProblems(val orphans: Set<String>, val deadEnds: Set<String>)

@Composable
fun rememberStructureProblems(graph: BookNavigationGraph): StructureProblems =
    remember(graph) { StructureProblems(graph.orphans().toSet(), graph.deadEnds().toSet()) }

@Composable
fun WarningBadges(
    isOrphan: Boolean,
    isDeadEnd: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    if (!isOrphan && !isDeadEnd) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isOrphan) {
            val orphanDesc = stringResource(R.string.structure_warning_orphan)
            Text(
                text = "⚠",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                modifier = Modifier.semantics { contentDescription = orphanDesc }
            )
        }
        if (isDeadEnd) {
            val deadEndDesc = stringResource(R.string.structure_warning_dead_end)
            Text(
                text = "⛔",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                modifier = Modifier.semantics { contentDescription = deadEndDesc }
            )
        }
    }
}

@Composable
fun StructureLegend(
    showOrphan: Boolean,
    showDeadEnd: Boolean,
    modifier: Modifier = Modifier
) {
    if (!showOrphan && !showDeadEnd) return

    // Minimised by default so it barely occupies space; tap to reveal the meanings.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        if (!expanded) {
            // Collapsed: just the present symbol(s).
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showOrphan) {
                    Text(
                        text = "⚠",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                if (showDeadEnd) {
                    Text(
                        text = "⛔",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Expanded: symbol + meaning per present problem.
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showOrphan) {
                    LegendRow(symbol = "⚠", text = stringResource(R.string.structure_warning_orphan))
                }
                if (showDeadEnd) {
                    LegendRow(symbol = "⛔", text = stringResource(R.string.structure_warning_dead_end))
                }
            }
        }
    }
}

@Composable
private fun LegendRow(symbol: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = symbol,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
