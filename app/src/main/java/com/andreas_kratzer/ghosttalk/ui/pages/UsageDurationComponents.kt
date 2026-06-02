package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UsageDurationBarChart(
    sessions: List<UserModeSession>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val locale = LocalConfiguration.current.locales[0]

    // Aggreration of last 7 days
    val dailyData = remember(sessions, locale) {
        val calendar = Calendar.getInstance()
        val data = mutableListOf<Pair<String, Long>>() // Label to duration in minutes
        
        val sdfDay = SimpleDateFormat("EE", locale)
        
        for (i in 6 downTo 0) {
            val checkCal = Calendar.getInstance()
            checkCal.add(Calendar.DAY_OF_YEAR, -i)
            
            val year = checkCal.get(Calendar.YEAR)
            val dayOfYear = checkCal.get(Calendar.DAY_OF_YEAR)
            
            val daySessions = sessions.filter { session ->
                calendar.timeInMillis = session.startTime
                calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.DAY_OF_YEAR) == dayOfYear
            }
            
            val totalMillis = daySessions.sumOf { it.endTime - it.startTime }
            val totalMinutes = totalMillis / (1000 * 60)
            
            data.add(Pair(sdfDay.format(checkCal.time), totalMinutes))
        }
        data
    }

    val maxVal = remember(dailyData) {
        val rawMax = dailyData.maxOfOrNull { it.second } ?: 0L
        if (rawMax <= 0L) 10L else ((rawMax + 9) / 10) * 10L // round up to multiple of 10
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_usage_duration_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.analytics_usage_sessions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        val leftPadding = 80f
                        val bottomPadding = 50f
                        
                        val chartWidth = canvasWidth - leftPadding
                        val chartHeight = canvasHeight - bottomPadding
                        
                        // Y-Axis lines
                        val steps = 4
                        for (i in 0..steps) {
                            val ratio = i.toFloat() / steps
                            val y = chartHeight * (1f - ratio)
                            
                            drawLine(
                                color = gridColor,
                                start = Offset(leftPadding, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 2f
                            )
                        }

                        // Drawing bars
                        val numBars = dailyData.size
                        val barSpacing = chartWidth / numBars
                        val barWidth = barSpacing * 0.5f
                        
                        dailyData.forEachIndexed { index, (dayLabel, minutes) ->
                            val ratio = minutes.toFloat() / maxVal
                            val barHeight = chartHeight * ratio
                            
                            val x = leftPadding + (index * barSpacing) + (barSpacing - barWidth) / 2
                            val y = chartHeight - barHeight
                            
                            if (barHeight > 0) {
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }
                    }

                    // Native overlays for text representation in coordinates
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${maxVal}m", style = textStyle)
                        Text("${maxVal * 3 / 4}m", style = textStyle)
                        Text("${maxVal / 2}m", style = textStyle)
                        Text("${maxVal / 4}m", style = textStyle)
                        Text("0m", style = textStyle)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 32.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        dailyData.forEach { (dayLabel, _) ->
                            Text(
                                text = dayLabel,
                                style = textStyle,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserModeSessionsSection(
    sessions: List<UserModeSession>,
    onClearSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    val sdfDate = remember(locale) { SimpleDateFormat("dd.MM.yyyy", locale) }
    val sdfTime = remember(locale) { SimpleDateFormat("HH:mm:ss", locale) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.analytics_usage_sessions_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text("${sessions.size}")
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sessions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.analytics_usage_sessions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        sessions.take(5).forEach { session ->
                            val duration = session.endTime - session.startTime
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = sdfDate.format(Date(session.startTime)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${sdfTime.format(Date(session.startTime))} - ${sdfTime.format(Date(session.endTime))}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 8.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.analytics_usage_sessions_clear),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.analytics_usage_sessions_clear)) },
            text = { Text(stringResource(R.string.analytics_usage_sessions_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSessions()
                        showClearConfirm = false
                    }
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1000) % 60
    val minutes = (durationMillis / (1000 * 60)) % 60
    val hours = durationMillis / (1000 * 60 * 60)

    return when {
        hours > 0 -> stringResource(R.string.analytics_duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.analytics_duration_minutes_seconds, minutes, seconds)
        seconds > 0 -> stringResource(R.string.analytics_duration_seconds, seconds)
        else -> stringResource(R.string.analytics_duration_less_than_minute)
    }
}
