package com.pulse.core.ui.ring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.core.designsystem.theme.LocalRingPalette

/**
 * Mini triple-concentric activity rings for calendar grid cells.
 *
 * Draws 3 full 360° rings (Apple Watch-style, starting at 12 o'clock):
 * - Outer: Steps (Forest green)
 * - Middle: Active Calories (Coral red)
 * - Inner: Distance (Sky blue)
 *
 * Progress values > 1.0 wrap past the start point (overshoot).
 * No animation for performance (up to 42 cells visible at once).
 */
@Composable
fun MiniActivityRings(
    outerProgress: Float,
    middleProgress: Float,
    innerProgress: Float,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
    strokeDp: Float = 3.5f,
    gapDp: Float = 1.5f,
    dayNumber: Int? = null,
) {
    val palette = LocalRingPalette.current
    val density = LocalDensity.current
    val strokePx = with(density) { strokeDp.dp.toPx() }
    val gapPx = with(density) { gapDp.dp.toPx() }

    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawRing(
                ringIndex = 0,
                progress = outerProgress,
                brush = palette.steps,
                trackColor = palette.track,
                strokePx = strokePx,
                gapPx = gapPx,
            )
            drawRing(
                ringIndex = 1,
                progress = middleProgress,
                brush = palette.calories,
                trackColor = palette.track,
                strokePx = strokePx,
                gapPx = gapPx,
            )
            drawRing(
                ringIndex = 2,
                progress = innerProgress,
                brush = palette.distance,
                trackColor = palette.track,
                strokePx = strokePx,
                gapPx = gapPx,
            )
        }
        if (dayNumber != null) {
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun DrawScope.drawRing(
    ringIndex: Int,
    progress: Float,
    brush: Brush,
    trackColor: Color,
    strokePx: Float,
    gapPx: Float,
) {
    val inset = strokePx / 2f + ringIndex * (strokePx + gapPx)
    val diameter = size.width - inset * 2
    if (diameter <= 0f) return
    val arcSize = Size(diameter, diameter)
    val topLeft = Offset(inset, inset)
    val startAngle = -90f // 12 o'clock

    // Track (full circle)
    drawArc(
        color = trackColor,
        startAngle = startAngle,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
    )

    // Progress arc
    if (progress > 0f) {
        val sweep = (progress * 360f).coerceAtMost(405f)
        drawArc(
            brush = brush,
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}
