package com.pulse.feature.insights.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.domain.model.WeeklyChallenge

private val completedGreen = Color(0xFF4CAF50)

@Composable
fun ChallengeCard(
    challenge: WeeklyChallenge,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (challenge.isComplete) completedGreen else MaterialTheme.colorScheme.primary

    // Animated progress bar
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(challenge.progress) {
        animatedProgress.animateTo(
            challenge.progress,
            tween(700, easing = FastOutSlowInEasing),
        )
    }

    // Checkmark bounce on completion
    val checkScale = remember { Animatable(if (challenge.isComplete) 1f else 0f) }
    LaunchedEffect(challenge.isComplete) {
        if (challenge.isComplete) {
            checkScale.snapTo(0f)
            checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = if (challenge.isComplete) BorderStroke(1.dp, completedGreen.copy(alpha = 0.4f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (challenge.isComplete)
                completedGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    challenge.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (challenge.isComplete) completedGreen else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (challenge.isComplete) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Complete",
                        tint = completedGreen,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(checkScale.value),
                    )
                }
            }
            Text(
                challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentColor,
                trackColor = if (challenge.isComplete)
                    completedGreen.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatProgress(challenge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (challenge.isComplete) "Complete!" else "${(challenge.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                )
            }
        }
    }
}

private fun formatProgress(challenge: WeeklyChallenge): String {
    val current = challenge.currentValue
    val target = challenge.targetValue
    return if (target == target.toLong().toDouble() && current == current.toLong().toDouble()) {
        "${current.toLong()} / ${target.toLong()}"
    } else {
        "%,.0f / %,.0f".format(current, target)
    }
}
