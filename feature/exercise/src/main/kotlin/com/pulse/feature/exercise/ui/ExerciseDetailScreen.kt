package com.pulse.feature.exercise.ui

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.pulse.domain.model.ExerciseDetail
import com.pulse.domain.model.ExerciseLap
import com.pulse.domain.model.HrSample
import com.pulse.domain.model.RoutePoint
import com.pulse.feature.exercise.state.EditField
import com.pulse.feature.exercise.state.ExerciseDetailEffect
import com.pulse.feature.exercise.state.ExerciseDetailIntent
import com.pulse.feature.exercise.state.ExerciseDetailState
import com.pulse.feature.exercise.state.RoutePointData
import com.pulse.feature.exercise.viewmodel.ExerciseDetailViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val METERS_PER_MILE = 1_609.34

// 5-zone HR colors (HRR-based thresholds computed at runtime)
private const val USER_AGE = 45
private const val USER_RESTING_HR = 72
private val Zone5Peak = Color(0xFFE53935)
private val Zone4Vigorous = Color(0xFFFFA726)
private val Zone3Moderate = Color(0xFF43A047)
private val Zone2Light = Color(0xFF29B6F6)
private val Zone1VeryLight = Color(0xFF78909C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailRoute(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val routeConsentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.contracts.ExerciseRouteRequestContract(),
    ) { exerciseRoute ->
        Log.d("Health", "Route consent result: route=${exerciseRoute != null}, points=${exerciseRoute?.route?.size ?: 0}")
        if (exerciseRoute != null) {
            val points = exerciseRoute.route.map { loc ->
                RoutePointData(
                    timestampMs = loc.time.toEpochMilli(),
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude?.inMeters,
                )
            }
            viewModel.onIntent(ExerciseDetailIntent.RouteConsentResult(points))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    ExerciseDetailEffect.NavigateBack -> onBack()
                    is ExerciseDetailEffect.LaunchRouteConsent -> {
                        routeConsentLauncher.launch(effect.sessionId)
                    }
                }
            }
    }
    ExerciseDetailScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        onRequestRouteConsent = { viewModel.onIntent(ExerciseDetailIntent.RequestRouteConsent) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailState,
    onBack: () -> Unit,
    onIntent: (ExerciseDetailIntent) -> Unit = {},
    onRequestRouteConsent: () -> Unit = {},
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
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { onIntent(ExerciseDetailIntent.OpenEdit) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.detail == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Activity not found", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> DetailContent(
                detail = state.detail,
                modifier = Modifier.padding(padding),
                onRequestRouteConsent = onRequestRouteConsent,
            )
        }
    }

    if (state.showEditDialog) {
        EditExerciseDialog(
            calories = state.editCalories,
            distance = state.editDistance,
            steps = state.editSteps,
            onCaloriesChange = { onIntent(ExerciseDetailIntent.UpdateEditField(EditField.Calories, it)) },
            onDistanceChange = { onIntent(ExerciseDetailIntent.UpdateEditField(EditField.Distance, it)) },
            onStepsChange = { onIntent(ExerciseDetailIntent.UpdateEditField(EditField.Steps, it)) },
            onSave = { onIntent(ExerciseDetailIntent.SaveEdit) },
            onDismiss = { onIntent(ExerciseDetailIntent.DismissEdit) },
        )
    }
}

