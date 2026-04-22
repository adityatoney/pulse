package com.pulse.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SleepClockDial(
    bedtimeHour: Float,
    wakeHour: Float,
    durationLabel: String,
    modifier: Modifier = Modifier,
    sleepColor: Color = Color(0xFF3F51B5),
    trackColor: Color = Color(0xFF3F51B5).copy(alpha = 0.15f),
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bedtimeHour, wakeHour) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000))
    }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Track circle
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Convert hours to angles on a 12-hour clock face
            // 12 o'clock = -90 degrees (top), clockwise
            fun hourToAngle(h: Float): Float {
                val h12 = h % 12f
                return (h12 / 12f) * 360f - 90f
            }

            val startAngle = hourToAngle(bedtimeHour)
            var sweepAngle = hourToAngle(wakeHour) - startAngle
            if (sweepAngle <= 0) sweepAngle += 360f
            sweepAngle *= progress.value

            // Sleep arc
            drawArc(
                color = sleepColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            )

            // Hour tick marks
            for (h in 0 until 12) {
                val angle = (h / 12f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val outerR = radius + strokeWidth / 2f + 2.dp.toPx()
                val innerR = radius + strokeWidth / 2f - 4.dp.toPx()
                val isMajor = h % 3 == 0

                val startR = if (isMajor) innerR - 4.dp.toPx() else innerR
                drawLine(
                    color = labelColor.copy(alpha = 0.4f),
                    start = Offset(center.x + cos(angle) * startR, center.y + sin(angle) * startR),
                    end = Offset(center.x + cos(angle) * (innerR + 2.dp.toPx()), center.y + sin(angle) * (innerR + 2.dp.toPx())),
                    strokeWidth = if (isMajor) 2f else 1f,
                )
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Asleep",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
            )
        }
    }
}
