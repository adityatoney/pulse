package com.pulse.feature.detail.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.designsystem.theme.LocalRingPalette
import com.pulse.core.designsystem.theme.SuccessGreen
import com.pulse.core.designsystem.theme.WarnAmber
import com.pulse.core.ui.badges.DeltaDirection
import com.pulse.core.ui.charts.MetricBar
import com.pulse.core.ui.charts.MetricBarsChart
import com.pulse.core.ui.insights.InsightCardData
import com.pulse.core.ui.insights.InsightCarousel
import com.pulse.core.ui.insights.InsightSentimentUi
import com.pulse.domain.model.DeltaPercent
import com.pulse.domain.model.InsightSentiment
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.Timeframe
import com.pulse.domain.model.TrendDirection
import com.pulse.feature.detail.state.PeriodComparison
import com.pulse.feature.detail.state.MetricDetailEffect
import com.pulse.feature.detail.state.MetricDetailIntent
import com.pulse.feature.detail.state.MetricDetailState
import com.pulse.feature.detail.viewmodel.MetricDetailViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailRoute(
    onBack: () -> Unit,
    viewModel: MetricDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    MetricDetailEffect.NavigateBack -> onBack()
                    is MetricDetailEffect.ShowSnackbar -> Unit
                }
            }
    }
    MetricDetailScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailScreen(
    state: MetricDetailState,
    onIntent: (MetricDetailIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state.metric), style = MaterialTheme.typography.titleLarge) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { TimeframeTabs(state.timeframe, onIntent) }
            item { PeriodPager(state, onIntent) }
            item { HeadlineBlock(state) }
            item {
                val points = state.series?.points.orEmpty()
                val labels = barLabels(state)
                val goalValue = scaledGoal(state)?.toDouble() ?: 0.0
                val today = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                val zone = TimeZone.currentSystemDefault()
                MetricBarsChart(
                    bars = points.mapIndexed { i, point ->
                        MetricBar(
                            label = labels.getOrElse(i) { "" },
                            value = point.value,
                            goal = goalValue,
                            isHighlighted = when (state.timeframe) {
                                Timeframe.Day -> false
                                Timeframe.Week -> {
                                    val d = point.bucketStart.toLocalDateTime(zone).date
                                    d == today
                                }
                                else -> false
                            },
                        )
                    },
                    formatValue = { v -> formatBarValue(v.toFloat(), state.metric) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            // Trend comparison panel
            if (state.wow != null || state.mom != null) {
                item {
                    TrendComparisonPanel(
                        wow = state.wow,
                        mom = state.mom,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.insights.isNotEmpty()) {
                item {
                    InsightCarousel(
                        insights = state.insights.map { insight ->
                            InsightCardData(
                                headline = insight.headline,
                                body = insight.body,
                                sentiment = insight.sentiment.toDetailUi(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            // Period breakdown
            if (state.comparisons.isNotEmpty()) {
                item {
                    PeriodBreakdownPanel(
                        comparisons = state.comparisons,
                        metric = state.metric,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeTabs(current: Timeframe, onIntent: (MetricDetailIntent) -> Unit) {
    val timeframes = Timeframe.entries
    val idx = timeframes.indexOf(current).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = idx,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        timeframes.forEach { tf ->
            Tab(
                selected = tf == current,
                onClick = { onIntent(MetricDetailIntent.ChangeTimeframe(tf)) },
                text = { Text(tf.label) },
            )
        }
    }
}

@Composable
private fun PeriodPager(state: MetricDetailState, onIntent: (MetricDetailIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onIntent(MetricDetailIntent.MovePeriod(forward = false)) }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous period")
        }
        Text(
            text = periodLabel(state),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onIntent(MetricDetailIntent.MovePeriod(forward = true)) }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun HeadlineBlock(state: MetricDetailState) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatHeadlineNumber(state),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitleFor(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = helperTextFor(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun titleFor(metric: MetricType): String = when (metric) {
    MetricType.Steps -> "Steps"
    MetricType.Distance, MetricType.ExerciseDistance -> "Distance"
    MetricType.Calories, MetricType.ActiveCalories, MetricType.ExerciseCalories -> "Calories"
    MetricType.ZoneMinutes -> "Zone Minutes"
    MetricType.HeartRate -> "Heart Rate"
    MetricType.RestingHeartRate -> "Resting HR"
    MetricType.Sleep -> "Sleep"
    MetricType.Floors -> "Floors"
    MetricType.Speed -> "Speed"
    MetricType.Exercise -> "Exercise"
    MetricType.Weight -> "Weight"
    MetricType.BodyFat -> "Body Fat"
    MetricType.SpO2 -> "SpO2"
    MetricType.SkinTemperature -> "Skin Temp"
    MetricType.HRV -> "HRV"
    MetricType.VO2Max -> "VO2 Max"
}

private fun formatHeadlineNumber(state: MetricDetailState): String {
    val value = if (state.timeframe == Timeframe.Day) state.total else state.average
    return when (state.metric) {
        MetricType.Steps, MetricType.Calories, MetricType.ActiveCalories, MetricType.ZoneMinutes ->
            "%,d".format(value.toInt())
        MetricType.Distance -> "%.2f".format(value)
        else -> "%.1f".format(value)
    }
}

private fun subtitleFor(state: MetricDetailState): String {
    val tf = state.timeframe
    val bucketLabel = when (tf) {
        Timeframe.Day -> "today"
        Timeframe.Week -> "per day (avg)"
        Timeframe.Month -> "per week (avg)"
        Timeframe.ThreeMonths, Timeframe.SixMonths, Timeframe.Year -> "per month (avg)"
    }
    return when (state.metric) {
        MetricType.Steps -> if (tf == Timeframe.Day) "steps today" else "steps $bucketLabel"
        MetricType.Distance -> if (tf == Timeframe.Day) "miles today" else "mi $bucketLabel"
        MetricType.Calories, MetricType.ActiveCalories -> if (tf == Timeframe.Day) "cal today" else "cal $bucketLabel"
        MetricType.ZoneMinutes -> if (tf == Timeframe.Day) "zone min today" else "zone min $bucketLabel"
        else -> bucketLabel
    }
}

private fun helperTextFor(state: MetricDetailState): String {
    if (state.timeframe == Timeframe.Day) {
        val goal = state.goal
        return if (goal != null && goal > 0) {
            val pct = (state.total / goal * 100).toInt()
            "$pct% of daily goal"
        } else ""
    }
    val total = "%,d".format(state.total.toInt())
    val hits = state.goalHitDays
    val bucketWord = when (state.timeframe) {
        Timeframe.Week -> "day"
        Timeframe.Month -> "week"
        Timeframe.ThreeMonths, Timeframe.SixMonths, Timeframe.Year -> "month"
        else -> "day"
    }
    val bucketPlural = if (hits == 1) bucketWord else "${bucketWord}s"
    return when (state.metric) {
        MetricType.Steps -> "You hit your goal on $hits $bucketPlural so far, and took a total of $total steps"
        MetricType.Distance -> "You've covered %.2f miles in this range".format(state.total)
        MetricType.Calories, MetricType.ActiveCalories -> "Total of $total calories burned in this range"
        MetricType.ZoneMinutes -> "Total of $total zone minutes"
        else -> ""
    }
}

private fun periodLabel(state: MetricDetailState): String {
    val anchor = state.periodAnchor
    return when (state.timeframe) {
        Timeframe.Day -> {
            val dow = anchor.dayOfWeek.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            val mon = anchor.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            "$dow, $mon ${anchor.dayOfMonth}"
        }
        Timeframe.Week -> {
            val dow = anchor.dayOfWeek.ordinal
            val monday = anchor.minus(DatePeriod(days = dow))
            val sunday = monday.plus(DatePeriod(days = 6))
            val mLabel = "${monday.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${monday.dayOfMonth}"
            val sLabel = "${sunday.dayOfMonth}"
            "$mLabel – $sLabel"
        }
        Timeframe.Month -> {
            val mon = anchor.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            "$mon ${anchor.year}"
        }
        Timeframe.ThreeMonths -> {
            val end = anchor
            val start = anchor.minus(DatePeriod(months = 3))
            val sLabel = start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val eLabel = end.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$sLabel – $eLabel ${end.year}"
        }
        Timeframe.SixMonths -> {
            val end = anchor
            val start = anchor.minus(DatePeriod(months = 6))
            val sLabel = start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val eLabel = end.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$sLabel – $eLabel ${end.year}"
        }
        Timeframe.Year -> "${anchor.year}"
    }
}

private fun barLabels(state: MetricDetailState): List<String> {
    val zone = TimeZone.currentSystemDefault()
    return when (state.timeframe) {
        Timeframe.Week -> listOf("M", "T", "W", "T", "F", "S", "S")
        Timeframe.Month -> {
            // Weekly buckets: compact "M/D" labels to avoid overlap
            val points = state.series?.points.orEmpty()
            points.map { p ->
                val d = p.bucketStart.toLocalDateTime(zone).date
                "${d.monthNumber}/${d.dayOfMonth}"
            }
        }
        Timeframe.Day -> {
            // 24 hourly labels: 12AM, 4AM, 8AM, 12PM, 4PM, 8PM, 11PM
            val keyHours = setOf(0, 4, 8, 12, 16, 20, 23)
            (0..23).map { h ->
                if (h in keyHours) {
                    when {
                        h == 0 -> "12AM"
                        h < 12 -> "${h}AM"
                        h == 12 -> "12PM"
                        h == 23 -> "11PM"
                        else -> "${h - 12}PM"
                    }
                } else ""
            }
        }
        else -> {
            // 3M/6M/Year: monthly buckets, show month abbreviation
            val points = state.series?.points.orEmpty()
            points.map { p ->
                val d = p.bucketStart.toLocalDateTime(zone).date
                d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }
}

/**
 * Scale the daily goal to match the bar bucket size.
 * Week view: bars = days → daily goal (unchanged).
 * Month view: bars = weeks → daily goal × 7.
 * 3M/6M/Year: bars = months → daily goal × 30.
 * Day view: bars = hours → no goal line (doesn't make sense hourly).
 */
private fun scaledGoal(state: MetricDetailState): Float? {
    val dailyGoal = state.goal?.toFloat() ?: return null
    if (dailyGoal <= 0f) return null
    return when (state.timeframe) {
        Timeframe.Day -> null
        Timeframe.Week -> dailyGoal
        Timeframe.Month -> dailyGoal * 7f
        Timeframe.ThreeMonths, Timeframe.SixMonths, Timeframe.Year -> dailyGoal * 30f
    }
}

private fun formatBarValue(v: Float, metric: MetricType): String = when (metric) {
    MetricType.Distance -> "%.2f".format(v)
    MetricType.Weight, MetricType.BodyFat, MetricType.SpO2, MetricType.SkinTemperature,
    MetricType.HRV, MetricType.VO2Max -> "%.1f".format(v)
    else -> when {
        v >= 100_000f -> "${(v / 1000).toInt()}k"
        v >= 10_000f -> "${"%.1f".format(v / 1000)}k"
        else -> "%,d".format(v.toInt())
    }
}

private fun InsightSentiment.toDetailUi(): InsightSentimentUi = when (this) {
    InsightSentiment.Positive -> InsightSentimentUi.Positive
    InsightSentiment.Neutral -> InsightSentimentUi.Neutral
    InsightSentiment.Negative -> InsightSentimentUi.Negative
    InsightSentiment.Celebratory -> InsightSentimentUi.Celebratory
}

private fun TrendDirection.toDelta() = when (this) {
    TrendDirection.Up -> DeltaDirection.Up
    TrendDirection.Down -> DeltaDirection.Down
    TrendDirection.Flat -> DeltaDirection.Flat
}

// ── Trend Comparison Panel ──────────────────────────────────────────────

@Composable
private fun TrendComparisonPanel(
    wow: DeltaPercent?,
    mom: DeltaPercent?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier
                .padding(20.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrendTile(
                label = "Week over Week",
                shortLabel = "WoW",
                delta = wow,
                modifier = Modifier.weight(1f),
            )

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            TrendTile(
                label = "Month over Month",
                shortLabel = "MoM",
                delta = mom,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrendTile(
    label: String,
    shortLabel: String,
    delta: DeltaPercent?,
    modifier: Modifier = Modifier,
) {
    val direction = delta?.direction?.toDelta()
    val value = delta?.value

    val (tint, icon) = when (direction) {
        DeltaDirection.Up -> SuccessGreen to Icons.Outlined.ArrowUpward
        DeltaDirection.Down -> Color(0xFFB3261E) to Icons.Outlined.ArrowDownward
        DeltaDirection.Flat -> WarnAmber to Icons.Outlined.Remove
        null -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Outlined.Remove
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Animated mini ring showing direction
        TrendMiniRing(
            direction = direction,
            tint = tint,
        )
        Spacer(Modifier.height(10.dp))
        if (value != null) {
            Text(
                text = "%+.1f%%".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrendMiniRing(
    direction: DeltaDirection?,
    tint: Color,
) {
    val targetProgress = when (direction) {
        DeltaDirection.Up -> 0.75f
        DeltaDirection.Down -> 0.35f
        DeltaDirection.Flat -> 0.5f
        null -> 0f
    }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animProgress by animateFloatAsState(
        targetValue = if (appeared) targetProgress else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "trendRing",
    )

    val icon = when (direction) {
        DeltaDirection.Up -> Icons.Outlined.ArrowUpward
        DeltaDirection.Down -> Icons.Outlined.ArrowDownward
        DeltaDirection.Flat -> Icons.Outlined.Remove
        null -> Icons.Outlined.Remove
    }

    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = 5.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(tint.copy(alpha = 0.12f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(tint, -90f, 360f * animProgress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// ── Period Breakdown Panel ──────────────────────────────────────────────

private val breakdownAccents = listOf(
    Color(0xFF42A5F5),
    Color(0xFF66BB6A),
    Color(0xFFFF7043),
    Color(0xFF7C4DFF),
    Color(0xFF26A69A),
    Color(0xFFEC407A),
    Color(0xFFFFA726),
)

@Composable
private fun PeriodBreakdownPanel(
    comparisons: List<PeriodComparison>,
    metric: MetricType,
    modifier: Modifier = Modifier,
) {
    // Parse numeric values for relative bar sizing
    val numericValues = comparisons.map { comp ->
        comp.value.replace(",", "").replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
    }
    val maxValue = numericValues.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Period Breakdown",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            comparisons.forEachIndexed { i, comp ->
                val accent = breakdownAccents[i % breakdownAccents.size]
                val fraction = (numericValues[i] / maxValue).toFloat().coerceIn(0f, 1f)
                val formattedValue = formatBarValue(numericValues[i].toFloat(), metric)

                PeriodBreakdownRow(
                    label = comp.label,
                    value = formattedValue,
                    subtitle = comp.subtitle,
                    accent = accent,
                    fraction = fraction,
                )
                if (i < comparisons.lastIndex) {
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun PeriodBreakdownRow(
    label: String,
    value: String,
    subtitle: String?,
    accent: Color,
    fraction: Float,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animFraction by animateFloatAsState(
        targetValue = if (appeared) fraction else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "barFraction",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color accent dot
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Progress bar
            LinearProgressIndicator(
                progress = { animFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
            )
        }
    }
}