@Composable
private fun DetailContent(
    detail: ExerciseDetail,
    modifier: Modifier = Modifier,
    onRequestRouteConsent: () -> Unit = {},
) {
    val session = detail.session
    val zone = TimeZone.currentSystemDefault()
    val startDt = session.start.toLocalDateTime(zone)
    val dateStr = "%s, %s %d".format(
        startDt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
        startDt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
        startDt.dayOfMonth,
    )
    val timeStr = "%d:%02d %s".format(
        if (startDt.hour % 12 == 0) 12 else startDt.hour % 12,
        startDt.minute,
        if (startDt.hour < 12) "AM" else "PM",
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Google Maps route (if route data exists)
        if (detail.route.isNotEmpty()) {
            item { GoogleMapsRouteSection(route = detail.route, hrSamples = detail.hrSamples) }
        } else if (detail.routeConsentRequired) {
            item {
                RouteConsentCard(onRequestRouteConsent = onRequestRouteConsent)
            }
        }

        // Hero header
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = session.type,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$dateStr \u2022 $timeStr",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Stats cards
        item {
            Spacer(Modifier.height(12.dp))
            StatsSection(detail)
        }

        // HR Zones
        if (detail.hrSamples.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                HrZonesCard(detail.hrSamples, detail.session.start, detail.session.end)
            }
        }

        // Pace Laps
        if (detail.laps.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                SectionCard(title = "Pace laps") {
                    Column {
                        LapHeaderRow()
                        detail.laps.forEach { lap -> LapRow(lap) }
                    }
                }
            }
        }

        // Elevation
        detail.elevationGainMeters?.let { elev ->
            item {
                Spacer(Modifier.height(16.dp))
                SectionCard(title = "Elevation gain") {
                    val ft = (elev * 3.28084).toInt()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(
                            Icons.Outlined.Terrain, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("$ft", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.width(4.dp))
                        Text("ft", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }
}

// ── Google Maps Route ────────────────────────────────────────────────────────

@Composable
private fun RouteConsentCard(onRequestRouteConsent: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onRequestRouteConsent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Terrain,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap to load route map",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Health Connect will ask for permission to share location data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun GoogleMapsRouteSection(route: List<RoutePoint>, hrSamples: List<HrSample>) {
    val zt = remember { com.pulse.domain.usecase.ZoneMinuteCalculator.thresholds(USER_RESTING_HR, USER_AGE) }

    val hrByTime = remember(hrSamples) {
        hrSamples.associate { it.timestampMs to it.bpm }
    }

    // Build colored polyline segments
    val segments = remember(route, hrByTime) {
        buildRouteSegments(route, hrByTime, zt.z5, zt.z4, zt.z3, zt.z2)
    }

    val bounds = remember(route) {
        val builder = LatLngBounds.builder()
        route.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
        builder.build()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds.center, 14f)
    }

    LaunchedEffect(bounds) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
            ),
        ) {
            if (segments.isNotEmpty()) {
                // Draw each colored segment
                segments.forEach { (points, color) ->
                    Polyline(
                        points = points,
                        color = color,
                        width = 18f,
                    )
                }
            } else {
                // Fallback: draw full route in default color when no HR data for coloring
                val allPoints = route.map { LatLng(it.latitude, it.longitude) }
                Polyline(
                    points = allPoints,
                    color = Zone2Light,
                    width = 18f,
                )
            }
        }

        // Zone legend overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoneLegendDot(Zone1VeryLight, "Below")
            ZoneLegendDot(Zone2Light, "Light")
            ZoneLegendDot(Zone3Moderate, "Moderate")
            ZoneLegendDot(Zone4Vigorous, "Vigorous")
            ZoneLegendDot(Zone5Peak, "Peak")
        }
    }
}

private data class PolySegment(val points: List<LatLng>, val color: Color)

private fun buildRouteSegments(
    route: List<RoutePoint>,
    hrByTime: Map<Long, Int>,
    z5Thresh: Int,
    z4Thresh: Int,
    z3Thresh: Int,
    z2Thresh: Int,
): List<PolySegment> {
    if (route.size < 2) return emptyList()
    val segments = mutableListOf<PolySegment>()
    var currentColor: Color? = null
    var currentPoints = mutableListOf<LatLng>()

    for (i in route.indices) {
        val pt = route[i]
        val bpm = findClosestBpm(pt.timestampMs, hrByTime)
        val color = bpmToZoneColor(bpm, z5Thresh, z4Thresh, z3Thresh, z2Thresh)
        val latLng = LatLng(pt.latitude, pt.longitude)

        if (color != currentColor && currentPoints.isNotEmpty()) {
            segments += PolySegment(currentPoints.toList(), currentColor ?: Zone2Light)
            currentPoints = mutableListOf(currentPoints.last()) // bridge
        }
        currentColor = color
        currentPoints += latLng
    }
    if (currentPoints.size >= 2) {
        segments += PolySegment(currentPoints, currentColor ?: Zone2Light)
    }
    return segments
}

