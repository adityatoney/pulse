package com.pulse.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.ui.charts.HrChartPoint
import com.pulse.core.ui.charts.HrRangeBar
import com.pulse.core.ui.charts.HrRangeChart
import com.pulse.core.ui.charts.IntradayHrChart
import com.pulse.feature.detail.state.HrDetailEffect
import com.pulse.feature.detail.state.HrDetailIntent
import com.pulse.feature.detail.state.HrDetailState
import com.pulse.feature.detail.state.HrTimeframe
import com.pulse.feature.detail.state.WeeklyHrSummary
import com.pulse.feature.detail.viewmodel.HrDetailViewModel

@Composable
fun HrDetailRoute(
    onBack: () -> Unit,
    viewModel: HrDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    HrDetailEffect.NavigateBack -> onBack()
                }
            }
    }

    HrDetailScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrDetailScreen(
    state: HrDetailState,
    onIntent: (HrDetailIntent) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Heart Rate") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(HrDetailIntent.Back) }) {
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
            // Timeframe tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HrTimeframe.entries.forEach { tf ->
                        FilterChip(
                            selected = state.timeframe == tf,
                            onClick = { onIntent(HrDetailIntent.ChangeTimeframe(tf)) },
                            label = { Text(tf.name) },
                        )
                    }
                }
            }

            // Period navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onIntent(HrDetailIntent.MovePeriod(forward = false)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                    }
                    Text(
                        text = state.periodLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { onIntent(HrDetailIntent.MovePeriod(forward = true)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                when (state.timeframe) {
                    HrTimeframe.Day -> {
                        // Intraday chart
                        item {
                            if (state.intradaySamples.isNotEmpty()) {
                                IntradayHrChart(
                                    points = state.intradaySamples.map { HrChartPoint(it.timestampMs, it.bpm) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No heart rate data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Stats row
                        if (state.dayMin != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    HrStatCard("Min", "${state.dayMin}", "bpm")
                                    HrStatCard("Max", "${state.dayMax}", "bpm")
                                    HrStatCard("Avg", "${state.dayAvg}", "bpm")
                                    state.restingHr?.let { HrStatCard("Resting", "$it", "bpm") }
                                }
                            }
                        }
                    }

                    HrTimeframe.Week, HrTimeframe.Month -> {
                        // HR range chart
                        item {
                            if (state.dailyRanges.isNotEmpty()) {
                                HrRangeChart(
                                    bars = state.dailyRanges.map { r ->
                                        val dayLabel = "${r.date.dayOfMonth}"
                                        HrRangeBar(label = dayLabel, minBpm = r.minBpm, maxBpm = r.maxBpm, avgBpm = r.avgBpm)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No heart rate data", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Weekly summaries
                        if (state.weeklySummaries.isNotEmpty()) {
                            item {
                                Text(
                                    "Weekly Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            items(state.weeklySummaries) { summary ->
                                WeeklySummaryRow(summary, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun HrStatCard(label: String, value: String, unit: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Spacer(Modifier.width(2.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeeklySummaryRow(summary: WeeklyHrSummary, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Favorite, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.padding(end = 8.dp))
                Text(summary.label, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${summary.minBpm}–${summary.maxBpm} bpm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
