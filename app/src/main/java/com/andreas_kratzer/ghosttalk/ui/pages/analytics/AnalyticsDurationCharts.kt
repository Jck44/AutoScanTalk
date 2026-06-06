package com.andreas_kratzer.ghosttalk.ui.pages.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
