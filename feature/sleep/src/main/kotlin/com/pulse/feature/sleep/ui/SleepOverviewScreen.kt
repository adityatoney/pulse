package com.pulse.feature.sleep.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.ui.charts.SleepBar
import com.pulse.core.ui.charts.SleepDurationBarsChart
import com.pulse.core.ui.charts.SleepScheduleBar
import com.pulse.core.ui.charts.SleepScheduleChart
import com.pulse.feature.sleep.state.SleepNightUi
import com.pulse.feature.sleep.state.SleepOverviewEffect
import com.pulse.feature.sleep.state.SleepOverviewIntent
import com.pulse.feature.sleep.state.SleepOverviewState
import com.pulse.feature.sleep.state.SleepViewMode
import com.pulse.feature.sleep.viewmodel.SleepOverviewViewModel

@Composable
fun SleepOverviewRoute(
    onBack: () -> Unit,
    onNavigateToNight: (String) -> Unit,
    viewModel: SleepOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    is SleepOverviewEffect.NavigateToNight -> onNavigateToNight(effect.dateStr)
                    SleepOverviewEffect.NavigateBack -> onBack()
                }
            }
    }

    SleepOverviewScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepOverviewScreen(
    state: SleepOverviewState,
    onIntent: (SleepOverviewIntent) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(SleepOverviewIntent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Period navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onIntent(SleepOverviewIntent.MovePeriod(forward = false)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week")
                    }
                    Text(
                        text = state.periodLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { onIntent(SleepOverviewIntent.MovePeriod(forward = true)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next week")
                    }
                }
            }

            // Mode toggle chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.viewMode == SleepViewMode.Duration,
                        onClick = { onIntent(SleepOverviewIntent.ToggleMode(SleepViewMode.Duration)) },
                        label = { Text("Duration") },
                    )
                    FilterChip(
                        selected = state.viewMode == SleepViewMode.Schedule,
                        onClick = { onIntent(SleepOverviewIntent.ToggleMode(SleepViewMode.Schedule)) },
                        label = { Text("Schedule") },
                    )
                }
            }

            // Summary
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (state.viewMode == SleepViewMode.Duration) {
                        val avgH = state.avgDurationMinutes / 60
                        val avgM = state.avgDurationMinutes % 60
                        Text(
                            text = if (state.avgDurationMinutes > 0) "Avg ${avgH}h ${avgM}m" else "No data",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = state.bedtimeRangeLabel.ifEmpty { "No data" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Chart
            item {
                if (state.viewMode == SleepViewMode.Duration) {
                    SleepDurationBarsChart(
                        bars = state.nights.map { night ->
                            SleepBar(
                                label = night.dayLabel,
                                durationMinutes = night.durationMinutes,
                            )
                        },
                        avgMinutes = state.avgDurationMinutes,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    SleepScheduleChart(
                        bars = state.nights.filter { it.durationMinutes > 0 }.map { night ->
                            SleepScheduleBar(
                                label = night.dayLabel,
                                bedtimeHour = night.bedtimeHour,
                                wakeHour = night.wakeHour,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Nightly log entries
            item {
                Text(
                    "Nightly Log",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            items(state.nights.filter { it.durationMinutes > 0 }, key = { it.date.toString() }) { night ->
                SleepNightRow(
                    night = night,
                    viewMode = state.viewMode,
                    onClick = { onIntent(SleepOverviewIntent.SelectNight(night.date)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.nights.none { it.durationMinutes > 0 }) {
                item {
                    Text(
                        "No sleep data for this period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SleepNightRow(
    night: SleepNightUi,
    viewMode: SleepViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.NightsStay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = formatNightDate(night.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Fitbit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (viewMode == SleepViewMode.Duration) {
                    val h = night.durationMinutes / 60
                    val m = night.durationMinutes % 60
                    "${h}h ${m}m"
                } else {
                    "${night.bedtimeLabel} – ${night.wakeTimeLabel}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatNightDate(date: kotlinx.datetime.LocalDate): String {
    val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dow, $month ${date.dayOfMonth}"
}
