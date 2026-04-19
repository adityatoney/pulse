package com.pulse.feature.insights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.domain.model.MetricType
import com.pulse.feature.insights.state.HeatmapDay
import com.pulse.feature.insights.state.HeatmapDetailEffect
import com.pulse.feature.insights.state.HeatmapDetailIntent
import com.pulse.feature.insights.state.HeatmapDetailState
import com.pulse.feature.insights.ui.components.MonthGrid
import com.pulse.feature.insights.ui.components.cellColor
import com.pulse.feature.insights.viewmodel.HeatmapDetailViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapDetailRoute(
    onBack: () -> Unit,
    viewModel: HeatmapDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    HeatmapDetailEffect.NavigateBack -> onBack()
                }
            }
    }

    HeatmapDetailScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapDetailScreen(
    state: HeatmapDetailState,
    onIntent: (HeatmapDetailIntent) -> Unit,
    onBack: () -> Unit,
) {
    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Heatmap", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Metric picker chips
            val metrics = listOf(
                MetricType.Steps,
                MetricType.Distance,
                MetricType.ActiveCalories,
                MetricType.ZoneMinutes,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(metrics) { metric ->
                    FilterChip(
                        selected = metric == state.metric,
                        onClick = { onIntent(HeatmapDetailIntent.ChangeMetric(metric)) },
                        label = { Text(heatmapDetailChipLabel(metric)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Legend
            val primary = MaterialTheme.colorScheme.primary
            val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
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
                            .size(12.dp)
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

            // Selected day info
            selectedDay?.let { day ->
                SelectedDayBanner(day = day, onDismiss = { selectedDay = null })
                Spacer(Modifier.height(8.dp))
            }

            // 12-month grid: 2 columns per row, newest first
            if (state.heatmapDays.isNotEmpty()) {
                val dayMap = remember(state.heatmapDays) { state.heatmapDays.associateBy { it.date } }
                val todayDate = remember {
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                }

                val months = remember(state.heatmapDays) {
                    state.heatmapDays.map { it.date.substring(0, 7) }
                        .distinct()
                        .sorted()
                        .reversed()
                }

                val surface = MaterialTheme.colorScheme.surface
                val cellSize = 16.dp

                months.chunked(2).forEach { rowMonths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowMonths.forEach { monthKey ->
                            MonthGrid(
                                monthKey = monthKey,
                                dayMap = dayMap,
                                today = todayDate,
                                primaryColor = primary,
                                emptyColor = surfaceVariant,
                                futureColor = surface,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                cellSize = cellSize,
                                onDayClick = { day -> selectedDay = day },
                            )
                        }
                        // If odd number, fill remaining space
                        if (rowMonths.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SelectedDayBanner(day: HeatmapDay, onDismiss: () -> Unit) {
    val dateStr = try {
        val date = LocalDate.parse(day.date)
        val mon = com.pulse.feature.insights.ui.components.monthName(date.monthNumber)
        "$mon ${date.dayOfMonth}, ${date.year}"
    } catch (_: Exception) {
        day.date
    }

    val valueStr = when {
        day.rawValue >= 1_000 -> "%,d".format(day.rawValue.toInt())
        day.rawValue == 0.0 -> "0"
        day.rawValue < 1.0 -> "%.1f".format(day.rawValue)
        else -> "${day.rawValue.toInt()}"
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "$valueStr ${day.metricLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Text(
                    "\u2715",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun heatmapDetailChipLabel(metric: MetricType): String = when (metric) {
    MetricType.Steps -> "Steps"
    MetricType.Distance -> "Distance"
    MetricType.ActiveCalories -> "Calories"
    MetricType.ZoneMinutes -> "Zone Min"
    else -> metric.name
}
