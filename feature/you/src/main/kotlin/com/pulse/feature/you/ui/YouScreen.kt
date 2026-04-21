package com.pulse.feature.you.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Watch
import kotlinx.coroutines.launch
import com.pulse.data.cloud.DriveAuthOutcome
import com.pulse.data.cloud.GoogleHealthAuthOutcome
import com.pulse.core.designsystem.theme.Coral500
import com.pulse.core.designsystem.theme.Forest300
import com.pulse.core.designsystem.theme.Forest500
import com.pulse.core.designsystem.theme.Forest900
import com.pulse.core.designsystem.theme.Amber500
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Launcher for Drive consent UI
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val activity = context.findActivity() ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val tokenResult = viewModel.driveAuthManager.handleConsentResult(activity, result.data)
            viewModel.onSignInResult(
                success = tokenResult.isSuccess,
                message = tokenResult.exceptionOrNull()?.message,
            )
        }
    }

    // Launcher for Google Health consent UI
    val healthConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val activity = context.findActivity() ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val tokenResult = viewModel.googleHealthAuthManager.handleConsentResult(activity, result.data)
            viewModel.onGoogleSignInResult(
                success = tokenResult.isSuccess,
                message = tokenResult.exceptionOrNull()?.message,
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    YouEffect.NavigateBack -> onBack()
                    YouEffect.LaunchFitbitSignIn -> {
                        val intent = viewModel.fitbitAuthManager.buildAuthIntent()
                        context.startActivity(intent)
                    }
                    YouEffect.LaunchDriveSignIn -> {
                        val activity = context.findActivity() ?: return@collect
                        coroutineScope.launch {
                            try {
                                when (val outcome = viewModel.driveAuthManager.requestAuth(activity)) {
                                    is DriveAuthOutcome.Authorized -> {
                                        viewModel.onSignInResult(success = true, message = null)
                                    }
                                    is DriveAuthOutcome.ConsentRequired -> {
                                        val intentSenderRequest = IntentSenderRequest.Builder(outcome.pendingIntent).build()
                                        driveConsentLauncher.launch(intentSenderRequest)
                                    }
                                }
                            } catch (e: Exception) {
                                viewModel.onSignInResult(success = false, message = e.message)
                            }
                        }
                    }
                    YouEffect.LaunchGoogleHealthAuth -> {
                        val activity = context.findActivity() ?: return@collect
                        coroutineScope.launch {
                            try {
                                when (val outcome = viewModel.googleHealthAuthManager.requestAuth(activity)) {
                                    is GoogleHealthAuthOutcome.Authorized -> {
                                        viewModel.onGoogleSignInResult(success = true, message = null)
                                    }
                                    is GoogleHealthAuthOutcome.ConsentRequired -> {
                                        val intentSenderRequest = IntentSenderRequest.Builder(outcome.pendingIntent).build()
                                        healthConsentLauncher.launch(intentSenderRequest)
                                    }
                                }
                            } catch (e: Exception) {
                                viewModel.onGoogleSignInResult(success = false, message = e.message)
                            }
                        }
                    }
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
            item { DataProvidersCard(state, onIntent) }
            item { AppearanceCard(state, onIntent) }
            item { DisplayPrefsCard(state, onIntent) }
            item { CloudBackupCard(state, onIntent) }
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
                    color = Amber500,
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
    MetricType.ZoneMinutes -> Amber500
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

// ── Data Providers Card ──────────────────────────────────────────────────────

@Composable
private fun DataProvidersCard(state: YouState, onIntent: (YouIntent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Watch,
                    contentDescription = null,
                    tint = Forest500,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Data Providers",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Connect data sources for richer health history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Fitbit provider
            ProviderRow(
                name = "Fitbit",
                description = if (state.fitbitConnected) {
                    state.fitbitSyncCursor?.let { "Synced to $it" } ?: "Connected"
                } else {
                    "Unlimited historical data"
                },
                connected = state.fitbitConnected,
                onConnect = { onIntent(YouIntent.FitbitSignIn) },
                onDisconnect = { onIntent(YouIntent.FitbitSignOut) },
            )

            Spacer(Modifier.height(4.dp))

            // Health Connect is always available
            ProviderRow(
                name = "Health Connect",
                description = "On-device, last ~30 days",
                connected = true,
                alwaysConnected = true,
                onConnect = {},
                onDisconnect = {},
            )

            Spacer(Modifier.height(4.dp))

            // Google Health — cloud reconciled data
            ProviderRow(
                name = "Google Health",
                description = if (state.googleHealthSignedIn) "Connected" else "Cloud health archive",
                connected = state.googleHealthSignedIn,
                onConnect = { onIntent(YouIntent.GoogleHealthSignIn) },
                onDisconnect = { onIntent(YouIntent.GoogleHealthSignOut) },
            )

            Spacer(Modifier.height(16.dp))

            // Sync Now button
            Button(
                onClick = { onIntent(YouIntent.SyncNow) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest500),
                enabled = !state.syncing,
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("Sync Now", style = MaterialTheme.typography.labelMedium)
                }
            }

            state.syncMessage?.let { msg ->
                Spacer(Modifier.height(4.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    name: String,
    description: String,
    connected: Boolean,
    alwaysConnected: Boolean = false,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (connected) Forest500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (connected) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                    contentDescription = null,
                    tint = if (connected) Forest500 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!alwaysConnected) {
            if (connected) {
                TextButton(onClick = onDisconnect) {
                    Text(
                        "Disconnect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest500),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text("Connect", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Appearance Card ──────────────────────────────────────────────────────────

@Composable
private fun AppearanceCard(state: YouState, onIntent: (YouIntent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = Forest500,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(Modifier.height(16.dp))

            PrefToggleRow(
                label = "Dark mode",
                description = if (state.forceDarkMode) "Always dark" else "Follow system",
                checked = state.forceDarkMode,
                onCheckedChange = { onIntent(YouIntent.SetDarkMode(it)) },
            )
            PrefToggleRow(
                label = "Dynamic colors",
                description = if (state.useDynamicColor) "Material You wallpaper colors" else "Default theme",
                checked = state.useDynamicColor,
                onCheckedChange = { onIntent(YouIntent.SetDynamicColor(it)) },
            )
        }
    }
}

// ── Display Preferences Card ─────────────────────────────────────────────────

@Composable
private fun DisplayPrefsCard(state: YouState, onIntent: (YouIntent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = Forest500,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Dashboard Metrics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose how distance and calories are calculated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            PrefToggleRow(
                label = "Distance",
                description = if (state.activityOnlyDistance) "Activity miles only" else "Total miles (all movement)",
                checked = state.activityOnlyDistance,
                onCheckedChange = { onIntent(YouIntent.SetActivityOnlyDistance(it)) },
            )
            PrefToggleRow(
                label = "Calories",
                description = if (state.activityOnlyCalories) "Activity calories only" else "All active calories",
                checked = state.activityOnlyCalories,
                onCheckedChange = { onIntent(YouIntent.SetActivityOnlyCalories(it)) },
            )
        }
    }
}

@Composable
private fun PrefToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Forest500),
        )
    }
}

// ── Cloud Backup Card ────────────────────────────────────────────────────────

@Composable
private fun CloudBackupCard(state: YouState, onIntent: (YouIntent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.driveSignedIn) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = if (state.driveSignedIn) Forest500 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (state.driveSignedIn) "Your data is backed up to Google Drive"
                else "Sign in to back up your health data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            if (!state.driveSignedIn) {
                Button(
                    onClick = { onIntent(YouIntent.DriveSignIn) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest500),
                ) {
                    Text("Sign in with Google")
                }
            } else {
                // Last backup info
                if (state.lastBackupTime != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Last backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.lastBackupTime,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
                if (state.lastBackupSize != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            state.lastBackupSize,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onIntent(YouIntent.RestoreNow) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !state.backupInProgress && !state.restoreInProgress,
                    ) {
                        if (state.restoreInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Restore")
                    }

                    Button(
                        onClick = { onIntent(YouIntent.BackupNow) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest500),
                        enabled = !state.backupInProgress && !state.restoreInProgress,
                    ) {
                        if (state.backupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Back up")
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onIntent(YouIntent.DriveSignOut) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Status message
            if (state.backupMessage != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onIntent(YouIntent.DismissBackupMessage) }) {
                            Text("OK", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
