package com.pulse.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HrRangeBar(
    val label: String,
    val minBpm: Int,
    val maxBpm: Int,
    val avgBpm: Int,
)

@Composable
fun HrRangeChart(
    bars: List<HrRangeBar>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFFE53935),
    barFillColor: Color = Color(0xFFE53935).copy(alpha = 0.2f),
) {
    if (bars.isEmpty()) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800))
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = barColor.copy(alpha = 0.6f))

    val globalMin = bars.minOf { it.minBpm }.coerceAtLeast(30)
    val globalMax = bars.maxOf { it.maxBpm }.coerceAtMost(220)
    val bpmRange = (globalMax - globalMin).coerceAtLeast(20)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val leftPad = 32.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - leftPad - 8.dp.toPx()
        val chartH = size.height - bottomPad - topPad

        fun toY(bpm: Int): Float {
            val frac = (bpm - globalMin).toFloat() / bpmRange
            return topPad + chartH - frac * chartH
        }

        // Y-axis labels
        val ySteps = listOf(globalMin, (globalMin + globalMax) / 2, globalMax)
        ySteps.forEach { bpm ->
            val y = toY(bpm)
            val measured = textMeasurer.measure("$bpm", labelStyle)
            drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
            // Grid line
            drawLine(
                barColor.copy(alpha = 0.1f),
                Offset(leftPad, y),
                Offset(size.width - 8.dp.toPx(), y),
                strokeWidth = 1f,
            )
        }

        // Bars
        val barSpacing = chartW / bars.size
        val barWidth = (barSpacing * 0.4f).coerceIn(4.dp.toPx(), 12.dp.toPx())

        bars.forEachIndexed { i, bar ->
            val cx = leftPad + barSpacing * i + barSpacing / 2f
            val top = toY(bar.maxBpm)
            val bottom = toY(bar.minBpm)
            val rangeH = (bottom - top).coerceAtLeast(2.dp.toPx()) * progress.value

            // Range fill bar
            drawRoundRect(
                color = barFillColor,
                topLeft = Offset(cx - barWidth / 2f, top),
                size = Size(barWidth, rangeH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )

            // Thin line through range
            drawLine(
                barColor,
                Offset(cx, top),
                Offset(cx, top + rangeH),
                strokeWidth = 2.dp.toPx(),
            )

            // Dots at min and max
            drawCircle(barColor, radius = 3.dp.toPx(), center = Offset(cx, top))
            drawCircle(barColor, radius = 3.dp.toPx(), center = Offset(cx, top + rangeH))

            // Day label
            val measured = textMeasurer.measure(bar.label, labelStyle)
            drawText(measured, topLeft = Offset(cx - measured.size.width / 2f, chartH + topPad + 4.dp.toPx()))
        }
    }
}
