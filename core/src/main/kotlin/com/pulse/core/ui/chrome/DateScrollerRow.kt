package com.pulse.core.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
fun DateScrollerRow(
    selected: LocalDate,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            text = labelFor(selected, today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row {
            IconButton(
                onClick = onNext,
                enabled = selected < today,
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
            }
            IconButton(onClick = onPickDate) {
                Icon(Icons.Outlined.Edit, contentDescription = "Pick a date")
            }
        }
    }
}

private fun labelFor(selected: LocalDate, today: LocalDate): String = when (selected) {
    today -> "Today"
    today.minus(DatePeriod(days = 1)) -> "Yesterday"
    else -> "${selected.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${selected.dayOfMonth}"
}

/** Helper for 'peek next day' logic; not used in composable directly. */
fun LocalDate.nextDay(): LocalDate = this.plus(DatePeriod(days = 1))
