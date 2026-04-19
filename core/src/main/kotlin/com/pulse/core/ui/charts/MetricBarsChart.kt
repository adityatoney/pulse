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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class MetricBar(
    val label: String,
    val value: Double,
    val goal: Double,
    val isHighlighted: Boolean = false,
)

@Composable
fun MetricBarsChart(
    bars: List<MetricBar>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
) {
    if (bars.isEmpty()) return

    val goal = bars.firstOrNull()?.goal ?: 0.0
    val showGoalLine = goal > 0.0
    val maxValue = bars.maxOf { it.value }.coerceAtLeast(if (showGoalLine) goal else 1.0)
    val maxBarHeight = 110.dp
    val barWidth: Dp = when {
        bars.size <= 7 -> 26.dp
        bars.size <= 12 -> 18.dp
        else -> 12.dp
    }

    // Pre-compute goal line position in dp
    val goalFraction = if (showGoalLine) (goal / maxValue).toFloat().coerceIn(0f, 1f) else 0f
    val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val density = LocalDensity.current

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

                        val animatedFraction by animateFloatAsState(
                            targetValue = if (appeared) (bar.value / maxValue).toFloat().coerceIn(0f, 1f) else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            label = "barHeight",
                        )

                        val barColor = when {
                            bar.value <= 0 -> MaterialTheme.colorScheme.surfaceVariant
                            bar.goal > 0 && bar.value >= bar.goal -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f),
                        ) {
                            // Value label
                            if (bar.value > 0) {
                                Text(
                                    text = formatValue(bar.value),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (bar.goal > 0 && bar.value >= bar.goal) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (bar.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(3.dp))
                            } else {
                                Spacer(Modifier.height(16.dp))
                            }

                            // Bar
                            val barHeight = if (bar.value > 0) {
                                (maxBarHeight.value * animatedFraction).dp.coerceAtLeast(6.dp)
                            } else {
                                3.dp
                            }

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

                            // Day label
                            Text(
                                text = bar.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bar.isHighlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (bar.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Dashed goal line overlay
                if (showGoalLine) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // Bottom offset: day label (~14dp) + spacer (6dp) = ~20dp
                        val bottomOffsetPx = with(density) { 20.dp.toPx() }
                        val barAreaPx = with(density) { maxBarHeight.toPx() }
                        val goalY = size.height - bottomOffsetPx - barAreaPx * goalFraction

                        drawLine(
                            color = goalLineColor,
                            start = Offset(0f, goalY),
                            end = Offset(size.width, goalY),
                            strokeWidth = with(density) { 1.dp.toPx() },
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(8f, 6f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

fun formatCompact(value: Double): String = when {
    value >= 100_000 -> "${(value / 1000).toInt()}k"
    value >= 1_000 -> "${"%.1f".format(value / 1000)}k"
    else -> "${value.toInt()}"
}
