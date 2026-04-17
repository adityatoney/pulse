package com.pulse.feature.exercise.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.ui.util.timeOfDay
import com.pulse.domain.model.ExerciseSession
import com.pulse.feature.exercise.state.DayMarker
import com.pulse.feature.exercise.state.ExerciseEffect
import com.pulse.feature.exercise.state.ExerciseFilter
import com.pulse.feature.exercise.state.ExerciseIntent
import com.pulse.feature.exercise.state.ExerciseState
import com.pulse.feature.exercise.state.ExerciseTimeframe
import com.pulse.feature.exercise.viewmodel.ExerciseViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: ExerciseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    ExerciseEffect.NavigateBack -> onBack()
                }
            }
    }
    ExerciseScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSessionClick = onNavigateToDetail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    state: ExerciseState,
    onIntent: (ExerciseIntent) -> Unit,
    onBack: () -> Unit,
    onSessionClick: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise", style = MaterialTheme.typography.titleLarge) },
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
            item { ExerciseHeader(state) }
            item { DayCirclesRow(state.dayMarkers) }
            item { FilterChipsRow(state.activeFilter, onIntent) }

            val sortedDays = state.sessionsByDay.keys.sortedDescending()
            sortedDays.forEach { date ->
                val sessions = state.sessionsByDay[date].orEmpty()
                item {
                    DayHeader(date = date, today = state.today)
                }
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        filter = state.activeFilter,
                        onClick = { onSessionClick(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeTabs(current: ExerciseTimeframe, onIntent: (ExerciseIntent) -> Unit) {
    val timeframes = ExerciseTimeframe.entries
    val idx = timeframes.indexOf(current).coerceAtLeast(0)
    TabRow(
        selectedTabIndex = idx,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        timeframes.forEach { tf ->
            Tab(
                selected = tf == current,
                onClick = { onIntent(ExerciseIntent.ChangeTimeframe(tf)) },
                text = { Text(tf.name) },
            )
        }
    }
}

@Composable
private fun PeriodPager(state: ExerciseState, onIntent: (ExerciseIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onIntent(ExerciseIntent.MovePeriod(forward = false)) }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous period")
        }
        Text(
            text = periodLabel(state),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onIntent(ExerciseIntent.MovePeriod(forward = true)) }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun ExerciseHeader(state: ExerciseState) {
    val (headline, unit, subtitle) = when (state.activeFilter) {
        ExerciseFilter.ExerciseDays -> Triple(
            "${state.exerciseDaysHit}",
            "of ${state.exerciseDayGoal} exercise days",
            "You exercised a total of ${state.totalSessions} time${if (state.totalSessions == 1) "" else "s"}",
        )
        ExerciseFilter.Duration -> {
            val hrs = state.totalDurationMin / 60
            val mins = state.totalDurationMin % 60
            val durText = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
            Triple(
                durText,
                "total duration",
                "${state.totalSessions} session${if (state.totalSessions == 1) "" else "s"} across ${state.exerciseDaysHit} day${if (state.exerciseDaysHit == 1) "" else "s"}",
            )
        }
        ExerciseFilter.Distance -> Triple(
            "%.2f".format(state.totalDistanceMiles),
            "miles total",
            "${state.totalSessions} session${if (state.totalSessions == 1) "" else "s"} across ${state.exerciseDaysHit} day${if (state.exerciseDaysHit == 1) "" else "s"}",
        )
        ExerciseFilter.Time -> Triple(
            "${state.totalCalories}",
            "calories burned",
            "${state.totalSessions} session${if (state.totalSessions == 1) "" else "s"} across ${state.exerciseDaysHit} day${if (state.exerciseDaysHit == 1) "" else "s"}",
        )
        ExerciseFilter.ZoneMin -> Triple(
            "${state.totalZoneMin}",
            "active zone minutes",
            "${state.totalSessions} session${if (state.totalSessions == 1) "" else "s"} across ${state.exerciseDaysHit} day${if (state.exerciseDaysHit == 1) "" else "s"}",
        )
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = headline,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCirclesRow(dayMarkers: List<DayMarker>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        dayMarkers.forEach { marker ->
            DayCircle(label = marker.label, hasExercise = marker.hasExercise)
        }
    }
}

@Composable
private fun DayCircle(label: String, hasExercise: Boolean) {
    val exerciseGreen = Color(0xFF1B5E20)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (hasExercise) exerciseGreen
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasExercise) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Exercised",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChipsRow(activeFilter: ExerciseFilter, onIntent: (ExerciseIntent) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ExerciseFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == activeFilter,
                onClick = { onIntent(ExerciseIntent.ChangeFilter(filter)) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val label = when {
        date == today -> "Today"
        date == today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> {
            val dow = date.dayOfWeek.abbreviation()
            val month = date.month.abbreviation()
            "$dow, $month ${date.dayOfMonth}"
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SessionCard(session: ExerciseSession, filter: ExerciseFilter, onClick: () -> Unit = {}) {
    val zone = TimeZone.currentSystemDefault()
    val timeLabel = session.start.timeOfDay(zone)
    val distanceText = session.distanceMeters?.let { "%.2f mi".format(it / 1609.34) }
    val durationText = "${session.durationMinutes} min"
    val caloriesText = session.calories?.let { "${it.toInt()} cal" }
    val zoneMinText = session.zoneMinutes?.let { "$it zone min" }
    val subtitle = listOfNotNull(distanceText, durationText, zoneMinText).joinToString(" \u00B7 ")

    // Right-side stat based on active filter
    val statText = when (filter) {
        ExerciseFilter.ExerciseDays -> null
        ExerciseFilter.Duration -> durationText
        ExerciseFilter.Distance -> distanceText ?: "--"
        ExerciseFilter.Time -> caloriesText ?: "--"
        ExerciseFilter.ZoneMin -> zoneMinText ?: "--"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.type,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (statText != null) {
                Text(
                    text = statText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun periodLabel(state: ExerciseState): String {
    return when (state.timeframe) {
        ExerciseTimeframe.Week -> {
            val dow = state.periodAnchor.dayOfWeek.ordinal
            val monday = state.periodAnchor.minus(DatePeriod(days = dow))
            val sunday = monday.plus(DatePeriod(days = 6))
            val todayDow = state.today.dayOfWeek.ordinal
            val todayMonday = state.today.minus(DatePeriod(days = todayDow))
            if (monday == todayMonday) {
                "This week"
            } else {
                val monLabel = "${monday.month.abbreviation()} ${monday.dayOfMonth}"
                val sunLabel = "${sunday.month.abbreviation()} ${sunday.dayOfMonth}"
                "$monLabel - $sunLabel"
            }
        }
        ExerciseTimeframe.Month -> {
            if (state.periodAnchor.year == state.today.year && state.periodAnchor.monthNumber == state.today.monthNumber) {
                "This month"
            } else {
                "${state.periodAnchor.month.abbreviation()} ${state.periodAnchor.year}"
            }
        }
    }
}

private fun filterLabel(filter: ExerciseFilter): String = when (filter) {
    ExerciseFilter.ExerciseDays -> "Exercise days"
    ExerciseFilter.Duration -> "Duration"
    ExerciseFilter.Distance -> "Distance"
    ExerciseFilter.Time -> "Energy burned"
    ExerciseFilter.ZoneMin -> "Zone minutes"
}

private fun DayOfWeek.abbreviation(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
    else -> name.take(3)
}

private fun kotlinx.datetime.Month.abbreviation(): String = when (this) {
    kotlinx.datetime.Month.JANUARY -> "Jan"
    kotlinx.datetime.Month.FEBRUARY -> "Feb"
    kotlinx.datetime.Month.MARCH -> "Mar"
    kotlinx.datetime.Month.APRIL -> "Apr"
    kotlinx.datetime.Month.MAY -> "May"
    kotlinx.datetime.Month.JUNE -> "Jun"
    kotlinx.datetime.Month.JULY -> "Jul"
    kotlinx.datetime.Month.AUGUST -> "Aug"
    kotlinx.datetime.Month.SEPTEMBER -> "Sep"
    kotlinx.datetime.Month.OCTOBER -> "Oct"
    kotlinx.datetime.Month.NOVEMBER -> "Nov"
    kotlinx.datetime.Month.DECEMBER -> "Dec"
    else -> name.take(3)
}
