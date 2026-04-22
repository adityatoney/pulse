package com.pulse.core.ui.streak

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StreakBadge(
    currentStreak: Int,
    longestStreak: Int,
    modifier: Modifier = Modifier,
) {
    val isMilestone = currentStreak in setOf(7, 14, 30, 50, 100, 365)
    val isPersonalBest = currentStreak > 0 && currentStreak >= longestStreak

    val bg = if (isMilestone || isPersonalBest) {
        Color(0xFFFF6B35).copy(alpha = 0.15f)
    } else {
        Color(0xFFFF9800).copy(alpha = 0.12f)
    }
    val tint = if (isMilestone) Color(0xFFFF5722) else Color(0xFFFF6B35)

    // Pulse animation for milestones
    val iconScale = if (isMilestone) {
        val infiniteTransition = rememberInfiniteTransition(label = "streak-pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "fire-scale",
        )
        scale
    } else {
        val bounce = remember { Animatable(0.6f) }
        LaunchedEffect(currentStreak) {
            bounce.snapTo(0.6f)
            bounce.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
        bounce.value
    }

    val label = buildString {
        append("$currentStreak-day")
        if (isMilestone) append(" ${milestoneEmoji(currentStreak)}")
        else if (isPersonalBest) append(" PB")
    }

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(16.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

private fun milestoneEmoji(streak: Int): String = when (streak) {
    7 -> "1W"
    14 -> "2W"
    30 -> "1M"
    50 -> "50"
    100 -> "100"
    365 -> "1Y"
    else -> ""
}
