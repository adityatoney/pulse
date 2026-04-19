package com.pulse.feature.insights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.ui.insights.InsightCard
import com.pulse.core.ui.insights.InsightSentimentUi
import com.pulse.domain.model.InsightSentiment
import com.pulse.domain.model.InsightType
import com.pulse.feature.insights.state.InsightsEffect
import com.pulse.feature.insights.state.InsightsIntent
import com.pulse.feature.insights.state.InsightsState
import com.pulse.feature.insights.ui.components.ActivityHeatmap
import com.pulse.feature.insights.ui.components.MetricPositionStrip
import com.pulse.feature.insights.ui.components.WeeklyBarsChart
import com.pulse.feature.insights.viewmodel.InsightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    InsightsEffect.NavigateBack -> onBack()
                }
            }
    }

    InsightsScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    state: InsightsState,
    onIntent: (InsightsIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hasAnyContent = state.dailyInsights.isNotEmpty() ||
                state.weeklyInsights.isNotEmpty() ||
                state.longitudinalInsights.isNotEmpty() ||
                state.weeklyBars.isNotEmpty() ||
                state.heatmapDays.isNotEmpty() ||
                state.todayPosition != null

            if (!hasAnyContent && !state.loading) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "No insights yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Insights will appear after your first sync. They are computed from your activity data to surface patterns and trends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Right Now ──
            if (state.dailyInsights.isNotEmpty() || state.todayPosition != null) {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Right Now")

                // Position strip — where today falls in 30-day range
                state.todayPosition?.let { pos ->
                    MetricPositionStrip(position = pos)
                }

                // Insight cards
                state.dailyInsights.forEach { insight ->
                    InsightCard(
                        headline = insight.headline,
                        body = insight.body,
                        sentiment = insight.sentiment.toUi(),
                    )
                }
            }

            // ── This Week ──
            if (state.weeklyInsights.isNotEmpty() || state.weeklyBars.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionHeader("This Week")

                // Weekly bar chart
                if (state.weeklyBars.isNotEmpty()) {
                    WeeklyBarsChart(bars = state.weeklyBars)
                }

                // Insight cards — sub-grouped
                if (state.weeklyInsights.isNotEmpty()) {
                    val grouped = state.weeklyInsights.groupBy { insightGroupLabel(it.type) }
                    grouped.forEach { (label, insights) ->
                        SubSectionLabel(label)
                        insights.forEach { insight ->
                            InsightCard(
                                headline = insight.headline,
                                body = insight.body,
                                sentiment = insight.sentiment.toUi(),
                            )
                        }
                    }
                }
            }

            // ── Big Picture ──
            if (state.longitudinalInsights.isNotEmpty() || state.heatmapDays.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Big Picture")

                // 3-month activity heatmap
                if (state.heatmapDays.isNotEmpty()) {
                    ActivityHeatmap(
                        days = state.heatmapDays,
                        todayDate = state.heatmapDays.maxByOrNull { it.date }?.date ?: "",
                    )
                }

                // Insight cards
                state.longitudinalInsights.forEach { insight ->
                    InsightCard(
                        headline = insight.headline,
                        body = insight.body,
                        sentiment = insight.sentiment.toUi(),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SubSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private fun insightGroupLabel(type: InsightType): String = when (type) {
    InsightType.SupportLevel -> "Consistency"
    InsightType.PaceTrajectory -> "Goal Pace"
    InsightType.GoalConsistency -> "Goal Pace"
    InsightType.WoW -> "vs Last Week"
    InsightType.Streak -> "Streaks"
    InsightType.PersonalRecord -> "Records"
    else -> "Other"
}

private fun InsightSentiment.toUi(): InsightSentimentUi = when (this) {
    InsightSentiment.Positive -> InsightSentimentUi.Positive
    InsightSentiment.Neutral -> InsightSentimentUi.Neutral
    InsightSentiment.Negative -> InsightSentimentUi.Negative
    InsightSentiment.Celebratory -> InsightSentimentUi.Celebratory
}
