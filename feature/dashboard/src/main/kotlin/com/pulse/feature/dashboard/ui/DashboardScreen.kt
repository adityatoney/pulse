package com.pulse.feature.dashboard.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.pulse.core.designsystem.theme.LocalRingPalette
import com.pulse.core.ui.badges.DeltaDirection
import com.pulse.core.ui.badges.WoWMoMBadge
import com.pulse.core.ui.chrome.BatteryChip
import com.pulse.core.ui.chrome.DateScrollerRow
import com.pulse.core.ui.ring.ActivityRingHero
import com.pulse.core.ui.ring.SecondaryRingTile
import com.pulse.core.ui.sync.SyncChipState
import com.pulse.core.ui.sync.SyncStatusChip
import com.pulse.core.ui.util.formatted
import com.pulse.core.ui.util.formattedMiles
import com.pulse.core.ui.util.relativeTo
import com.pulse.core.ui.util.timeOfDay
import com.pulse.domain.model.ExerciseSession
import com.pulse.domain.model.MetricType
import com.pulse.domain.model.SyncPhase
import com.pulse.domain.model.TrendDirection
import com.pulse.feature.dashboard.state.DashboardEffect
import com.pulse.feature.dashboard.state.DashboardIntent
import com.pulse.feature.dashboard.state.DashboardState
import com.pulse.feature.dashboard.viewmodel.DashboardViewModel
import kotlinx.datetime.Clock as KtxClock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    onNavigateToMetric: (MetricType) -> Unit,
    onNavigateToExerciseLog: () -> Unit,
    onNavigateToExerciseDetail: (String) -> Unit = {},
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    is DashboardEffect.NavigateToMetricDetail -> onNavigateToMetric(effect.metric)
                    DashboardEffect.NavigateToExerciseLog -> onNavigateToExerciseLog()
                    is DashboardEffect.NavigateToExerciseDetail -> onNavigateToExerciseDetail(effect.sessionId)
                    DashboardEffect.NavigateToChat -> onNavigateToChat()
                    DashboardEffect.NavigateToProfile -> onNavigateToProfile()
                    DashboardEffect.NavigateToDebugMenu -> onNavigateToDebug()
                    is DashboardEffect.ShowSnackbar -> snackbarState.showSnackbar(effect.message)
                    DashboardEffect.RequestHealthConnectPermissions,
                    DashboardEffect.LaunchPlayStoreForHealthConnect -> {
                        // handled by :app permission host
                    }
                }
            }
    }

    DashboardScreen(state = state, snackbarState = snackbarState, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    snackbarState: SnackbarHostState,
    onIntent: (DashboardIntent) -> Unit,
) {
    val rings = LocalRingPalette.current
    val context = LocalContext.current
    // Tap-count gesture for hidden debug menu. `remember` is fine (rotation-only reset is desired).
    var batteryTapCount by remember { mutableStateOf(0) }
    var batteryTapTs by remember { mutableStateOf(0L) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val sel = state.selectedDate
        android.app.DatePickerDialog(
            context,
            { _, y, m, d -> onIntent(DashboardIntent.ChangeDate(LocalDate(y, m + 1, d))); showDatePicker = false },
            sel.year, sel.monthNumber - 1, sel.dayOfMonth,
        ).apply {
            setOnDismissListener { showDatePicker = false }
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Pulse", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    val now = KtxClock.System.now().toEpochMilliseconds()
                    val pct = state.device?.batteryPct ?: 100
                    BatteryChip(
                        pct = pct,
                        onTap = {
                            val withinWindow = now - batteryTapTs < 2_000L
                            val newCount = if (withinWindow) batteryTapCount + 1 else 1
                            batteryTapCount = newCount
                            batteryTapTs = now
                            if (newCount >= 5) {
                                batteryTapCount = 0
                                batteryTapTs = 0L
                                onIntent(DashboardIntent.OpenDebugMenu)
                            }
                        },
                    )
                },
                actions = {
                    state.sync?.let { sync ->
                        SyncStatusChip(
                            state = sync.state.toChip(),
                            label = syncChipLabel(sync),
                            onClick = { onIntent(DashboardIntent.RetrySync) },
                            onLongPress = { onIntent(DashboardIntent.OpenDebugMenu) },
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onIntent(DashboardIntent.OpenChat) }) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Coach")
                    }
                    IconButton(onClick = { onIntent(DashboardIntent.OpenProfile) }) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .semantics { testTag = "avatar" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = "You", modifier = Modifier.size(32.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onIntent(DashboardIntent.OpenChat) },
                    icon = { Icon(Icons.Outlined.SelfImprovement, contentDescription = null) },
                    label = { Text("Coach") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onIntent(DashboardIntent.OpenProfile) },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("You") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(DashboardIntent.OpenExerciseLog) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Log")
            }
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(DashboardIntent.PullToRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DateScrollerRow(
                    selected = state.selectedDate,
                    today = state.today,
                    onPrev = { onIntent(DashboardIntent.ChangeDate(state.selectedDate.minus(DatePeriod(days = 1)))) },
                    onNext = { onIntent(DashboardIntent.ChangeDate(state.selectedDate.plus(DatePeriod(days = 1)))) },
                    onPickDate = { showDatePicker = true },
                )
            }

            item {
                val steps = state.metrics?.steps
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ActivityRingHero(
                        progress = steps?.progress ?: 0f,
                        brush = rings.steps,
                        trackColor = rings.track,
                        centerIcon = Icons.Outlined.DirectionsRun,
                        centerValue = (steps?.current ?: 0).formatted(),
                        centerLabel = "Steps",
                        onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.Steps)) },
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val zm = state.metrics?.zoneMinutes
                    SecondaryRingTile(
                        progress = zm?.progress ?: 0f,
                        brush = rings.zone,
                        trackColor = rings.track,
                        icon = Icons.Outlined.TrendingUp,
                        value = (zm?.current ?: 0).toString(),
                        label = "Zone Min",
                        onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.ZoneMinutes)) },
                    )

                    val dist = state.metrics?.distanceMiles
                    SecondaryRingTile(
                        progress = dist?.progress ?: 0f,
                        brush = rings.distance,
                        trackColor = rings.track,
                        icon = Icons.Outlined.Place,
                        value = (dist?.current ?: 0.0).formattedMiles(),
                        label = "mi",
                        onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.Distance)) },
                    )

                    val cal = state.metrics?.calories
                    SecondaryRingTile(
                        progress = cal?.progress ?: 0f,
                        brush = rings.calories,
                        trackColor = rings.track,
                        icon = Icons.Outlined.LocalFireDepartment,
                        value = (cal?.current ?: 0).formatted(),
                        label = "cal",
                        onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.ActiveCalories)) },
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WoWMoMBadge(
                        label = "WoW",
                        deltaPct = state.wow?.value,
                        direction = state.wow?.direction?.toDirection(),
                    )
                    WoWMoMBadge(
                        label = "MoM",
                        deltaPct = state.mom?.value,
                        direction = state.mom?.direction?.toDirection(),
                    )
                }
            }

            item { RecoverySection(state) }

            item { BodyMetricsSection(state, onIntent) }

            // Activities section
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Activities",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.recentExercises.isNotEmpty()) {
                            androidx.compose.material3.TextButton(
                                onClick = { onIntent(DashboardIntent.OpenExerciseLog) },
                            ) {
                                Text("View all")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.recentExercises.isEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("No activities today", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(state.recentExercises, key = { it.id }) { session ->
                ExerciseSessionCard(
                    session = session,
                    onClick = { onIntent(DashboardIntent.OpenExerciseDetail(session.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
        }
    }
}

private fun SyncPhase.toChip(): SyncChipState = when (this) {
    SyncPhase.Idle -> SyncChipState.Idle
    SyncPhase.Syncing -> SyncChipState.Syncing
    SyncPhase.Stale -> SyncChipState.Stale
    SyncPhase.Failed -> SyncChipState.Failed
}

private fun TrendDirection.toDirection() = when (this) {
    TrendDirection.Up -> DeltaDirection.Up
    TrendDirection.Down -> DeltaDirection.Down
    TrendDirection.Flat -> DeltaDirection.Flat
}

private fun syncChipLabel(sync: com.pulse.domain.model.SyncStatus): String = when (sync.state) {
    SyncPhase.Syncing -> "Syncing…"
    SyncPhase.Stale -> "Last synced ${sync.lastSyncedAt.relativeTo(KtxClock.System.now())}"
    SyncPhase.Failed -> "Retry sync"
    SyncPhase.Idle -> if (sync.lastSyncedAt == null) "Not synced" else "Synced"
}

@Composable
private fun ExerciseSessionCard(session: ExerciseSession, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            session.start.timeOfDay(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                session.type,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val distMi = session.distanceMeters?.let { "%.2f mi".format(it / 1609.34) } ?: ""
            val dur = "${session.durationMinutes} min"
            Text(
                listOfNotNull(distMi.takeIf { it.isNotEmpty() }, dur).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    }
}

@Composable
private fun RecoverySection(state: DashboardState) {
    val sleep = state.recovery?.sleep
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Recovery",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        sleep?.let { "${it.totalMinutes / 60}h ${it.totalMinutes % 60}m" } ?: "No data",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Sleep duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sleep != null && sleep.deepMinutes != null) {
                Spacer(Modifier.height(16.dp))
                SleepStageBar(sleep)
                Spacer(Modifier.height(8.dp))
                SleepStageLegend(sleep)
            }
        }
    }
}

@Composable
private fun SleepStageBar(sleep: com.pulse.domain.model.SleepSummary) {
    val deep = sleep.deepMinutes ?: 0L
    val rem = sleep.remMinutes ?: 0L
    val light = sleep.lightMinutes ?: 0L
    val awake = sleep.awakeMinutes ?: 0L
    val total = (deep + rem + light + awake).coerceAtLeast(1L).toFloat()

    val deepColor = Color(0xFF3F51B5)
    val remColor = Color(0xFF7C4DFF)
    val lightColor = Color(0xFF4DD0E1)
    val awakeColor = Color(0xFFFF8A65)

    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        if (deep > 0) Box(Modifier.weight(deep / total).fillMaxSize().background(deepColor))
        if (rem > 0) Box(Modifier.weight(rem / total).fillMaxSize().background(remColor))
        if (light > 0) Box(Modifier.weight(light / total).fillMaxSize().background(lightColor))
        if (awake > 0) Box(Modifier.weight(awake / total).fillMaxSize().background(awakeColor))
    }
}

@Composable
private fun SleepStageLegend(sleep: com.pulse.domain.model.SleepSummary) {
    val stages = listOfNotNull(
        sleep.deepMinutes?.let { "Deep" to it },
        sleep.remMinutes?.let { "REM" to it },
        sleep.lightMinutes?.let { "Light" to it },
        sleep.awakeMinutes?.let { "Awake" to it },
    )
    val colors = listOf(Color(0xFF3F51B5), Color(0xFF7C4DFF), Color(0xFF4DD0E1), Color(0xFFFF8A65))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stages.forEachIndexed { i, (label, mins) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors[i]),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "$label ${mins / 60}h${mins % 60}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BodyMetricsSection(state: DashboardState, onIntent: (DashboardIntent) -> Unit) {
    val hasAny = state.restingHr != null || state.weight != null || state.spo2 != null || state.hrv != null
    if (!hasAny) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Body",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.restingHr?.let { hr ->
                BodyMetricCard(
                    icon = Icons.Outlined.Favorite,
                    value = "${hr.toInt()}",
                    unit = "bpm",
                    label = "Resting HR",
                    onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.RestingHeartRate)) },
                    modifier = Modifier.weight(1f),
                )
            }
            state.spo2?.let { spo2 ->
                BodyMetricCard(
                    icon = Icons.Outlined.WaterDrop,
                    value = "${spo2.toInt()}",
                    unit = "%",
                    label = "SpO2",
                    onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.SpO2)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.weight?.let { w ->
                BodyMetricCard(
                    icon = Icons.Outlined.MonitorWeight,
                    value = "%.1f".format(w),
                    unit = "kg",
                    label = "Weight",
                    onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.Weight)) },
                    modifier = Modifier.weight(1f),
                )
            }
            state.hrv?.let { hrv ->
                BodyMetricCard(
                    icon = Icons.Outlined.TrendingUp,
                    value = "${hrv.toInt()}",
                    unit = "ms",
                    label = "HRV",
                    onClick = { onIntent(DashboardIntent.SelectMetric(MetricType.HRV)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BodyMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    unit: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
