package com.pulse.feature.sleep.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.core.ui.charts.HrChartPoint
import com.pulse.core.ui.charts.IntradayHrChart
import com.pulse.core.ui.charts.SleepClockDial
import com.pulse.core.ui.charts.SleepStage
import com.pulse.core.ui.charts.SleepStageColors
import com.pulse.core.ui.charts.SleepStagesTimeline
import com.pulse.feature.sleep.state.SleepNightState
import com.pulse.feature.sleep.viewmodel.SleepNightViewModel

@Composable
fun SleepNightRoute(
    onBack: () -> Unit,
    viewModel: SleepNightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SleepNightScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepNightScreen(
    state: SleepNightState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sleep Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.sleep == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No sleep data", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Sleep Score ring
            state.sleepScore?.let { score ->
                SleepScoreRing(score = score)
                Spacer(Modifier.height(8.dp))
            }

            // Clock dial
            SleepClockDial(
                bedtimeHour = state.bedtimeHour,
                wakeHour = state.wakeHour,
                durationLabel = state.durationLabel,
            )

            Spacer(Modifier.height(24.dp))

            // Duration section — rich visual cards
            DurationPanel(
                asleep = state.durationLabel,
                inBed = state.inBedLabel,
                efficiency = state.efficiency,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Schedule section — rich visual
            SchedulePanel(
                bedtime = state.bedtimeLabel,
                wakeTime = state.wakeTimeLabel,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Sleep stages
            val sleep = state.sleep
            if (sleep.deepMinutes != null) {
                SectionCard(title = "Sleep Stages", modifier = Modifier.padding(horizontal = 16.dp)) {
                    SleepStagesTimeline(
                        stages = listOfNotNull(
                            sleep.deepMinutes?.let { SleepStage("Deep", it, SleepStageColors.Deep) },
                            sleep.remMinutes?.let { SleepStage("REM", it, SleepStageColors.Rem) },
                            sleep.lightMinutes?.let { SleepStage("Light", it, SleepStageColors.Light) },
                            sleep.awakeMinutes?.let { SleepStage("Awake", it, SleepStageColors.Awake) },
                        ),
                    )

                    state.spo2?.let { spo2 ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Oxygen level", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("${spo2.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // HR during sleep
            if (state.hrSamples.isNotEmpty()) {
                SectionCard(title = "Heart rate during sleep", modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.avgHrDuringSleep?.let { avg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Favorite, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$avg bpm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("avg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    IntradayHrChart(
                        points = state.hrSamples.map { HrChartPoint(it.timestampMs, it.bpm) },
                        modifier = Modifier.height(120.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Sleep Score Ring ──────────────────────────────────────────────────

@Composable
private fun SleepScoreRing(score: Int) {
    val scoreColor = when {
        score >= 80 -> Color(0xFF4CAF50)
        score >= 60 -> Color(0xFF42A5F5)
        else -> Color(0xFFFF8A65)
    }
    val trackColor = scoreColor.copy(alpha = 0.15f)

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animProgress by animateFloatAsState(
        targetValue = if (appeared) (score / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scoreRing",
    )

    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val stroke = 8.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(scoreColor, -90f, 360f * animProgress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scoreColor)
            Text("Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Duration Panel ───────────────────────────────────────────────────

@Composable
private fun DurationPanel(
    asleep: String,
    inBed: String,
    efficiency: Float,
    modifier: Modifier = Modifier,
) {
    val effColor = when {
        efficiency >= 90f -> Color(0xFF4CAF50)
        efficiency >= 75f -> Color(0xFF42A5F5)
        else -> Color(0xFFFF8A65)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Duration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Asleep stat
                DurationStatTile(
                    icon = Icons.Outlined.Bedtime,
                    iconTint = Color(0xFF3F51B5),
                    value = asleep,
                    label = "Asleep",
                    modifier = Modifier.weight(1f),
                )

                // Vertical divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // In bed stat
                DurationStatTile(
                    icon = Icons.Outlined.Bedtime,
                    iconTint = Color(0xFF7C4DFF),
                    value = inBed,
                    label = "In bed",
                    modifier = Modifier.weight(1f),
                )

                // Vertical divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )

                // Efficiency with mini ring
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EfficiencyMiniRing(efficiency = efficiency, color = effColor)
                    Spacer(Modifier.height(4.dp))
                    Text("Efficiency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DurationStatTile(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EfficiencyMiniRing(efficiency: Float, color: Color) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animProgress by animateFloatAsState(
        targetValue = if (appeared) (efficiency / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "effRing",
    )

    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val stroke = 5.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animProgress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            "${"%.0f".format(efficiency)}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// ── Schedule Panel ───────────────────────────────────────────────────

@Composable
private fun SchedulePanel(
    bedtime: String,
    wakeTime: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Schedule", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Bedtime card
                ScheduleTimeCard(
                    icon = Icons.Outlined.Bedtime,
                    iconBgColor = Color(0xFF3F51B5),
                    time = bedtime,
                    label = "Bedtime",
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(12.dp))

                // Arrow connector
                Column(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(width = 32.dp, height = 2.dp)) {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF3F51B5).copy(alpha = 0.6f), Color(0xFFFFA726).copy(alpha = 0.6f))
                            ),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Wake time card
                ScheduleTimeCard(
                    icon = Icons.Outlined.WbSunny,
                    iconBgColor = Color(0xFFFFA726),
                    time = wakeTime,
                    label = "Wake up",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ScheduleTimeCard(
    icon: ImageVector,
    iconBgColor: Color,
    time: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = iconBgColor.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconBgColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                time,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Section Card ─────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
