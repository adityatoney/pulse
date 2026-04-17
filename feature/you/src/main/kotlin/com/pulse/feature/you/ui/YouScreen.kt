package com.pulse.feature.you.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.designsystem.theme.Coral500
import com.pulse.core.designsystem.theme.Forest300
import com.pulse.core.designsystem.theme.Forest500
import com.pulse.core.designsystem.theme.Forest900
import com.pulse.core.designsystem.theme.Mustard500
import com.pulse.core.designsystem.theme.Sage100
import com.pulse.core.designsystem.theme.Sage300
import com.pulse.core.designsystem.theme.Sky500
import com.pulse.domain.model.MetricType
import com.pulse.domain.usecase.ZoneMinuteCalculator
import com.pulse.feature.you.state.GoalSetting
import com.pulse.feature.you.state.YouEffect
import com.pulse.feature.you.state.YouIntent
import com.pulse.feature.you.state.YouState
import com.pulse.feature.you.viewmodel.YouViewModel

private val ZonePeak = Color(0xFFE53935)
private val ZoneVigorous = Color(0xFFFFA726)
private val ZoneModerate = Color(0xFF43A047)
private val ZoneLight = Color(0xFF29B6F6)
private val ZoneBelowColor = Color(0xFF78909C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouRoute(
    onBack: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    YouEffect.NavigateBack -> onBack()
                }
            }
    }
    YouScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    state: YouState,
    onIntent: (YouIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { ProfileHero(state) }
            item { TodaySnapshot(state) }
            item { GoalsCard(state, onIntent) }
            item { HeartZoneVisual(state) }
            item { BodyStatsRow(state) }
        }
    }

    // Goal editor bottom sheet
    if (state.editingGoal != null) {
        GoalEditorSheet(
            goal = state.editingGoal,
            onDismiss = { onIntent(YouIntent.DismissGoalEditor) },
            onSave = { target -> onIntent(YouIntent.SaveDailyGoal(state.editingGoal.metric, target)) },
        )
    }
}

// ── Profile Hero ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileHero(state: YouState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ringProgress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            ringProgress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        }

        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 4.dp.toPx()
                val radius = (size.minDimension - stroke) / 2
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Forest300, Forest900, Forest500, Forest300)),
                    radius = radius,
                    style = Stroke(width = stroke),
                    alpha = ringProgress.value,
                )
            }
            Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = Sage100,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Forest900,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Member since ${state.memberSince}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Today Snapshot ───────────��────────────────────────���───────────────────────

@Composable
private fun TodaySnapshot(state: YouState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Today",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val stepsGoal = state.goals.find { it.metric == MetricType.Steps }?.dailyTarget ?: 10_000
                val calGoal = state.goals.find { it.metric == MetricType.ActiveCalories }?.dailyTarget ?: 500
                val zmGoal = state.goals.find { it.metric == MetricType.ZoneMinutes }?.dailyTarget ?: 30

                MiniRing(
                    value = state.todaySteps,
                    goal = stepsGoal,
                    label = "Steps",
                    color = Forest500,
                    format = { "%,d".format(it) },
                )
                MiniRing(
                    value = state.weekCalories,
                    goal = calGoal,
                    label = "Cal",
                    color = Coral500,
                    format = { "%,d".format(it) },
                )
                MiniRing(
                    value = state.weekZoneMin,
                    goal = zmGoal,
                    label = "Zone min",
                    color = Mustard500,
                    format = { "$it" },
                )
            }
        }
    }
}

