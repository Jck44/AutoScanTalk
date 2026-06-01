package com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsPrefetchDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val dimensions = LocalDimensions.current
    val pages by viewModel.allPages.collectAsState()
    val selectedPages by viewModel.selectedPagesForPrefetch.collectAsState()
    val stats by viewModel.prefetchStats.collectAsState()
    val isPrefetching by viewModel.isPrefetching.collectAsState()
    val progress by viewModel.prefetchProgress.collectAsState()
    val currentCount by viewModel.prefetchCurrentCount.collectAsState()
    val totalCount by viewModel.prefetchTotalCount.collectAsState()
    val currentText by viewModel.currentPrefetchText.collectAsState()

    Dialog(
        onDismissRequest = { if (!isPrefetching) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(dimensions.paddingMedium))
                    Text(
                        text = stringResource(R.string.prefetch_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.paddingLarge))

                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = when {
                            isPrefetching -> 2
                            stats != null -> 1
                            else -> 0
                        },
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "PrefetchStepTransition"
                    ) { step ->
                        when (step) {
                            0 -> PageSelectionContent(
                                pages = pages,
                                selectedPages = selectedPages,
                                onToggle = viewModel::togglePageSelectionForPrefetch,
                                onSelectAll = { viewModel.selectAllPagesForPrefetch(pages) },
                                onDeselectAll = viewModel::deselectAllPagesForPrefetch,
                                onCalculate = { viewModel.calculatePrefetchStats(pages) },
                                dimensions = dimensions
                            )
                            1 -> {
                                val currentStats = stats
                                if (currentStats != null) {
                                    PrefetchStatsContent(
                                        stats = currentStats,
                                        onStart = { viewModel.startPrefetch(pages) },
                                        onBack = { viewModel.deselectAllPagesForPrefetch() },
                                        dimensions = dimensions
                                    )
                                } else {
                                    // Fallback if stats becomes null during transition
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                            2 -> PrefetchProgressContent(
                                progress = progress,
                                currentCount = currentCount,
                                totalCount = totalCount,
                                currentText = currentText,
                                onCancel = viewModel::cancelPrefetch,
                                dimensions = dimensions
                            )
                        }
                    }
                }

                if (!isPrefetching) {
                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSelectionContent(
    pages: List<com.andreas_kratzer.ghosttalk.core.model.Page>,
    selectedPages: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCalculate: () -> Unit,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.prefetch_dialog_select_pages),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.prefetch_select_all))
                }
                TextButton(onClick = onDeselectAll) {
                    Text(stringResource(R.string.prefetch_deselect_all))
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(pages) { page ->
                val isSelected = selectedPages.contains(page.id)
                Surface(
                    onClick = { onToggle(page.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensions.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggle(page.id) }
                        )
                        Text(
                            text = page.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(dimensions.paddingLarge))

        Button(
            onClick = onCalculate,
            enabled = selectedPages.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(GhostTalkIcons.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_confirm),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun PrefetchStatsContent(
    stats: com.andreas_kratzer.ghosttalk.core.model.PrefetchStats,
    onStart: () -> Unit,
    onBack: () -> Unit,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.prefetch_stats_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.paddingMedium)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                StatRow(GhostTalkIcons.Description, stringResource(R.string.prefetch_stats_unique, stats.uniqueStrings))
                StatRow(GhostTalkIcons.Copy, stringResource(R.string.prefetch_stats_duplicates, stats.duplicateStrings))
                StatRow(GhostTalkIcons.RecordVoiceOver, stringResource(R.string.prefetch_stats_words, stats.totalWords))
                StatRow(GhostTalkIcons.Edit, stringResource(R.string.prefetch_stats_chars, stats.totalCharacters))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingMedium))
                
                StatRow(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.prefetch_stats_cached, stats.alreadyCached),
                    tint = if (stats.alreadyCached > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(dimensions.paddingLarge))

        val needsDownload = stats.uniqueStrings > stats.alreadyCached
        
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = needsDownload,
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.prefetch_start),
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(dimensions.paddingSmall))
        
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_back),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun PrefetchProgressContent(
    progress: Float,
    currentCount: Int,
    totalCount: Int,
    currentText: String?,
    onCancel: () -> Unit,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "PrefetchProgressAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(120.dp),
            strokeWidth = 10.dp,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(dimensions.paddingLarge))
        
        Text(
            text = stringResource(R.string.prefetch_in_progress),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = stringResource(R.string.prefetch_progress_count, currentCount, totalCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(dimensions.paddingExtraLarge))
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensions.paddingLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentText ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.prefetch_cancel),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
