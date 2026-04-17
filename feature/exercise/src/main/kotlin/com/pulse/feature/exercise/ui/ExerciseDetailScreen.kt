package com.pulse.feature.exercise.ui

import android.util.Log
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
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        onRequestRouteConsent = { viewModel.onIntent(ExerciseDetailIntent.RequestRouteConsent) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailState,
    onBack: () -> Unit,
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
            ZoneLegendDot(Zone1VeryLight, "Z1")
            ZoneLegendDot(Zone2Light, "Z2")
            ZoneLegendDot(Zone3Moderate, "Z3")
            ZoneLegendDot(Zone4Vigorous, "Z4")
            ZoneLegendDot(Zone5Peak, "Z5")
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
    val distStr = distMi?.let { "%.2f mi".format(it) } ?: "--"
    val calStr = session.calories?.toInt()?.toString() ?: "--"
    val stepsStr = detail.steps?.let { "%,d".format(it) } ?: "--"
    val zoneStr = detail.zoneMinutes?.toString() ?: "--"
    val paceStr = detail.avgPaceSecondsPerMile?.let {
        "%d'%02d\"".format(it / 60, it % 60)
    } ?: "--"

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Duration", durationStr, Icons.Outlined.Timer, Modifier.weight(1f))
            StatCard("Distance", distStr, Icons.Outlined.Straighten, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Calories", "$calStr cal", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
            StatCard("Steps", stepsStr, Icons.Outlined.DirectionsRun, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Zone min", zoneStr, Icons.Outlined.FavoriteBorder, Modifier.weight(1f))
            StatCard("Avg pace", "$paceStr /mi", Icons.Outlined.Speed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                ZoneLegendDot(Zone1VeryLight, "Z1")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone2Light, "Z2")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone3Moderate, "Z3")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone4Vigorous, "Z4")
                Spacer(Modifier.width(10.dp))
                ZoneLegendDot(Zone5Peak, "Z5")
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
