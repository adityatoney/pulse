package com.pulse.feature.insights.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.feature.insights.state.HeatmapDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Composable
fun ActivityHeatmap(
    days: List<HeatmapDay>,
    todayDate: String,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val dayMap = remember(days) { days.associateBy { it.date } }
    val today = remember(todayDate) { LocalDate.parse(todayDate) }

    // Group into up to 3 most recent months
    val months = remember(days, todayDate) {
        val allMonths = days.map { it.date.substring(0, 7) }.distinct().sorted()
        allMonths.takeLast(3)
    }

    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface

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
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    monthKey: String,
    dayMap: Map<String, HeatmapDay>,
    today: LocalDate,
    primaryColor: Color,
    emptyColor: Color,
    futureColor: Color,
    modifier: Modifier = Modifier,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val monthLabel = monthName(month)

    val firstOfMonth = LocalDate(year, month, 1)
    val daysInMonth = daysInMonth(year, month)

    // Sunday-first offset: Sun=0, Mon=1, ..., Sat=6
    val firstDowOffset = sundayOffset(firstOfMonth.dayOfWeek)

    val cellSize = 10.dp
    val cellGap = 2.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))

        for (row in 0..5) {
            Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                for (col in 0..6) {
                    val dayNum = row * 7 + col - firstDowOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val dateStr = "$monthKey-${dayNum.toString().padStart(2, '0')}"
                        val cellDate = LocalDate(year, month, dayNum)
                        val isFuture = cellDate > today

                        val intensity = dayMap[dateStr]?.intensity ?: 0f
                        val color = when {
                            isFuture -> futureColor
                            intensity <= 0f -> emptyColor.copy(alpha = 0.4f)
                            else -> cellColor(intensity, primaryColor, emptyColor)
                        }

                        Box(
                            Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color),
                        )
                    } else {
                        // Empty spacer for grid alignment
                        Box(Modifier.size(cellSize))
                    }
                }
            }
            Spacer(Modifier.height(cellGap))
        }
    }
}

private fun cellColor(intensity: Float, primary: Color, empty: Color): Color {
    if (intensity <= 0f) return empty
    // Blend from faint primary to full primary
    val alpha = 0.18f + intensity * 0.82f
    return primary.copy(alpha = alpha).compositeOver(empty.copy(alpha = 0.1f))
}

private fun sundayOffset(dow: DayOfWeek): Int = when (dow) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    else -> 0
}

private fun daysInMonth(year: Int, month: Int): Int {
    val first = LocalDate(year, month, 1)
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return (nextMonth.toEpochDays() - first.toEpochDays()).toInt()
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
    5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
    else -> ""
}
