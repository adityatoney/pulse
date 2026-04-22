package com.pulse.core.ui.charts

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class SleepBar(
    val label: String,
    val durationMinutes: Long,
    val isHighlighted: Boolean = false,
)

@Composable
fun SleepDurationBarsChart(
    bars: List<SleepBar>,
    avgMinutes: Long,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
) {
    if (bars.isEmpty()) return

    val maxMinutes = bars.maxOf { it.durationMinutes }.coerceAtLeast(1L)
    val maxBarHeight = 110.dp
    val barWidth = when {
        bars.size <= 7 -> 26.dp
        bars.size <= 14 -> 18.dp
        else -> 12.dp
    }

    val avgFraction = if (avgMinutes > 0) (avgMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f) else 0f
    val avgLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val density = LocalDensity.current
    val sleepColor = MaterialTheme.colorScheme.primary
    val sleepColorDim = sleepColor.copy(alpha = 0.45f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            summary?.invoke()

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { bar ->
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }

                        val animFraction by animateFloatAsState(
                            targetValue = if (appeared) (bar.durationMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f) else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            label = "sleepBar",
                        )

                        val barColor = if (bar.isHighlighted) sleepColor else sleepColorDim

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (bar.durationMinutes > 0) {
                                val h = bar.durationMinutes / 60
                                val m = bar.durationMinutes % 60
                                Text(
                                    text = if (h > 0) "${h}h${m}m" else "${m}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (bar.isHighlighted) sleepColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (bar.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(3.dp))
                            } else {
                                Spacer(Modifier.height(16.dp))
                            }

                            val barHeight = if (bar.durationMinutes > 0) {
                                (maxBarHeight.value * animFraction).dp.coerceAtLeast(6.dp)
                            } else 3.dp

                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height(barHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 6.dp, topEnd = 6.dp,
                                            bottomStart = 2.dp, bottomEnd = 2.dp,
                                        )
                                    )
                                    .background(barColor),
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = bar.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bar.isHighlighted) sleepColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (bar.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Dashed avg line overlay
                if (avgMinutes > 0) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val bottomOffsetPx = with(density) { 20.dp.toPx() }
                        val barAreaPx = with(density) { maxBarHeight.toPx() }
                        val goalY = size.height - bottomOffsetPx - barAreaPx * avgFraction

                        drawLine(
                            color = avgLineColor,
                            start = Offset(0f, goalY),
                            end = Offset(size.width, goalY),
                            strokeWidth = with(density) { 1.dp.toPx() },
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                        )
                    }
                }
            }
        }
    }
}
