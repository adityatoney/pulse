package com.pulse.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SleepScheduleBar(
    val label: String,
    val bedtimeHour: Float,
    val wakeHour: Float,
    val isHighlighted: Boolean = false,
)

@Composable
fun SleepScheduleChart(
    bars: List<SleepScheduleBar>,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
) {
    if (bars.isEmpty()) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800))
    }

    val barColor = MaterialTheme.colorScheme.primary
    val barColorDim = barColor.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = barColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val highlightStyle = TextStyle(fontSize = 10.sp, color = highlightColor)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            summary?.invoke()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                val leftPad = 40.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val chartW = size.width - leftPad - 8.dp.toPx()
                val chartH = size.height - bottomPad

                // Y-axis: 8 PM (20) to 10 AM (34) — we use a 14-hour window
                // Normalize: bedtime/wake hours are in 24h format,
                // but bedtime typically > 20 and wake < 10 next day.
                // We map 20:00 to top, 10:00 (next day = 34) to bottom
                val yMin = 20f
                val yMax = 34f
                val yRange = yMax - yMin

                fun hourToY(h: Float): Float {
                    val normalized = if (h < 12f) h + 24f else h
                    val frac = ((normalized - yMin) / yRange).coerceIn(0f, 1f)
                    return frac * chartH
                }

                // Y-axis labels
                val yLabels = listOf(20f, 22f, 24f, 26f, 28f, 30f, 32f, 34f)
                val yLabelTexts = listOf("8 PM", "10 PM", "12 AM", "2 AM", "4 AM", "6 AM", "8 AM", "10 AM")
                yLabels.forEachIndexed { i, hour ->
                    val y = hourToY(hour)
                    drawLine(gridColor, Offset(leftPad, y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 1f)
                    val measured = textMeasurer.measure(yLabelTexts[i], labelStyle)
                    drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
                }

                // Bars
                val barSpacing = chartW / bars.size
                val barWidth = (barSpacing * 0.5f).coerceIn(8.dp.toPx(), 24.dp.toPx())

                bars.forEachIndexed { i, bar ->
                    val cx = leftPad + barSpacing * i + barSpacing / 2f
                    val top = hourToY(if (bar.bedtimeHour < 12f) bar.bedtimeHour + 24f else bar.bedtimeHour) * progress.value
                    val bottom = hourToY(if (bar.wakeHour < 12f) bar.wakeHour + 24f else bar.wakeHour) * progress.value

                    val color = if (bar.isHighlighted) barColor else barColorDim

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx - barWidth / 2f, top),
                        size = Size(barWidth, (bottom - top).coerceAtLeast(2.dp.toPx())),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )

                    // Dots at endpoints
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(cx, top))
                    drawCircle(color, radius = 3.dp.toPx(), center = Offset(cx, bottom))

                    // Day label
                    val style = if (bar.isHighlighted) highlightStyle else labelStyle
                    val measured = textMeasurer.measure(bar.label, style)
                    drawText(measured, topLeft = Offset(cx - measured.size.width / 2f, chartH + 6.dp.toPx()))
                }
            }
        }
    }
}
