package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                        
                        dailyData.forEachIndexed { index, (_, minutes) ->
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
    historyEvents: List<com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent>,
    onClearSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val showClearConfirm = remember { mutableStateOf(false) }
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
                            val sessionClicks = historyEvents.filter { event ->
                                event.sessionId == session.id || (event.sessionId == null && event.timestamp in session.startTime..session.endTime)
                            }.size
                            val durationMins = duration / (1000.0 * 60.0)
                            val rate = if (durationMins > 0) sessionClicks / durationMins else 0.0

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
                                        val displayRate = String.format(Locale.US, "%.1f", rate)
                                        Text(
                                            text = "$sessionClicks Klicks • $displayRate/Min",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
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
                            onClick = { showClearConfirm.value = true },
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

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text(stringResource(R.string.analytics_usage_sessions_clear)) },
            text = { Text(stringResource(R.string.analytics_usage_sessions_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSessions()
                        showClearConfirm.value = false
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
                TextButton(onClick = { showClearConfirm.value = false }) {
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

@Composable
fun ReactionTimeFatigueChart(
    sessions: List<UserModeSession>,
    historyEvents: List<com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.error
    val pointColor = MaterialTheme.colorScheme.onErrorContainer
    val areaColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Analyze fatigue reaction times grouped by elapsed minutes in sessions
    val fatigueData = remember(sessions, historyEvents) {
        val sessionMap = sessions.associateBy { it.id }
        val bins = listOf("0-3 Min", "3-6 Min", "6-9 Min", "9-12 Min", "12-15 Min", "15+ Min")
        val binValues = MutableList(6) { mutableListOf<Long>() }

        historyEvents.forEach { event ->
            val reaction = event.reactionTimeMs
            if (reaction != null && reaction > 0) {
                val session = event.sessionId?.let { sessionMap[it] }
                    ?: sessions.find { event.timestamp in it.startTime..it.endTime }

                if (session != null) {
                    val elapsedMs = event.timestamp - session.startTime
                    if (elapsedMs >= 0) {
                        val elapsedMins = elapsedMs / (1000 * 60)
                        val binIndex = when {
                            elapsedMins < 3 -> 0
                            elapsedMins < 6 -> 1
                            elapsedMins < 9 -> 2
                            elapsedMins < 12 -> 3
                            elapsedMins < 15 -> 4
                            else -> 5
                        }
                        binValues[binIndex].add(reaction)
                    }
                }
            }
        }

        bins.mapIndexed { index, label ->
            val list = binValues[index]
            val avg = if (list.isEmpty()) null else list.average()
            Pair(label, avg)
        }
    }

    val hasData = remember(fatigueData) { fatigueData.any { it.second != null } }

    val maxVal = remember(fatigueData) {
        val rawMax = fatigueData.mapNotNull { it.second }.maxOfOrNull { it } ?: 0.0
        if (rawMax <= 0.0) 2000.0 else ((rawMax.toLong() + 499) / 500) * 500.0
    }

    // Recommendation logic: find the baseline (first bin with data) and compare
    val activeBins = remember(fatigueData) {
        fatigueData.mapIndexedNotNull { index, pair ->
            pair.second?.let { avg -> Triple(index, pair.first, avg) }
        }
    }

    val recommendationText = if (activeBins.size >= 2) {
        val baseline = activeBins.first().third
        val threshold = baseline * 1.3 // 30% increase
        val fatigueBin = activeBins.find { it.third >= threshold }

        if (fatigueBin != null) {
            val percentIncrease = ((fatigueBin.third - baseline) / baseline * 100).toInt()
            val minutesLimit = fatigueBin.second.split("-").first().split("+").first()
            stringResource(
                R.string.analytics_fatigue_recommendation,
                fatigueBin.second,
                percentIncrease,
                minutesLimit
            )
        } else {
            stringResource(R.string.analytics_fatigue_observation)
        }
    } else {
        stringResource(R.string.analytics_fatigue_notice)
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
            Column {
                Text(
                    text = "⏱️ Kognitive Belastung & Reaktionszeit-Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zeigt die durchschnittliche Switch-Reaktionszeit (Fokus bis Klick) im Verlauf einer Session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Noch keine präzisen Reaktionszeiten aufgezeichnet.\nNutze die App im User-Modus mit Scanning, um Daten zu sammeln.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val leftPadding = 100f
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

                        // Drawing line
                        val numBins = fatigueData.size
                        val stepX = chartWidth / (numBins - 1)

                        val points = fatigueData.mapIndexedNotNull { index, (_, avg) ->
                            if (avg != null) {
                                val ratioY = (avg / maxVal).toFloat().coerceIn(0f, 1f)
                                val x = leftPadding + (index * stepX)
                                val y = chartHeight * (1f - ratioY)
                                Offset(x, y)
                            } else {
                                null
                            }
                        }

                        if (points.size > 1) {
                            // Draw area gradient
                            val areaPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(points.first().x, chartHeight)
                                points.forEach { lineTo(it.x, it.y) }
                                lineTo(points.last().x, chartHeight)
                                close()
                            }
                            drawPath(
                                path = areaPath,
                                color = areaColor
                            )

                            // Draw trend line
                            val linePath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                            drawPath(
                                path = linePath,
                                color = lineColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 6f,
                                    pathEffect = null
                                )
                            )

                            // Draw data points
                            points.forEach { point ->
                                drawCircle(
                                    color = lineColor,
                                    radius = 10f,
                                    center = point
                                )
                                drawCircle(
                                    color = pointColor,
                                    radius = 6f,
                                    center = point
                                )
                            }
                        } else if (points.size == 1) {
                            // Just a single point
                            drawCircle(
                                color = lineColor,
                                radius = 10f,
                                center = points.first()
                            )
                        }
                    }

                    // Native overlays for text representation in coordinates
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${maxVal.toInt()}ms", style = textStyle)
                        Text("${(maxVal * 3 / 4).toInt()}ms", style = textStyle)
                        Text("${(maxVal / 2).toInt()}ms", style = textStyle)
                        Text("${(maxVal / 4).toInt()}ms", style = textStyle)
                        Text("0ms", style = textStyle)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        fatigueData.forEach { (label, _) ->
                            Text(
                                text = label.replace(" Min", ""),
                                style = textStyle,
                                modifier = Modifier.width(48.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Recommendation Callout
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val rawRec = recommendationText
                    val parts = rawRec.split("**")
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column {
                            // Simple parser for bold parts
                            androidx.compose.foundation.text.BasicText(
                                text = androidx.compose.ui.text.buildAnnotatedString {
                                    var isBold = false
                                    parts.forEach { part ->
                                        if (isBold) {
                                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer))
                                            append(part)
                                            pop()
                                        } else {
                                            pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                            append(part)
                                            pop()
                                        }
                                        isBold = !isBold
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

