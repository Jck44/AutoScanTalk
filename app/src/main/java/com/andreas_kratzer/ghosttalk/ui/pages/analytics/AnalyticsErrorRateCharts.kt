package com.andreas_kratzer.ghosttalk.ui.pages.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession

@Composable
fun SessionErrorRateChart(
    sessions: List<UserModeSession>,
    historyEvents: List<ButtonUsageEvent>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.error
    val pointColor = MaterialTheme.colorScheme.onErrorContainer
    val areaColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Group clicks and accidental clicks by elapsed minutes in sessions
    val errorRateData = remember(sessions, historyEvents) {
        val sessionMap = sessions.associateBy { it.id }
        
        val maxMins = historyEvents.mapNotNull { event ->
            val session = event.sessionId?.let { sessionMap[it] }
                ?: sessions.find { event.timestamp in it.startTime..it.endTime }
            if (session != null) {
                val elapsedMs = event.timestamp - session.startTime
                if (elapsedMs >= 0) elapsedMs / (1000 * 60) else null
            } else null
        }.maxOfOrNull { it } ?: 15L

        val step = when {
            maxMins <= 15 -> 3
            maxMins <= 30 -> 6
            maxMins <= 60 -> 10
            maxMins <= 120 -> 20
            else -> ((maxMins + 5) / 6).toInt().coerceAtLeast(1)
        }

        val bins = listOf(
            "0-$step min",
            "$step-${2 * step} min",
            "${2 * step}-${3 * step} min",
            "${3 * step}-${4 * step} min",
            "${4 * step}-${5 * step} min",
            "${5 * step}+ min"
        )
        val binClicks = MutableList(6) { 0 }
        val binAccidentals = MutableList(6) { 0 }

        historyEvents.forEach { event ->
            val session = event.sessionId?.let { sessionMap[it] }
                ?: sessions.find { event.timestamp in it.startTime..it.endTime }

            if (session != null) {
                val elapsedMs = event.timestamp - session.startTime
                if (elapsedMs >= 0) {
                    val elapsedMins = elapsedMs / (1000 * 60)
                    val binIndex = when {
                        elapsedMins < step -> 0
                        elapsedMins < 2 * step -> 1
                        elapsedMins < 3 * step -> 2
                        elapsedMins < 4 * step -> 3
                        elapsedMins < 5 * step -> 4
                        else -> 5
                    }
                    binClicks[binIndex]++
                    if (event.isAccidental) {
                        binAccidentals[binIndex]++
                    }
                }
            }
        }

        bins.mapIndexed { index, label ->
            val total = binClicks[index]
            val accidental = binAccidentals[index]
            val rate = if (total > 0) (accidental.toDouble() / total.toDouble()) * 100.0 else null
            Pair(label, rate)
        }
    }

    val hasData = remember(errorRateData) { errorRateData.any { it.second != null } }

    val maxVal = remember(errorRateData) {
        val rawMax = errorRateData.mapNotNull { it.second }.maxOfOrNull { it } ?: 0.0
        if (rawMax <= 0.0) 20.0 else ((rawMax.toLong() + 19) / 20) * 20.0
    }.coerceAtMost(100.0)

    val activeBins = remember(errorRateData) {
        errorRateData.mapIndexedNotNull { index, pair ->
            pair.second?.let { rate -> Triple(index, pair.first, rate) }
        }
    }

    val recommendationText = if (activeBins.size >= 2) {
        val baseline = activeBins.first().third
        // If error rate increases by more than 15% absolute or doubles, suggest fatigue
        val fatigueBin = activeBins.find { it.third >= baseline + 15.0 || (it.third >= baseline * 2.0 && it.third > 5.0) }

        if (fatigueBin != null) {
            val increasePct = (fatigueBin.third - baseline).toInt()
            val minutesLimit = fatigueBin.second.split("-").first().split("+").first()
            stringResource(
                R.string.analytics_session_error_rate_recommendation,
                fatigueBin.second,
                increasePct,
                fatigueBin.third.toInt(),
                minutesLimit
            )
        } else {
            stringResource(R.string.analytics_session_error_rate_observation)
        }
    } else {
        stringResource(R.string.analytics_session_error_rate_notice)
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
                    text = stringResource(R.string.analytics_session_error_rate_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.analytics_session_error_rate_subtitle),
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
                        text = stringResource(R.string.analytics_session_error_rate_empty),
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
                        val numBins = errorRateData.size
                        val stepX = chartWidth / (numBins - 1)

                        val points = errorRateData.mapIndexedNotNull { index, (_, rate) ->
                            if (rate != null) {
                                val ratioY = (rate / maxVal).toFloat().coerceIn(0f, 1f)
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
                        Text("${maxVal.toInt()}%", style = textStyle)
                        Text("${(maxVal * 3 / 4).toInt()}%", style = textStyle)
                        Text("${(maxVal / 2).toInt()}%", style = textStyle)
                        Text("${(maxVal / 4).toInt()}%", style = textStyle)
                        Text("0%", style = textStyle)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        errorRateData.forEach { (label, _) ->
                            Text(
                                text = label,
                                style = textStyle,
                                modifier = Modifier.width(64.dp),
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
