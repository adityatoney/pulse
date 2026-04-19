package com.pulse.feature.insights.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.feature.insights.state.MetricPosition

private val StripBlue = Color(0xFF4A90E2)
private val StripGreen = Color(0xFF2D7D4B)
private val StripAmber = Color(0xFFE0A84A)
private val StripCoral = Color(0xFFE15D4A)

@Composable
fun MetricPositionStrip(
    position: MetricPosition,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Today's Steps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatSteps(position.current),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Gradient dot strip with markers
            val dotCount = 35
            val percentile = position.percentile.coerceIn(0f, 1f)
            val avgPercentile = if (position.max > position.min) {
                ((position.avg - position.min) / (position.max - position.min)).toFloat().coerceIn(0f, 1f)
            } else 0.5f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                val dotRadius = 4.dp.toPx()
                val totalDotsWidth = dotCount * dotRadius * 2
                val spacing = (size.width - totalDotsWidth) / (dotCount - 1).coerceAtLeast(1)
                val centerY = size.height * 0.6f

                // Draw dots
                for (i in 0 until dotCount) {
                    val fraction = i.toFloat() / (dotCount - 1)
                    val cx = dotRadius + i * (dotRadius * 2 + spacing)
                    val color = gradientColor(fraction)
                    drawCircle(
                        color = color,
                        radius = dotRadius,
                        center = Offset(cx, centerY),
                    )
                }

                // Average marker line
                val avgX = dotRadius + avgPercentile * ((dotCount - 1) * (dotRadius * 2 + spacing))
                drawLine(
                    color = Color.Gray,
                    start = Offset(avgX, centerY - dotRadius * 3),
                    end = Offset(avgX, centerY + dotRadius * 2),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Today marker — larger filled circle
                val todayX = dotRadius + percentile * ((dotCount - 1) * (dotRadius * 2 + spacing))
                val todayColor = gradientColor(percentile)
                // White outline
                drawCircle(
                    color = Color.White,
                    radius = dotRadius * 2.2f,
                    center = Offset(todayX, centerY),
                )
                drawCircle(
                    color = todayColor,
                    radius = dotRadius * 1.8f,
                    center = Offset(todayX, centerY),
                )
            }

            // Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatSteps(position.min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Avg ${formatSteps(position.avg)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatSteps(position.max),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun gradientColor(fraction: Float): Color = when {
    fraction < 0.33f -> lerp(StripBlue, StripGreen, fraction / 0.33f)
    fraction < 0.66f -> lerp(StripGreen, StripAmber, (fraction - 0.33f) / 0.33f)
    else -> lerp(StripAmber, StripCoral, (fraction - 0.66f) / 0.34f)
}

private fun formatSteps(value: Double): String = when {
    value >= 10_000 -> "${"%.1f".format(value / 1000)}k"
    value >= 1_000 -> "${"%.1f".format(value / 1000)}k"
    else -> "${value.toInt()}"
}
