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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HrChartPoint(
    val timestampMs: Long,
    val bpm: Int,
)

@Composable
fun IntradayHrChart(
    points: List<HrChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFE53935),
    compact: Boolean = false,
) {
    if (points.size < 2) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800))
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = lineColor.copy(alpha = 0.6f))
    val compactLabelStyle = TextStyle(fontSize = 9.sp, color = lineColor.copy(alpha = 0.5f))

    val minBpm = points.minOf { it.bpm }
    val maxBpm = points.maxOf { it.bpm }
    val bpmRange = (maxBpm - minBpm).coerceAtLeast(10)

    val minTs = points.first().timestampMs
    val maxTs = points.last().timestampMs
    val tsRange = (maxTs - minTs).coerceAtLeast(1L)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 80.dp else 160.dp),
    ) {
        val bottomPad = if (compact) 16.dp.toPx() else 20.dp.toPx()
        val leftPad = if (compact) 0f else 32.dp.toPx()
        val topPad = if (compact) 4.dp.toPx() else 8.dp.toPx()
        val chartW = size.width - leftPad - 4.dp.toPx()
        val chartH = size.height - bottomPad - topPad

        fun toX(ts: Long): Float = leftPad + ((ts - minTs).toFloat() / tsRange) * chartW
        fun toY(bpm: Int): Float = topPad + chartH - ((bpm - minBpm).toFloat() / bpmRange) * chartH

        val visibleCount = (points.size * progress.value).toInt().coerceAtLeast(2)

        // Line path
        val linePath = Path().apply {
            moveTo(toX(points[0].timestampMs), toY(points[0].bpm))
            for (i in 1 until visibleCount) {
                lineTo(toX(points[i].timestampMs), toY(points[i].bpm))
            }
        }
        drawPath(linePath, lineColor, style = Stroke(width = if (compact) 1.5.dp.toPx() else 2.dp.toPx()))

        // Fill gradient
        val fillPath = Path().apply {
            moveTo(toX(points[0].timestampMs), toY(points[0].bpm))
            for (i in 1 until visibleCount) {
                lineTo(toX(points[i].timestampMs), toY(points[i].bpm))
            }
            lineTo(toX(points[visibleCount - 1].timestampMs), topPad + chartH)
            lineTo(toX(points[0].timestampMs), topPad + chartH)
            close()
        }
        drawPath(
            fillPath,
            Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.15f), Color.Transparent)),
            style = Fill,
        )

        // Time labels — shown in both compact and full modes
        val tickInterval = if (compact) 3_600_000L * 4 else 3_600_000L * 4
        val startHour = (minTs / tickInterval) * tickInterval
        var tick = startHour
        val style = if (compact) compactLabelStyle else labelStyle
        while (tick <= maxTs) {
            val x = toX(tick)
            if (x >= leftPad + 10.dp.toPx() && x <= size.width - 20.dp.toPx()) {
                val hour = ((tick / 3_600_000L) % 24).toInt()
                val label = when {
                    hour == 0 -> "12a"
                    hour < 12 -> if (compact) "${hour}a" else "$hour AM"
                    hour == 12 -> "12p"
                    else -> if (compact) "${hour - 12}p" else "${hour - 12} PM"
                }
                val measured = textMeasurer.measure(label, style)
                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, topPad + chartH + 3.dp.toPx()))
            }
            tick += tickInterval
        }

        if (!compact) {
            // Y-axis: bpm labels (full mode only)
            val ySteps = listOf(minBpm, (minBpm + maxBpm) / 2, maxBpm)
            ySteps.forEach { bpm ->
                val y = toY(bpm)
                val measured = textMeasurer.measure("$bpm", labelStyle)
                drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
            }
        }
    }
}
