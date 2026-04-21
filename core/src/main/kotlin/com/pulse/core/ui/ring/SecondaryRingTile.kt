package com.pulse.core.ui.ring

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SecondaryRingTile(
    progress: Float,
    brush: Brush,
    trackColor: Color,
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clamped = progress.coerceIn(0f, 1.25f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "secondary-ring",
    )
    Column(
        modifier = modifier
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() },
                    )
                } else Modifier
            )
            .semantics {
                contentDescription = "$value $label, ${(progress * 100).toInt()} percent"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.coerceAtMost(1f), 0f..1f)
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val stroke = with(density) { 8.dp.toPx() }

            Canvas(Modifier.fillMaxSize()) {
                val totalSweep = 270f
                val startAngle = 135f
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = brush,
                    startAngle = startAngle,
                    sweepAngle = (totalSweep * animated).coerceAtMost(360f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

/**
 * A [SecondaryRingTile] with a built-in source toggle.
 *
 * Tapping the tile opens the metric detail. Double-tapping flips between
 * "all sources" and "activity only" with a smooth ring-color crossfade
 * and a value counter-roll animation. A small source pill appears beneath
 * the label to indicate the active mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToggleableRingTile(
    progress: Float,
    brush: Brush,
    trackColor: Color,
    icon: ImageVector,
    value: String,
    label: String,
    isActivityOnly: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clamped = progress.coerceIn(0f, 1.25f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "toggle-ring",
    )

    // Source pill color
    val pillColor by animateColorAsState(
        targetValue = if (isActivityOnly)
            MaterialTheme.colorScheme.tertiary
        else
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "pill-color",
    )

    // Subtle scale bounce on toggle
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "scale",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onDoubleClick = { onToggle() },
            )
            .semantics {
                val source = if (isActivityOnly) "activity only" else "all sources"
                contentDescription = "$value $label, $source, ${(progress * 100).toInt()} percent"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.coerceAtMost(1f), 0f..1f)
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val stroke = with(density) { 8.dp.toPx() }

            Canvas(Modifier.fillMaxSize()) {
                val totalSweep = 270f
                val startAngle = 135f
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)

                // Track
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )

                drawArc(
                    brush = brush,
                    startAngle = startAngle,
                    sweepAngle = (totalSweep * animated).coerceAtMost(360f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Label + source pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            // Source mode pill
            Text(
                text = if (isActivityOnly) "ACT" else "ALL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = if (isActivityOnly)
                    MaterialTheme.colorScheme.onTertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(pillColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
