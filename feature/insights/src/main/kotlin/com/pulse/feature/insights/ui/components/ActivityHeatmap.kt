package com.pulse.feature.insights.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pulse.feature.insights.state.HeatmapDay
import kotlinx.datetime.LocalDate

@Composable
fun ActivityHeatmap(
    days: List<HeatmapDay>,
    todayDate: String,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    if (days.isEmpty()) return

    val dayMap = remember(days) { days.associateBy { it.date } }
    val today = remember(todayDate) { LocalDate.parse(todayDate) }

    val months = remember(days, todayDate) {
        val allMonths = days.map { it.date.substring(0, 7) }.distinct().sorted()
        allMonths.takeLast(3)
    }

    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { intensity ->
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cellColor(intensity, primary, surfaceVariant)),
                    )
                }
                Text(
                    "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                months.forEach { monthKey ->
                    MonthGrid(
                        monthKey = monthKey,
                        dayMap = dayMap,
                        today = today,
                        primaryColor = primary,
                        emptyColor = surfaceVariant,
                        futureColor = surface,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        onDayClick = { day -> selectedDay = day },
                    )
                }
            }

            // Tap-to-inspect popup
            selectedDay?.let { day ->
                Spacer(Modifier.height(8.dp))
                DayDetailPopup(
                    day = day,
                    onDismiss = { selectedDay = null },
                )
            }

            // "View all" link
            if (onViewAll != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "View all \u2192",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onViewAll() }
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailPopup(
    day: HeatmapDay,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatHeatmapDate(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    text = "${formatHeatmapValue(day.rawValue)} ${day.metricLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

private fun formatHeatmapDate(dateStr: String): String = try {
    val date = LocalDate.parse(dateStr)
    val mon = monthName(date.monthNumber)
    "$mon ${date.dayOfMonth}, ${date.year}"
} catch (_: Exception) {
    dateStr
}

private fun formatHeatmapValue(value: Double): String = when {
    value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000)}M"
    value >= 1_000 -> "%,d".format(value.toInt())
    value == 0.0 -> "0"
    value < 1.0 -> "%.1f".format(value)
    else -> "${value.toInt()}"
}
