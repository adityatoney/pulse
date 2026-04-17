package com.pulse.core.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.core.designsystem.theme.SuccessGreen
import com.pulse.core.designsystem.theme.WarnAmber
import kotlin.math.abs

enum class DeltaDirection { Up, Down, Flat }

@Composable
fun WoWMoMBadge(
    label: String,
    deltaPct: Float?,
    direction: DeltaDirection?,
    modifier: Modifier = Modifier,
) {
    if (deltaPct == null || direction == null) {
        Row(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label —", style = MaterialTheme.typography.labelMedium)
        }
        return
    }

    val (icon, tint, bg) = when (direction) {
        DeltaDirection.Up -> Triple(Icons.Outlined.ArrowUpward, SuccessGreen, SuccessGreen.copy(alpha = 0.12f))
        DeltaDirection.Down -> Triple(Icons.Outlined.ArrowDownward, Color(0xFFB3261E), Color(0xFFB3261E).copy(alpha = 0.12f))
        DeltaDirection.Flat -> Triple(Icons.Outlined.Remove, WarnAmber, WarnAmber.copy(alpha = 0.12f))
    }

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${"%+.1f".format(deltaPct)}% $label",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

fun formatDelta(abs: Float): String = "%.1f".format(abs(abs))
