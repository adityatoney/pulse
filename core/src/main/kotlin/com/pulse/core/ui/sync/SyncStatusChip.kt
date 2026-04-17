package com.pulse.core.ui.sync

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pulse.core.designsystem.theme.SuccessGreen
import com.pulse.core.designsystem.theme.WarnAmber

/** Render-ready view state decoupled from domain.SyncPhase so :core stays presentation-only. */
enum class SyncChipState { Idle, Syncing, Stale, Failed }

@Composable
fun SyncStatusChip(
    state: SyncChipState,
    label: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint) = iconFor(state)

    val pulse = if (state == SyncChipState.Syncing) {
        val t = rememberInfiniteTransition(label = "sync-pulse")
        val alpha by t.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pulse",
        )
        alpha
    } else 1f

    val container = when (state) {
        SyncChipState.Failed -> MaterialTheme.colorScheme.errorContainer
        SyncChipState.Stale -> MaterialTheme.colorScheme.tertiaryContainer
        SyncChipState.Syncing -> MaterialTheme.colorScheme.secondaryContainer
        SyncChipState.Idle -> MaterialTheme.colorScheme.primaryContainer
    }

    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() },
                )
            }
            .alpha(pulse),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = state.name, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

private fun iconFor(state: SyncChipState): Pair<ImageVector, Color> = when (state) {
    SyncChipState.Idle -> Icons.Outlined.CloudDone to SuccessGreen
    SyncChipState.Syncing -> Icons.Outlined.CloudSync to SuccessGreen
    SyncChipState.Stale -> Icons.Outlined.CloudOff to WarnAmber
    SyncChipState.Failed -> Icons.Outlined.ErrorOutline to Color(0xFFB3261E)
}

