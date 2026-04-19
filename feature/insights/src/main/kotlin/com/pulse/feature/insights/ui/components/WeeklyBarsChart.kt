package com.pulse.feature.insights.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.core.ui.charts.MetricBar
import com.pulse.core.ui.charts.MetricBarsChart
import com.pulse.core.ui.charts.formatCompact
import com.pulse.feature.insights.state.DayBar

@Composable
fun WeeklyBarsChart(
    bars: List<DayBar>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return

    val totalSteps = bars.sumOf { it.value }
    val daysActive = bars.count { it.value > 0 }
    val avgPerDay = if (daysActive > 0) totalSteps / daysActive else 0.0

    MetricBarsChart(
        bars = bars.map { MetricBar(it.label, it.value, it.goal, it.isToday) },
        formatValue = ::formatCompact,
        modifier = modifier,
        summary = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(value = formatCompact(totalSteps), label = "Total")
                SummaryItem(value = "$daysActive", label = "Active days")
                SummaryItem(value = formatCompact(avgPerDay), label = "Avg/day")
            }
        },
    )
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