@Composable
private fun MiniRing(
    value: Int,
    goal: Int,
    label: String,
    color: Color,
    format: (Int) -> String,
) {
    val progress = remember { Animatable(0f) }
    val targetPct = (value.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    LaunchedEffect(targetPct) {
        progress.animateTo(targetPct, tween(900, easing = FastOutSlowInEasing))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                format(value),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Goals Card ───────────���───────────────────────────────────────────────────

@Composable
private fun GoalsCard(state: YouState, onIntent: (YouIntent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Daily Goals",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap to adjust your targets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            state.goals.forEach { goal ->
                GoalRow(
                    goal = goal,
                    icon = iconFor(goal.metric),
                    color = colorFor(goal.metric),
                    onClick = { onIntent(YouIntent.EditGoal(goal.metric)) },
                )
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: GoalSetting,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                goal.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                "%,d ${goal.unit} / day".format(goal.dailyTarget),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun iconFor(metric: MetricType): ImageVector = when (metric) {
    MetricType.Steps -> Icons.Outlined.DirectionsRun
    MetricType.Distance -> Icons.Outlined.Straighten
    MetricType.ActiveCalories, MetricType.Calories -> Icons.Outlined.LocalFireDepartment
    MetricType.ZoneMinutes -> Icons.Outlined.Timer
    else -> Icons.Outlined.FavoriteBorder
}

private fun colorFor(metric: MetricType): Color = when (metric) {
    MetricType.Steps -> Forest500
    MetricType.Distance -> Sky500
    MetricType.ActiveCalories, MetricType.Calories -> Coral500
    MetricType.ZoneMinutes -> Mustard500
    else -> Forest500
}

// ── Goal Editor Bottom Sheet ─────────────��───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(
    goal: GoalSetting,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderValue by remember(goal) { mutableFloatStateOf(goal.dailyTarget.toFloat()) }
    val color = colorFor(goal.metric)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconFor(goal.metric), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                goal.label,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(Modifier.height(24.dp))

            // Big number
            Text(
                "%,d".format(sliderValue.toInt()),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Text(
                "${goal.unit} per day",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "= %,d ${goal.unit} per week".format(sliderValue.toInt() * 7),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(28.dp))

            // Slider
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = snapTo(it, goal.step) },
                valueRange = goal.min.toFloat()..goal.max.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = color.copy(alpha = 0.15f),
                ),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "%,d".format(goal.min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "%,d".format(goal.max),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Save button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = color,
                onClick = { onSave(sliderValue.toInt()) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "Set Goal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun snapTo(value: Float, step: Int): Float {
    val rounded = (value / step).toInt() * step
    return rounded.toFloat()
}

// ── Heart Zone Visual ─────────��──────────────────────────────────────────────

@Composable
private fun HeartZoneVisual(state: YouState) {
    val t = state.thresholds

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = ZonePeak,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Heart Rate Zones",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Based on max HR ${t.maxHr}, resting ${t.restingHr} bpm (HRR = ${t.maxHr - t.restingHr})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            ZoneThresholdBar(t)
            Spacer(Modifier.height(24.dp))

            ZoneRow("Peak", "${t.z5}–${t.maxHr} bpm", ZonePeak, "86%+ HRR  \u00b7  2x")
            ZoneRow("Vigorous", "${t.z4}–${t.z5 - 1} bpm", ZoneVigorous, "76–86% HRR  \u00b7  2x")
            ZoneRow("Cardio", "${t.z3}–${t.z4 - 1} bpm", ZoneModerate, "60–76% HRR  \u00b7  1x")
            ZoneRow("Fat Burn", "${t.z2}–${t.z3 - 1} bpm", ZoneLight, "40–60% HRR  \u00b7  1x")
            ZoneRow("Below Zones", "<${t.z2} bpm", ZoneBelowColor, "Not counted")
        }
    }
}

@Composable
private fun ZoneThresholdBar(t: ZoneMinuteCalculator.ZoneThresholds) {
    val barAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        barAnim.animateTo(1f, tween(800, delayMillis = 300, easing = FastOutSlowInEasing))
    }

    val totalRange = (t.maxHr - t.restingHr).toFloat().coerceAtLeast(1f)
    val belowFrac = (t.z2 - t.restingHr) / totalRange
    val fatBurnFrac = (t.z3 - t.z2) / totalRange
    val cardioFrac = (t.z4 - t.z3) / totalRange
    val vigorousFrac = (t.z5 - t.z4) / totalRange
    val peakFrac = (t.maxHr - t.z5) / totalRange

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${t.restingHr}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${t.z2}", style = MaterialTheme.typography.labelSmall, color = ZoneLight)
            Text("${t.z3}", style = MaterialTheme.typography.labelSmall, color = ZoneModerate)
            Text("${t.z4}", style = MaterialTheme.typography.labelSmall, color = ZoneVigorous)
            Text("${t.z5}", style = MaterialTheme.typography.labelSmall, color = ZonePeak)
            Text("${t.maxHr}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            val w = size.width * barAnim.value
            val h = size.height
            var x = 0f
            listOf(
                belowFrac to ZoneBelowColor,
                fatBurnFrac to ZoneLight,
                cardioFrac to ZoneModerate,
                vigorousFrac to ZoneVigorous,
                peakFrac to ZonePeak,
            ).forEach { (frac, color) ->
                val segW = frac * w
                drawRect(color = color, topLeft = Offset(x, 0f), size = Size(segW, h))
                x += segW
            }
        }
    }
}

@Composable
private fun ZoneRow(name: String, range: String, color: Color, hrrLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(hrrLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(range, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

// ── Body Stats ───────────────────────────────────────���───────────────────────

@Composable
private fun BodyStatsRow(state: YouState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatPill("Age", "${state.age}", Modifier.weight(1f))
        StatPill("Resting HR", "${state.restingHr}", Modifier.weight(1f))
        StatPill("Max HR", "${state.maxHr}", Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