private fun bpmToZoneColor(bpm: Int?, z5Thresh: Int, z4Thresh: Int, z3Thresh: Int, z2Thresh: Int): Color =
    when {
        bpm == null -> Zone2Light
        bpm >= z5Thresh -> Zone5Peak
        bpm >= z4Thresh -> Zone4Vigorous
        bpm >= z3Thresh -> Zone3Moderate
        bpm >= z2Thresh -> Zone2Light
        else -> Zone1VeryLight
    }

private fun findClosestBpm(timestampMs: Long, hrByTime: Map<Long, Int>): Int? {
    if (hrByTime.isEmpty()) return null
    val closest = hrByTime.keys.minByOrNull { kotlin.math.abs(it - timestampMs) } ?: return null
    return if (kotlin.math.abs(closest - timestampMs) < 60_000) hrByTime[closest] else null
}

@Composable
private fun ZoneLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Stats Section ────────────────────────────────────────────────────────────

private val DurationColor = Color(0xFF42A5F5)
private val DistanceColor = Color(0xFF66BB6A)
private val CaloriesColor = Color(0xFFFF7043)
private val StepsColor = Color(0xFF7C4DFF)
private val ZoneColor = Color(0xFFE53935)
private val PaceColor = Color(0xFF26A69A)

@Composable
private fun StatsSection(detail: ExerciseDetail) {
    val session = detail.session
    val durationMin = session.durationMinutes
    val durationStr = if (durationMin >= 60) "${durationMin / 60}h ${durationMin % 60}m"
    else {
        val secs = ((session.end.toEpochMilliseconds() - session.start.toEpochMilliseconds()) / 1000 % 60)
        "${durationMin}m ${secs}s"
    }
    val distMi = session.distanceMeters?.let { it / METERS_PER_MILE }
    val distStr = distMi?.let { "%.2f" .format(it) } ?: "--"
    val calStr = session.calories?.toInt()?.let { "%,d".format(it) } ?: "--"
    val stepsStr = detail.steps?.let { "%,d".format(it) } ?: "--"
    val zoneMin = detail.zoneMinutes
    val paceStr = detail.avgPaceSecondsPerMile?.let {
        "%d'%02d\"".format(it / 60, it % 60)
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Duration hero ring
        DurationHeroRing(
            durationLabel = durationStr,
            durationMinutes = durationMin,
        )

        // Key metrics panel: Distance | Calories | Steps
        KeyMetricsPanel(
            distStr = distStr,
            calStr = calStr,
            stepsStr = stepsStr,
            hasDistance = distMi != null,
        )

        // Performance panel: Zone Minutes + Avg Pace
        if (zoneMin != null || paceStr != null) {
            PerformancePanel(
                zoneMinutes = zoneMin,
                paceStr = paceStr,
                durationMinutes = durationMin,
            )
        }
    }
}

// ── Duration Hero Ring ──────────────────────────────────────────────────

