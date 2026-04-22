package com.pulse.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun Sparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    animated: Boolean = true,
    fillGradient: Boolean = true,
) {
    if (points.size < 2) return

    val progress = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(points) {
        if (animated) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(800))
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val visibleCount = (points.size * progress.value).toInt().coerceAtLeast(2)
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val linePath = Path().apply {
            moveTo(0f, h * (1f - points[0]))
            for (i in 1 until visibleCount) {
                val x = i * stepX
                val y = h * (1f - points[i])
                lineTo(x, y)
            }
        }
        drawPath(linePath, color, style = Stroke(width = strokeWidth))

        // Fill gradient under the line
        if (fillGradient && visibleCount > 1) {
            val fillPath = Path().apply {
                moveTo(0f, h * (1f - points[0]))
                for (i in 1 until visibleCount) {
                    lineTo(i * stepX, h * (1f - points[i]))
                }
                lineTo((visibleCount - 1) * stepX, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.2f), Color.Transparent),
                ),
                style = Fill,
            )
        }
    }
}
