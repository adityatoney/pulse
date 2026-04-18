package com.pulse.feature.detail.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.core.designsystem.theme.Forest500
import com.pulse.core.designsystem.theme.Forest900

/**
 * Minimal custom bar chart used on MetricDetailScreen. Draws rounded bars with a
 * goal threshold line. Deliberately kept Canvas-native so the module has no
 * hard dependency on Vico at this stage — Vico is already wired in the build file
 * and can be swapped in once installed.
 */
@Composable
fun MetricBarChart(
    values: List<Float>,
    goal: Float?,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barBrush: Brush = Brush.verticalGradient(listOf(Forest500, Forest900)),
    goalLineColor: Color = Forest900,
    selectedIndex: Int? = null,
    formatValue: ((Float) -> String)? = null,
    onBarTapped: ((index: Int) -> Unit)? = null,
) {
    if (values.isEmpty()) {
        Box(modifier.height(220.dp), contentAlignment = Alignment.Center) {
            Text("No data", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxValue = maxOf(values.max(), goal ?: 0f).coerceAtLeast(1f)
    val hasLabels = labels.isNotEmpty()
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val valueStyle = TextStyle(fontSize = 11.sp, color = Forest900, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val dimBrush = Brush.verticalGradient(listOf(Forest500.copy(alpha = 0.3f), Forest900.copy(alpha = 0.3f)))

    Box(modifier.height(if (hasLabels) 260.dp else 220.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onBarTapped != null) {
                        Modifier.pointerInput(values.size) {
                            detectTapGestures { offset ->
                                val axisRight = size.width * 0.92f
                                val barSlot = axisRight / values.size
                                val tappedIndex = (offset.x / barSlot).toInt()
                                    .coerceIn(0, values.size - 1)
                                onBarTapped(tappedIndex)
                            }
                        }
                    } else Modifier
                )
        ) {
            val labelSpace = if (hasLabels) 36.dp.toPx() else 0f
            val chartHeight = (size.height - labelSpace) * 0.82f
            val chartTop = (size.height - labelSpace) * 0.06f
            val axisRight = size.width * 0.92f
            val barSlot = axisRight / values.size
            val barWidth = (barSlot * 0.45f).coerceAtLeast(3f)

            // Goal threshold line
            if (goal != null && goal > 0f) {
                val y = chartTop + chartHeight * (1f - goal / maxValue)
                drawLine(
                    color = goalLineColor.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(axisRight, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }

            values.forEachIndexed { i, v ->
                val h = chartHeight * (v / maxValue)
                val x = i * barSlot + (barSlot - barWidth) / 2
                val barBottom = chartTop + chartHeight
                val barHeight = h.coerceAtLeast(barWidth) // min height = round cap diameter
                val barTop = barBottom - barHeight
                val cornerRadius = barWidth / 2
                val brush = if (selectedIndex != null && i != selectedIndex) dimBrush else barBrush
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }

            // Draw value label above selected bar
            if (selectedIndex != null && selectedIndex in values.indices) {
                val v = values[selectedIndex]
                val label = formatValue?.invoke(v) ?: v.toInt().toString()
                val measured = textMeasurer.measure(label, valueStyle, maxLines = 1)
                val h = chartHeight * (v / maxValue)
                val barBottom = chartTop + chartHeight
                val barHeight = h.coerceAtLeast(barWidth)
                val barTop = barBottom - barHeight
                val cx = selectedIndex * barSlot + barSlot / 2
                val labelY = (barTop - measured.size.height - 4.dp.toPx()).coerceAtLeast(0f)
                drawText(
                    measured,
                    topLeft = Offset(
                        (cx - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                        labelY,
                    ),
                )
            }

            // Draw labels below bars — constrain to slot width to prevent overlap
            if (hasLabels) {
                val labelTop = chartTop + chartHeight + 8.dp.toPx()
                val maxLabelWidth = (barSlot * 0.95f).toInt().coerceAtLeast(1)
                labels.forEachIndexed { i, label ->
                    if (label.isNotBlank() && i < values.size) {
                        val constrainedStyle = labelStyle.copy(fontSize = 9.sp)
                        val constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxLabelWidth)
                        val measured = textMeasurer.measure(label, constrainedStyle, constraints = constraints, maxLines = 1)
                        val cx = i * barSlot + barSlot / 2
                        drawText(
                            measured,
                            topLeft = Offset(cx - measured.size.width / 2f, labelTop),
                        )
                    }
                }
            }
        }
    }
}