@Composable
private fun DurationHeroRing(
    durationLabel: String,
    durationMinutes: Long,
) {
    // Progress arc based on workout length (60 min = full ring)
    val targetProgress = (durationMinutes / 60f).coerceIn(0f, 1f)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animProgress by animateFloatAsState(
        targetValue = if (appeared) targetProgress else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "durationRing",
    )

    val arcColor = DurationColor
    val trackColor = arcColor.copy(alpha = 0.12f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(120.dp)) {
                    val stroke = 10.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)

                    drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(arcColor.copy(alpha = 0.6f), arcColor),
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = arcColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        durationLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Key Metrics Panel ───────────────────────────────────────────────────

@Composable
private fun KeyMetricsPanel(
    distStr: String,
    calStr: String,
    stepsStr: String,
    hasDistance: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricStatTile(
                icon = Icons.Outlined.Straighten,
                iconTint = DistanceColor,
                value = distStr,
                unit = if (hasDistance) "mi" else "",
                label = "Distance",
                modifier = Modifier.weight(1f),
            )

            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            MetricStatTile(
                icon = Icons.Outlined.LocalFireDepartment,
                iconTint = CaloriesColor,
                value = calStr,
                unit = "cal",
                label = "Calories",
                modifier = Modifier.weight(1f),
            )

            Box(
                Modifier
                    .width(1.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            MetricStatTile(
                icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                iconTint = StepsColor,
                value = stepsStr,
                unit = "",
                label = "Steps",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricStatTile(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
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
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Performance Panel ───────────────────────────────────────────────────

@Composable
private fun PerformancePanel(
    zoneMinutes: Int?,
    paceStr: String?,
    durationMinutes: Long,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Zone Minutes card with intensity ring
        if (zoneMinutes != null) {
            PerformanceCard(
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                iconBgColor = ZoneColor,
                value = "$zoneMinutes",
                unit = "min",
                label = "Zone Minutes",
                ring = {
                    IntensityMiniRing(
                        value = zoneMinutes,
                        max = durationMinutes.toInt().coerceAtLeast(1),
                        color = ZoneColor,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Avg Pace card
        if (paceStr != null) {
            PerformanceCard(
                icon = Icons.Outlined.Speed,
                iconBgColor = PaceColor,
                value = paceStr,
                unit = "/mi",
                label = "Avg Pace",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PerformanceCard(
    icon: ImageVector,
    iconBgColor: Color,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    ring: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = iconBgColor.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ring != null) {
                ring()
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBgColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconBgColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun IntensityMiniRing(value: Int, max: Int, color: Color) {
    val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animProgress by animateFloatAsState(
        targetValue = if (appeared) fraction else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "intensityRing",
    )

    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = 5.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * animProgress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

// ── HR Zones Card ────────────────────────────────────────────────────────────

@Composable
private fun HrZonesCard(samples: List<HrSample>, sessionStart: Instant, sessionEnd: Instant) {
    val avgBpm = samples.map { it.bpm }.average().toInt()
    val minBpm = samples.minOf { it.bpm }
    val maxBpm = samples.maxOf { it.bpm }

    val zt = remember { com.pulse.domain.usecase.ZoneMinuteCalculator.thresholds(USER_RESTING_HR, USER_AGE) }

    val totalSamples = samples.size
    val z5Count = samples.count { it.bpm >= zt.z5 }
    val z4Count = samples.count { it.bpm in zt.z4 until zt.z5 }
    val z3Count = samples.count { it.bpm in zt.z3 until zt.z4 }
    val z2Count = samples.count { it.bpm in zt.z2 until zt.z3 }
    val belowCount = samples.count { it.bpm < zt.z2 }

    val durationMs = if (samples.size >= 2) samples.last().timestampMs - samples.first().timestampMs else 0L
    val durationMin = (durationMs / 60_000.0).coerceAtLeast(1.0)

    fun samplesTo(count: Int): Int = (count.toDouble() / totalSamples * durationMin).toInt()
    fun samplesPct(count: Int): Int = if (totalSamples > 0) (count * 100 / totalSamples) else 0

    data class ZoneInfo(val name: String, val color: Color, val minutes: Int, val pct: Int)

    val zones = listOf(
        ZoneInfo("Peak", Zone5Peak, samplesTo(z5Count), samplesPct(z5Count)),
        ZoneInfo("Vigorous", Zone4Vigorous, samplesTo(z4Count), samplesPct(z4Count)),
        ZoneInfo("Moderate", Zone3Moderate, samplesTo(z3Count), samplesPct(z3Count)),
        ZoneInfo("Light", Zone2Light, samplesTo(z2Count), samplesPct(z2Count)),
        ZoneInfo("Below Zones", Zone1VeryLight, samplesTo(belowCount), samplesPct(belowCount)),
    )

    val zone = TimeZone.currentSystemDefault()
    val startDt = sessionStart.toLocalDateTime(zone)
    val endDt = sessionEnd.toLocalDateTime(zone)
    fun fmtTime(h: Int, m: Int) = "%d:%02d %s".format(
        if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM",
    )
    val startTimeLabel = fmtTime(startDt.hour, startDt.minute)
    val endTimeLabel = fmtTime(endDt.hour, endDt.minute)
    val totalDurMin = (durationMs / 60_000).toInt()

    SectionCard(title = "Heart rate zones") {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$avgBpm", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(6.dp))
                Text("bpm (avg)", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Premium HR chart
            PremiumHrChart(
                samples = samples,
                minBpm = minBpm,
                maxBpm = maxBpm,
                z5Thresh = zt.z5,
                z4Thresh = zt.z4,
                z3Thresh = zt.z3,
                z2Thresh = zt.z2,
                startTimeLabel = startTimeLabel,
                endTimeLabel = endTimeLabel,
                totalDurMin = totalDurMin,
            )

            Spacer(Modifier.height(16.dp))

            // Legend
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ZoneLegendDot(Zone1VeryLight, "Below")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone2Light, "Light")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone3Moderate, "Moderate")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone4Vigorous, "Vigorous")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone5Peak, "Peak")
            }

            Spacer(Modifier.height(24.dp))

            Text("Time in each zone",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))

            zones.forEach { z ->
                ZoneBreakdownRow(z.name, z.color, z.pct, z.minutes)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun PremiumHrChart(
    samples: List<HrSample>,
    minBpm: Int,
    maxBpm: Int,
    z5Thresh: Int,
    z4Thresh: Int,
    z3Thresh: Int,
    z2Thresh: Int,
    startTimeLabel: String,
    endTimeLabel: String,
    totalDurMin: Int,
) {
    if (samples.isEmpty()) return

    // Downsample for performance (max ~300 points)
    val drawSamples = remember(samples) {
        if (samples.size <= 300) samples
        else {
            val step = samples.size / 300
            samples.filterIndexed { i, _ -> i % step == 0 }
        }
    }

    // Model producer: feed BPM values as Y, use indices as X
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(drawSamples) {
        modelProducer.runTransaction {
            lineSeries {
                series(drawSamples.map { it.bpm.toDouble() })
            }
        }
    }

    // Chart Y range: fixed to show padding around min/max BPM
    val chartMinY = (minBpm - 8).coerceAtLeast(40).toDouble()
    val chartMaxY = (maxBpm + 12).coerceAtMost(220).toDouble()
    val rangeProvider = remember(chartMinY, chartMaxY) {
        CartesianLayerRangeProvider.fixed(minY = chartMinY, maxY = chartMaxY)
    }

    // Compute fractional positions for 5-zone boundaries within the chart's Y range
    // Gradient goes top-to-bottom = high BPM to low BPM, so fractions are inverted
    val bpmRange = (chartMaxY - chartMinY).toFloat().coerceAtLeast(1f)
    fun bpmFrac(bpm: Int) = (1f - (bpm - chartMinY.toFloat()) / bpmRange).coerceIn(0f, 1f)
    val z5Frac = bpmFrac(z5Thresh)
    val z4Frac = bpmFrac(z4Thresh)
    val z3Frac = bpmFrac(z3Thresh)
    val z2Frac = bpmFrac(z2Thresh)

    // Zone-colored gradient area fill aligned to actual BPM thresholds
    val areaFill = LineCartesianLayer.AreaFill.single(
        fill(
            ShaderProvider.verticalGradient(
                arrayOf(
                    Zone5Peak.copy(alpha = 0.40f), Zone5Peak.copy(alpha = 0.40f),
                    Zone4Vigorous.copy(alpha = 0.32f), Zone4Vigorous.copy(alpha = 0.32f),
                    Zone3Moderate.copy(alpha = 0.25f), Zone3Moderate.copy(alpha = 0.25f),
                    Zone2Light.copy(alpha = 0.18f), Zone2Light.copy(alpha = 0.18f),
                    Zone1VeryLight.copy(alpha = 0.12f), Color.Transparent,
                ),
                floatArrayOf(0f, z5Frac, z5Frac, z4Frac, z4Frac, z3Frac, z3Frac, z2Frac, z2Frac, 1f),
            ),
        ),
    )
    // Zone-colored gradient for the line itself aligned to BPM thresholds
    val lineFill = LineCartesianLayer.LineFill.single(
        fill(
            ShaderProvider.verticalGradient(
                arrayOf(
                    Zone5Peak, Zone5Peak,
                    Zone4Vigorous, Zone4Vigorous,
                    Zone3Moderate, Zone3Moderate,
                    Zone2Light, Zone2Light,
                    Zone1VeryLight, Zone1VeryLight,
                ),
                floatArrayOf(0f, z5Frac, z5Frac, z4Frac, z4Frac, z3Frac, z3Frac, z2Frac, z2Frac, 1f),
            ),
        ),
    )
    val line = LineCartesianLayer.rememberLine(
        fill = lineFill,
        areaFill = areaFill,
    )
    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(line),
        rangeProvider = rangeProvider,
    )

    // Bottom axis: show start/end time labels only
    val sampleCount = drawSamples.size
    val bottomAxisValueFormatter = CartesianValueFormatter { _, value, _ ->
        val idx = value.toInt()
        when {
            idx <= 0 -> startTimeLabel
            idx >= sampleCount - 1 -> endTimeLabel
            else -> " " // non-empty placeholder (Vico 2.0.2 rejects empty strings)
        }
    }
    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = bottomAxisValueFormatter,
        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { sampleCount.coerceAtLeast(2) - 1 }),
    )

    // End axis (right): show BPM values
    val endAxisValueFormatter = CartesianValueFormatter { _, value, _ ->
        "${value.toInt()}"
    }
    val endAxis = VerticalAxis.rememberEnd(
        valueFormatter = endAxisValueFormatter,
        itemPlacer = VerticalAxis.ItemPlacer.count({ 5 }),
    )

    // Threshold decoration lines
    val thresholdDecorations = remember(z5Thresh, z4Thresh, z3Thresh, z2Thresh) {
        listOf(
            z5Thresh to Zone5Peak,
            z4Thresh to Zone4Vigorous,
            z3Thresh to Zone3Moderate,
            z2Thresh to Zone2Light,
        ).map { (thresh, color) ->
            HorizontalLine(
                y = { thresh.toDouble() },
                line = LineComponent(
                    fill = Fill(color.copy(alpha = 0.7f).toArgb()),
                    thicknessDp = 1f,
                ),
            )
        }
    }

    // Build the chart
    val chart = rememberCartesianChart(
        lineLayer,
        endAxis = endAxis,
        bottomAxis = bottomAxis,
        decorations = thresholdDecorations,
    )

    Box {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        // Duration badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        ) {
            Text(
                "${totalDurMin}m",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ZoneBreakdownRow(name: String, color: Color, pct: Int, minutes: Int) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$name \u00b7 $pct%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text("$minutes min",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ── Section Card wrapper ─────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Lap rows ─────────────────────────────────────────────────────────────────

@Composable
private fun LapHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Lap", Modifier.weight(0.15f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pace", Modifier.weight(0.30f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Distance", Modifier.weight(0.25f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(0.30f))
    }
}

@Composable
private fun LapRow(lap: ExerciseLap) {
    val distMi = lap.distanceMeters / METERS_PER_MILE
    val paceStr = lap.paceSecondsPerMile?.let { "%d'%02d\" /mi".format(it / 60, it % 60) } ?: "--"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${lap.lapNumber}", Modifier.weight(0.15f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(paceStr, Modifier.weight(0.30f), style = MaterialTheme.typography.bodyMedium)
        Text("%.2f mi".format(distMi), Modifier.weight(0.25f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(0.30f)) {
            LinearProgressIndicator(
                progress = { (distMi / 1.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun EditExerciseDialog(
    calories: String,
    distance: String,
    steps: String,
    onCaloriesChange: (String) -> Unit,
    onDistanceChange: (String) -> Unit,
    onStepsChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = calories,
                    onValueChange = onCaloriesChange,
                    label = { Text("Calories (cal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = distance,
                    onValueChange = onDistanceChange,
                    label = { Text("Distance (mi)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = onStepsChange,
                    label = { Text("Steps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
