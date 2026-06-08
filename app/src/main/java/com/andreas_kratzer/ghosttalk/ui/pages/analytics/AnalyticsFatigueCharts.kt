package com.andreas_kratzer.ghosttalk.ui.pages.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
fun ReactionTimeFatigueChart(
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

    // Analyze fatigue reaction times grouped by elapsed minutes in sessions
    val fatigueData = remember(sessions, historyEvents) {
        val sessionMap = sessions.associateBy { it.id }
        
        val maxMins = historyEvents.mapNotNull { event ->
            val reaction = event.reactionTimeMs
            if (reaction != null && reaction > 0) {
                val session = event.sessionId?.let { sessionMap[it] }
                    ?: sessions.find { event.timestamp in it.startTime..it.endTime }
                if (session != null) {
                    val elapsedMs = event.timestamp - session.startTime
                    if (elapsedMs >= 0) elapsedMs / (1000 * 60) else null
                } else null
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
                            elapsedMins < step -> 0
                            elapsedMins < 2 * step -> 1
                            elapsedMins < 3 * step -> 2
                            elapsedMins < 4 * step -> 3
                            elapsedMins < 5 * step -> 4
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
