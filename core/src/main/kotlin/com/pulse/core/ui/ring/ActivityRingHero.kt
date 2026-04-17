package com.pulse.core.ui.ring

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Hero Activity Ring — Fitbit-style.
 *
 * Arc sweep math:
 *  - startAngle = 135° (Compose's 0° is 3 o'clock, clockwise positive).
 *    135° places the start at about 7 o'clock.
 *  - totalSweep = 270° leaves the bottom ~90° gap open for the icon + value.
 *  - Progress sweeps [0f..1.25f] so overshoot renders up to ~337° before capping at 360°.
 *
 * Animates with a bouncy spring; round caps; Brush.sweepGradient for depth.
 */
@Composable
fun ActivityRingHero(
    progress: Float,
    brush: Brush,
    trackColor: Color,
    centerIcon: ImageVector,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 260,
    strokeDp: Int = 22,
    onClick: (() -> Unit)? = null,
) {
    val clamped = progress.coerceIn(0f, 1.25f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "hero-ring",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = false, radius = (sizeDp / 2).dp),
                    onClick = onClick,
                    role = Role.Button,
                ) else Modifier
            )
            .semantics {
                contentDescription = "$centerValue $centerLabel, ${(progress * 100).toInt()} percent of goal"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.coerceAtMost(1f), 0f..1f)
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val stroke = with(density) { strokeDp.dp.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val totalSweep = 270f
            val startAngle = 135f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Track (full 270° sweep).
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Progress arc, capped at 360° when overshoot wraps past 100%.
            val rawSweep = totalSweep * animated
            val drawnSweep = rawSweep.coerceAtMost(360f)
            drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = drawnSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(
                imageVector = centerIcon,
                contentDescription = null,
                modifier = Modifier.size((sizeDp * 0.12f).dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = centerValue,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

