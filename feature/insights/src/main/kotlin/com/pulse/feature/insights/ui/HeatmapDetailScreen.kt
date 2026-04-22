package com.pulse.feature.insights.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.designsystem.theme.Coral500
import com.pulse.core.designsystem.theme.Forest300
import com.pulse.core.designsystem.theme.Sky500
import com.pulse.core.ui.ring.MiniActivityRings
import com.pulse.domain.model.MetricType
import com.pulse.feature.insights.state.HeatmapDay
import com.pulse.feature.insights.state.HeatmapDetailEffect
import com.pulse.feature.insights.state.HeatmapDetailIntent
import com.pulse.feature.insights.state.HeatmapDetailState
import com.pulse.feature.insights.state.HeatmapViewMode
import com.pulse.feature.insights.state.RingDay
import com.pulse.feature.insights.ui.components.cellColor
import com.pulse.feature.insights.ui.components.daysInMonth
import com.pulse.feature.insights.ui.components.monthName
import com.pulse.feature.insights.ui.components.sundayOffset
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
    val monthKey = state.selectedMonth ?: return
    val isRings = state.viewMode == HeatmapViewMode.Rings
    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }
    var selectedRingDay by remember { mutableStateOf<RingDay?>(null) }

    val dayMap = remember(state.heatmapDays) { state.heatmapDays.associateBy { it.date } }
    val ringDayMap = remember(state.ringDays) { state.ringDays.associateBy { it.date } }
    val todayDate = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val monthDays = remember(state.heatmapDays, monthKey) {
        state.heatmapDays.filter { it.date.startsWith(monthKey) }
    }
    val monthRingDays = remember(state.ringDays, monthKey) {
        state.ringDays.filter { it.date.startsWith(monthKey) }
    }
    val months = state.availableMonths
    val monthIdx = months.indexOf(monthKey)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
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
            // ── View mode toggle ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isRings,
                    onClick = { onIntent(HeatmapDetailIntent.ChangeViewMode(HeatmapViewMode.Heatmap)) },
                    label = { Text("Heatmap") },
                )
                FilterChip(
                    selected = isRings,
                    onClick = { onIntent(HeatmapDetailIntent.ChangeViewMode(HeatmapViewMode.Rings)) },
                    label = { Text("Rings") },
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Metric picker chips (heatmap mode only) ──
            AnimatedVisibility(visible = !isRings) {
                val metrics = listOf(
                    MetricType.Steps, MetricType.Distance,
                    MetricType.ActiveCalories, MetricType.ZoneMinutes,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(metrics) { metric ->
                        FilterChip(
                            selected = metric == state.metric,
                            onClick = { onIntent(HeatmapDetailIntent.ChangeMetric(metric)) },
                            label = { Text(chipLabel(metric)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Month navigation header ──
            val year = monthKey.substring(0, 4).toInt()
            val month = monthKey.substring(5, 7).toInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { onIntent(HeatmapDetailIntent.PrevMonth) },
                    enabled = monthIdx > 0,
                ) {
                    Icon(Icons.Outlined.ChevronLeft, "Previous month")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${fullMonthName(month)} $year",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isRings) {
                        val closed = monthRingDays.count { it.allRingsClosed }
                        Text(
                            "$closed of ${monthRingDays.size} rings closed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val monthTotal = monthDays.sumOf { it.rawValue }
                        Text(
                            "${formatValue(monthTotal, state.metric)} ${metricUnit(state.metric)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onIntent(HeatmapDetailIntent.NextMonth) },
                    enabled = monthIdx < months.lastIndex,
                ) {
                    Icon(Icons.Outlined.ChevronRight, "Next month")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Monthly stats ──
            if (isRings) {
                RingsMonthlyStats(monthRingDays)
            } else {
                MonthlyStats(monthDays, state.metric)
            }

            Spacer(Modifier.height(16.dp))

            // ── Animated month content ──
            AnimatedContent(
                targetState = monthKey to state.viewMode,
                transitionSpec = {
                    if (targetState.first > initialState.first) {
                        (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 2 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 2 } + fadeOut())
                    }
                },
                label = "month-transition",
            ) { (currentMonth, mode) ->
                Column {
                    DayOfWeekHeaders()
                    Spacer(Modifier.height(8.dp))
                    when (mode) {
                        HeatmapViewMode.Heatmap -> MonthCalendarGrid(
                            monthKey = currentMonth,
                            dayMap = dayMap,
                            today = todayDate,
                            metric = state.metric,
                            onDayTapped = { day -> selectedDay = day; selectedRingDay = null },
                        )
                        HeatmapViewMode.Rings -> RingsCalendarGrid(
                            monthKey = currentMonth,
                            ringDayMap = ringDayMap,
                            today = todayDate,
                            onDayTapped = { ringDay -> selectedRingDay = ringDay; selectedDay = null },
                        )
                    }
                }
            }

            // ── Ring legend (rings mode only) ──
            if (isRings) {
                Spacer(Modifier.height(12.dp))
                RingLegend()
            }

            Spacer(Modifier.height(12.dp))

            // ── Selected day banner ──
            if (!isRings) {
                selectedDay?.let { day ->
                    SelectedDayBanner(
                        day = day, metric = state.metric,
                        onDismiss = { selectedDay = null },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                selectedRingDay?.let { ringDay ->
                    RingsSelectedDayBanner(
                        ringDay = ringDay,
                        onDismiss = { selectedRingDay = null },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Weekly breakdown ──
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Weekly Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (isRings) {
                RingsWeeklyBreakdown(monthKey, ringDayMap)
            } else {
                WeeklyBreakdown(monthKey, dayMap, state.metric)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Monthly stats: Active Days / Daily Avg / Best Day ──

@Composable
private fun MonthlyStats(days: List<HeatmapDay>, metric: MetricType) {
    val nonZero = days.filter { it.rawValue > 0 }
    val total = nonZero.sumOf { it.rawValue }
    val avg = if (nonZero.isNotEmpty()) total / nonZero.size else 0.0
    val best = nonZero.maxByOrNull { it.rawValue }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem("Active Days", "${nonZero.size}", "of ${days.size}")
        StatItem("Daily Avg", formatValue(avg, metric), metricUnit(metric))
        best?.let {
            val label = try {
                val d = LocalDate.parse(it.date)
                "${monthName(d.monthNumber)} ${d.dayOfMonth}"
            } catch (_: Exception) { it.date }
            StatItem("Best Day", formatValue(it.rawValue, metric), label)
        }
    }
}

// ── Rings monthly stats: Rings Closed / Best Streak / Avg Progress ──

@Composable
private fun RingsMonthlyStats(days: List<RingDay>) {
    val closed = days.count { it.allRingsClosed }
    val streak = bestStreak(days)
    val avgPct = if (days.isNotEmpty()) {
        days.map { (it.stepsProgress + it.caloriesProgress + it.distanceProgress) / 3f }
            .average() * 100
    } else 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem("Rings Closed", "$closed", "of ${days.size} days")
        StatItem("Best Streak", "$streak", "days")
        StatItem("Avg Progress", "${avgPct.toInt()}%", "all rings")
    }
}

private fun bestStreak(days: List<RingDay>): Int {
    var max = 0
    var current = 0
    for (day in days.sortedBy { it.date }) {
        if (day.allRingsClosed) {
            current++
            if (current > max) max = current
        } else {
            current = 0
        }
    }
    return max
}

@Composable
private fun StatItem(label: String, value: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Ring legend ──

@Composable
private fun RingLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendItem(Forest300, "Steps")
        LegendItem(Coral500, "Calories")
        LegendItem(Sky500, "Distance")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Day-of-week header row ──

@Composable
private fun DayOfWeekHeaders() {
    Row(Modifier.fillMaxWidth()) {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dow ->
            Text(
                text = dow,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Rings calendar grid ──

@Composable
private fun RingsCalendarGrid(
    monthKey: String,
    ringDayMap: Map<String, RingDay>,
    today: LocalDate,
    onDayTapped: (RingDay) -> Unit,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val daysCount = daysInMonth(year, month)
    val firstDowOffset = sundayOffset(LocalDate(year, month, 1).dayOfWeek)
    val numRows = (firstDowOffset + daysCount + 6) / 7

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary

    // Scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubRingDay by remember { mutableStateOf<RingDay?>(null) }
    var scrubPosition by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightDp = 56.dp
    val rowHeightPx = with(density) { rowHeightDp.toPx() }

    fun dayFromOffset(offset: Offset): RingDay? {
        if (gridSize.width <= 0) return null
        val col = (offset.x / (gridSize.width / 7f)).toInt().coerceIn(0, 6)
        val row = (offset.y / rowHeightPx).toInt().coerceIn(0, numRows - 1)
        val dayNum = row * 7 + col - firstDowOffset + 1
        if (dayNum !in 1..daysCount) return null
        val cellDate = LocalDate(year, month, dayNum)
        if (cellDate > today) return null
        return ringDayMap["$monthKey-${dayNum.toString().padStart(2, '0')}"]
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { gridSize = it }
                .pointerInput(monthKey) {
                    detectTapGestures { offset ->
                        dayFromOffset(offset)?.let(onDayTapped)
                    }
                }
                .pointerInput(monthKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            isScrubbing = true
                            scrubPosition = offset
                            scrubRingDay = dayFromOffset(offset)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubPosition = change.position
                            val day = dayFromOffset(change.position)
                            if (day != null && day.date != scrubRingDay?.date) {
                                scrubRingDay = day
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            scrubRingDay?.let(onDayTapped)
                            isScrubbing = false
                            scrubRingDay = null
                        },
                        onDragCancel = {
                            isScrubbing = false
                            scrubRingDay = null
                        },
                    )
                },
        ) {
            for (row in 0 until numRows) {
                Row(
                    Modifier.fillMaxWidth().height(rowHeightDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (col in 0..6) {
                        val dayNum = row * 7 + col - firstDowOffset + 1
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayNum in 1..daysCount) {
                                val dateStr = "$monthKey-${dayNum.toString().padStart(2, '0')}"
                                val cellDate = LocalDate(year, month, dayNum)
                                val isFuture = cellDate > today
                                val isToday = cellDate == today
                                val ringDay = ringDayMap[dateStr]
                                val isScrubTarget = isScrubbing && scrubRingDay?.date == dateStr

                                if (!isFuture && ringDay != null) {
                                    Box(
                                        modifier = Modifier.then(
                                            if (isToday || isScrubTarget) Modifier.border(
                                                1.5.dp,
                                                if (isScrubTarget) tertiary else onSurface,
                                                CircleShape,
                                            ) else Modifier
                                        ),
                                    ) {
                                        MiniActivityRings(
                                            outerProgress = ringDay.stepsProgress,
                                            middleProgress = ringDay.caloriesProgress,
                                            innerProgress = ringDay.distanceProgress,
                                            dayNumber = dayNum,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (!isFuture) "$dayNum" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (isFuture) onSurfaceVariant.copy(alpha = 0.3f)
                                        else onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Scrub tooltip overlay ──
        if (isScrubbing && scrubRingDay != null) {
            val tooltipWidthDp = 180.dp
            val tooltipHeightDp = 90.dp
            val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
            val tooltipHeightPx = with(density) { tooltipHeightDp.toPx() }
            val gapPx = with(density) { 12.dp.toPx() }

            val tx = (scrubPosition.x - tooltipWidthPx / 2)
                .coerceIn(0f, gridSize.width.toFloat() - tooltipWidthPx)
            val ty = (scrubPosition.y - tooltipHeightPx - gapPx)
                .coerceAtLeast(0f)

            Surface(
                modifier = Modifier
                    .offset(
                        x = with(density) { tx.toDp() },
                        y = with(density) { ty.toDp() },
                    )
                    .width(tooltipWidthDp),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                val sd = scrubRingDay!!
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        formatDate(sd.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    TooltipMetricRow(Forest300, "Steps", sd.stepsValue, sd.stepsGoal, MetricType.Steps)
                    TooltipMetricRow(Coral500, "Cal", sd.caloriesValue, sd.caloriesGoal, MetricType.ActiveCalories)
                    TooltipMetricRow(Sky500, "Dist", sd.distanceValue, sd.distanceGoal, MetricType.Distance)
                }
            }
        }
    }
}

@Composable
private fun TooltipMetricRow(color: Color, label: String, value: Double, goal: Double, metric: MetricType) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            "$label: ${formatValue(value, metric)} / ${formatValue(goal, metric)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontSize = 10.sp,
        )
    }
}

// ── Heatmap calendar grid with proportional circles + scrub gesture ──

@Composable
private fun MonthCalendarGrid(
    monthKey: String,
    dayMap: Map<String, HeatmapDay>,
    today: LocalDate,
    metric: MetricType,
    onDayTapped: (HeatmapDay) -> Unit,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val daysCount = daysInMonth(year, month)
    val firstDowOffset = sundayOffset(LocalDate(year, month, 1).dayOfWeek)
    val numRows = (firstDowOffset + daysCount + 6) / 7

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary

    val maxVal = remember(monthKey, dayMap) {
        (1..daysCount).maxOfOrNull { d ->
            dayMap["$monthKey-${d.toString().padStart(2, '0')}"]?.rawValue ?: 0.0
        }?.coerceAtLeast(1.0) ?: 1.0
    }

    // Scrub state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubDay by remember { mutableStateOf<HeatmapDay?>(null) }
    var scrubPosition by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightDp = 48.dp
    val rowHeightPx = with(density) { rowHeightDp.toPx() }

    fun dayFromOffset(offset: Offset): HeatmapDay? {
        if (gridSize.width <= 0) return null
        val col = (offset.x / (gridSize.width / 7f)).toInt().coerceIn(0, 6)
        val row = (offset.y / rowHeightPx).toInt().coerceIn(0, numRows - 1)
        val dayNum = row * 7 + col - firstDowOffset + 1
        if (dayNum !in 1..daysCount) return null
        val cellDate = LocalDate(year, month, dayNum)
        if (cellDate > today) return null
        return dayMap["$monthKey-${dayNum.toString().padStart(2, '0')}"]
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { gridSize = it }
                .pointerInput(monthKey) {
                    detectTapGestures { offset ->
                        dayFromOffset(offset)?.let(onDayTapped)
                    }
                }
                .pointerInput(monthKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            isScrubbing = true
                            scrubPosition = offset
                            scrubDay = dayFromOffset(offset)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubPosition = change.position
                            val day = dayFromOffset(change.position)
                            if (day != null && day.date != scrubDay?.date) {
                                scrubDay = day
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            scrubDay?.let(onDayTapped)
                            isScrubbing = false
                            scrubDay = null
                        },
                        onDragCancel = {
                            isScrubbing = false
                            scrubDay = null
                        },
                    )
                },
        ) {
            for (row in 0 until numRows) {
                Row(
                    Modifier.fillMaxWidth().height(rowHeightDp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (col in 0..6) {
                        val dayNum = row * 7 + col - firstDowOffset + 1
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayNum in 1..daysCount) {
                                val dateStr = "$monthKey-${dayNum.toString().padStart(2, '0')}"
                                val cellDate = LocalDate(year, month, dayNum)
                                val isFuture = cellDate > today
                                val isToday = cellDate == today
                                val day = dayMap[dateStr]
                                val value = day?.rawValue ?: 0.0
                                val isScrubTarget = isScrubbing && scrubDay?.date == dateStr

                                val fraction = if (value > 0 && !isFuture) {
                                    (value / maxVal).toFloat().coerceIn(0.15f, 1f)
                                } else 0f

                                if (fraction > 0f) {
                                    val circleSize = lerp(22f, 40f, fraction).dp
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(cellColor(fraction, primary, surfaceVariant))
                                            .then(
                                                if (isToday || isScrubTarget) Modifier.border(
                                                    2.dp,
                                                    if (isScrubTarget) tertiary else onSurface,
                                                    CircleShape,
                                                ) else Modifier
                                            )
                                            .height(circleSize)
                                            .width(circleSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = if (fraction > 0.5f) onPrimary else onSurface,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (!isFuture) "$dayNum" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (isFuture) onSurfaceVariant.copy(alpha = 0.3f)
                                        else onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Scrub tooltip overlay ──
        if (isScrubbing && scrubDay != null) {
            val tooltipWidthDp = 150.dp
            val tooltipHeightDp = 52.dp
            val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
            val tooltipHeightPx = with(density) { tooltipHeightDp.toPx() }
            val gapPx = with(density) { 12.dp.toPx() }

            val tx = (scrubPosition.x - tooltipWidthPx / 2)
                .coerceIn(0f, gridSize.width.toFloat() - tooltipWidthPx)
            val ty = (scrubPosition.y - tooltipHeightPx - gapPx)
                .coerceAtLeast(0f)

            Surface(
                modifier = Modifier
                    .offset(
                        x = with(density) { tx.toDp() },
                        y = with(density) { ty.toDp() },
                    )
                    .width(tooltipWidthDp),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sd = scrubDay!!
                    Text(
                        formatDate(sd.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Text(
                        "${formatValue(sd.rawValue, metric)} ${metricUnit(metric)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

// ── Selected day banner ──

@Composable
private fun SelectedDayBanner(day: HeatmapDay, metric: MetricType, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    formatDate(day.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${formatValue(day.rawValue, metric)} ${metricUnit(metric)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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

// ── Rings selected day banner ──

@Composable
private fun RingsSelectedDayBanner(ringDay: RingDay, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDate(ringDay.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                BannerMetricRow(Forest300, "Steps", ringDay.stepsValue, ringDay.stepsGoal, ringDay.stepsProgress, MetricType.Steps)
                BannerMetricRow(Coral500, "Calories", ringDay.caloriesValue, ringDay.caloriesGoal, ringDay.caloriesProgress, MetricType.ActiveCalories)
                BannerMetricRow(Sky500, "Distance", ringDay.distanceValue, ringDay.distanceGoal, ringDay.distanceProgress, MetricType.Distance)
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

@Composable
private fun BannerMetricRow(
    color: Color,
    label: String,
    value: Double,
    goal: Double,
    progress: Float,
    metric: MetricType,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            "$label: ${formatValue(value, metric)} / ${formatValue(goal, metric)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (progress >= 1f) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

// ── Weekly breakdown with inline bar charts ──

@Composable
private fun WeeklyBreakdown(
    monthKey: String,
    dayMap: Map<String, HeatmapDay>,
    metric: MetricType,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val daysCount = daysInMonth(year, month)

    data class WeekData(val startDay: Int, val endDay: Int, val total: Double)

    val weeks = remember(monthKey, dayMap) {
        val result = mutableListOf<WeekData>()
        var d = 1
        while (d <= daysCount) {
            val date = LocalDate(year, month, d)
            val dow = sundayOffset(date.dayOfWeek)
            val weekEnd = (d + (6 - dow)).coerceAtMost(daysCount)
            var total = 0.0
            for (dd in d..weekEnd) {
                total += dayMap["$monthKey-${dd.toString().padStart(2, '0')}"]?.rawValue ?: 0.0
            }
            result += WeekData(d, weekEnd, total)
            d = weekEnd + 1
        }
        result
    }

    val maxWeekTotal = weeks.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    weeks.forEach { week ->
        val label = if (week.startDay == week.endDay) {
            "${monthName(month)} ${week.startDay}"
        } else {
            "${monthName(month)} ${week.startDay} \u2013 ${week.endDay}"
        }
        val fraction = (week.total / maxWeekTotal).toFloat().coerceIn(0f, 1f)

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatValue(week.total, metric)} ${metricUnit(metric)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(trackColor),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
            }
        }
    }
}

// ── Rings weekly breakdown ──

@Composable
private fun RingsWeeklyBreakdown(
    monthKey: String,
    ringDayMap: Map<String, RingDay>,
) {
    val year = monthKey.substring(0, 4).toInt()
    val month = monthKey.substring(5, 7).toInt()
    val daysCount = daysInMonth(year, month)

    data class WeekRingData(val startDay: Int, val endDay: Int, val closed: Int, val total: Int)

    val weeks = remember(monthKey, ringDayMap) {
        val result = mutableListOf<WeekRingData>()
        var d = 1
        while (d <= daysCount) {
            val date = LocalDate(year, month, d)
            val dow = sundayOffset(date.dayOfWeek)
            val weekEnd = (d + (6 - dow)).coerceAtMost(daysCount)
            var closed = 0
            var count = 0
            for (dd in d..weekEnd) {
                val rd = ringDayMap["$monthKey-${dd.toString().padStart(2, '0')}"]
                if (rd != null) {
                    count++
                    if (rd.allRingsClosed) closed++
                }
            }
            result += WeekRingData(d, weekEnd, closed, count)
            d = weekEnd + 1
        }
        result
    }

    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    weeks.forEach { week ->
        val label = if (week.startDay == week.endDay) {
            "${monthName(month)} ${week.startDay}"
        } else {
            "${monthName(month)} ${week.startDay} \u2013 ${week.endDay}"
        }
        val fraction = if (week.total > 0) (week.closed.toFloat() / week.total) else 0f

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${week.closed} / ${week.total} closed",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(trackColor),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor),
                )
            }
        }
    }
}

// ── Helpers ──

private fun formatValue(value: Double, metric: MetricType): String = when (metric) {
    MetricType.Distance -> "%.1f".format(value)
    else -> when {
        value >= 100_000 -> "${(value / 1000).toInt()}k"
        value >= 10_000 -> "${"%.1f".format(value / 1000)}k"
        value >= 1_000 -> "%,d".format(value.toInt())
        value == 0.0 -> "0"
        value < 1.0 -> "%.1f".format(value)
        else -> "${value.toInt()}"
    }
}

private fun metricUnit(metric: MetricType): String = when (metric) {
    MetricType.Steps -> "steps"
    MetricType.Distance -> "mi"
    MetricType.ActiveCalories -> "cal"
    MetricType.ZoneMinutes -> "min"
    else -> ""
}

private fun chipLabel(metric: MetricType): String = when (metric) {
    MetricType.Steps -> "Steps"
    MetricType.Distance -> "Distance"
    MetricType.ActiveCalories -> "Calories"
    MetricType.ZoneMinutes -> "Zone Min"
    else -> metric.name
}

private fun fullMonthName(month: Int): String = when (month) {
    1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
    5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
    9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
    else -> ""
}

private fun formatDate(dateStr: String): String = try {
    val date = LocalDate.parse(dateStr)
    "${fullMonthName(date.monthNumber)} ${date.dayOfMonth}, ${date.year}"
} catch (_: Exception) { dateStr }
