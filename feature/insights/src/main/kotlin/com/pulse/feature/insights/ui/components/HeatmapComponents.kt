package com.pulse.feature.insights.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.feature.insights.state.HeatmapDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Composable
fun MonthGrid(
    monthKey: String,
    dayMap: Map<String, HeatmapDay>,
    today: LocalDate,
    primaryColor: Color,
    emptyColor: Color,
    futureColor: Color,
    modifier: Modifier = Modifier,
    cellSize: Dp = 10.dp,
    cellGap: Dp = 2.dp,
    onDayClick: ((HeatmapDay) -> Unit)? = null,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val monthLabel = monthName(month)

    val firstOfMonth = LocalDate(year, month, 1)
    val daysCount = daysInMonth(year, month)
    val firstDowOffset = sundayOffset(firstOfMonth.dayOfWeek)
    val onSurface = MaterialTheme.colorScheme.onSurface

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
                    if (dayNum in 1..daysCount) {
                        val dateStr = "$monthKey-${dayNum.toString().padStart(2, '0')}"
                        val cellDate = LocalDate(year, month, dayNum)
                        val isFuture = cellDate > today
                        val isToday = cellDate == today

                        val heatmapDay = dayMap[dateStr]
                        val intensity = heatmapDay?.intensity ?: 0f
                        val color = when {
                            isFuture -> futureColor
                            intensity <= 0f -> emptyColor.copy(alpha = 0.4f)
                            else -> cellColor(intensity, primaryColor, emptyColor)
                        }

                        Box(
                            Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                                .then(
                                    if (isToday) Modifier.border(
                                        1.5.dp, onSurface, RoundedCornerShape(2.dp)
                                    ) else Modifier
                                )
                                .then(
                                    if (onDayClick != null && heatmapDay != null && !isFuture) {
                                        Modifier.clickable { onDayClick(heatmapDay) }
                                    } else Modifier
                                ),
                        )
                    } else {
                        Box(Modifier.size(cellSize))
                    }
                }
            }
            Spacer(Modifier.height(cellGap))
        }
    }
}

fun cellColor(intensity: Float, primary: Color, empty: Color): Color {
    if (intensity <= 0f) return empty
    val alpha = 0.18f + intensity * 0.82f
    return primary.copy(alpha = alpha).compositeOver(empty.copy(alpha = 0.1f))
}

fun sundayOffset(dow: DayOfWeek): Int = when (dow) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    else -> 0
}

fun daysInMonth(year: Int, month: Int): Int {
    val first = LocalDate(year, month, 1)
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return (nextMonth.toEpochDays() - first.toEpochDays()).toInt()
}

fun monthName(month: Int): String = when (month) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
    5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
    else -> ""
}
