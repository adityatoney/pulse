package com.pulse.feature.insights.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.core.ui.charts.Sparkline
import com.pulse.domain.model.MetricTrend
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.TrendDirection
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun TrendCard(
    trend: MetricTrend,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0,
) {
    val label = trendLabel(trend.metric)
    val invertSentiment = trend.metric == MetricType.RestingHeartRate
    val delta = trend.delta
    val arrowChar: String
    val arrowColor: Color

    if (delta != null) {
        val effectiveDir = if (invertSentiment) {
            when (delta.direction) {
                TrendDirection.Up -> TrendDirection.Down
                TrendDirection.Down -> TrendDirection.Up
                TrendDirection.Flat -> TrendDirection.Flat
            }
        } else delta.direction

        arrowChar = when (delta.direction) {
            TrendDirection.Up -> "\u2191"
            TrendDirection.Down -> "\u2193"
            TrendDirection.Flat -> "\u2192"
        }
        arrowColor = when (effectiveDir) {
            TrendDirection.Up -> Color(0xFF4CAF50)
            TrendDirection.Down -> Color(0xFFE53935)
            TrendDirection.Flat -> Color.Gray
        }
    } else {
        arrowChar = "\u2192"
        arrowColor = Color.Gray
    }

    val sparkColor = arrowColor.copy(alpha = 0.7f)

    // Entry animation: fade + slide up
    val animAlpha = remember { Animatable(0f) }
    val animOffset = remember { Animatable(20f) }
    LaunchedEffect(trend.metric) {
        animAlpha.snapTo(0f)
        animOffset.snapTo(20f)
        kotlinx.coroutines.delay(animationDelay.toLong())
        animAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(trend.metric) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        animOffset.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
    }

    Card(
        modifier = modifier
            .alpha(animAlpha.value)
            .graphicsLayer { translationY = animOffset.value },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, arrowColor.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        arrowChar,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = arrowColor,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (delta != null) "${delta.value.absoluteValue.roundToInt()}%" else "--",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = arrowColor,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (trend.sparklinePoints.size >= 2) {
                Sparkline(
                    points = trend.sparklinePoints,
                    color = sparkColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatAvg(trend.recentAvg, trend.metric),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trendLabel(metric: MetricType): String = when (metric) {
    MetricType.Steps -> "Steps"
    MetricType.Distance -> "Distance"
    MetricType.ActiveCalories -> "Calories"
    MetricType.ZoneMinutes -> "Zone Min"
    MetricType.Sleep -> "Sleep"
    MetricType.RestingHeartRate -> "Resting HR"
    else -> metric.name
}

private fun formatAvg(avg: Double, metric: MetricType): String = when (metric) {
    MetricType.Steps -> "%,.0f avg".format(avg)
    MetricType.Distance -> "%.1f mi avg".format(avg)
    MetricType.ActiveCalories -> "%,.0f cal avg".format(avg)
    MetricType.ZoneMinutes -> "%.0f min avg".format(avg)
    MetricType.Sleep -> "%.1f hrs avg".format(avg / 60.0)
    MetricType.RestingHeartRate -> "%.0f bpm avg".format(avg)
    else -> "%.0f avg".format(avg)
}
