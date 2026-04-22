package com.pulse.core.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SleepStage(
    val label: String,
    val minutes: Long,
    val color: Color,
)

object SleepStageColors {
    val Deep = Color(0xFF3F51B5)
    val Rem = Color(0xFF7C4DFF)
    val Light = Color(0xFF4DD0E1)
    val Awake = Color(0xFFFF8A65)
}

@Composable
fun SleepStagesTimeline(
    stages: List<SleepStage>,
    modifier: Modifier = Modifier,
) {
    if (stages.isEmpty()) return

    val total = stages.sumOf { it.minutes }.coerceAtLeast(1L).toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(stages) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Proportional bar
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            stages.forEach { stage ->
                if (stage.minutes > 0) {
                    Box(
                        Modifier
                            .weight((stage.minutes / total) * progress.value.coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(stage.color),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Legend with durations
        stages.forEach { stage ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stage.color),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stage.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = formatSleepDuration(stage.minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

fun formatSleepDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
