package com.pulse.feature.debug.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.pulse.data.datastore.FeatureFlagKey
import com.pulse.feature.debug.state.ConfirmAction
import com.pulse.feature.debug.state.DebugMenuEffect
import com.pulse.feature.debug.state.DebugMenuIntent
import com.pulse.feature.debug.state.DebugMenuState
import com.pulse.feature.debug.state.SyncWorkerState
import com.pulse.feature.debug.viewmodel.DebugMenuViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuRoute(
    onDismiss: () -> Unit,
    viewModel: DebugMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    LaunchedEffect(viewModel) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { effect ->
                when (effect) {
                    is DebugMenuEffect.ShareCsv -> {
                        val file = java.io.File(effect.filePath)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file,
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Export data"))
                    }
                    is DebugMenuEffect.Snackbar -> Unit
                    DebugMenuEffect.OpenHealthConnectApp -> {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage("com.google.android.apps.healthdata")
                            ?: Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri())
                        context.startActivity(intent)
                    }
                    DebugMenuEffect.NavigateBack -> onDismiss()
                    is DebugMenuEffect.NavigateToRecordDump -> Unit
                    DebugMenuEffect.LaunchGoogleSignIn -> {
                        val activity = context as? android.app.Activity ?: return@collect
                        coroutineScope.launch {
                            val result = viewModel.authManager.signIn(activity)
                            viewModel.onSignInResult(
                                success = result.isSuccess,
                                message = result.exceptionOrNull()?.message,
                            )
                        }
                    }
                    DebugMenuEffect.LaunchFitbitSignIn -> {
                        val intent = viewModel.fitbitAuthManager.buildAuthIntent()
                        context.startActivity(intent)
                    }
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DebugMenuSheetBody(state = state, onIntent = viewModel::onIntent)
    }
}

@Composable
private fun DebugMenuSheetBody(
    state: DebugMenuState,
    onIntent: (DebugMenuIntent) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = "Debug Menu",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = "Pending queue: ${state.pendingQueueSize}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        SectionHeader("Data")
        ActionItem("Seed fake data (90 days)", "Deterministic, seed=42") {
            onIntent(DebugMenuIntent.SeedFakeData)
        }
        ActionItem("Seed realistic week", "Matches Fitbit screenshots") {
            onIntent(DebugMenuIntent.SeedRealisticWeek)
        }
        ActionItem("Clear local Room cache", "Keeps HC change token") {
            onIntent(DebugMenuIntent.RequestClearCache)
        }
        ActionItem("Hard reset", "Cache + tokens + flags") {
            onIntent(DebugMenuIntent.RequestHardReset)
        }
        ActionItem("Export all data as CSV", "Opens share sheet") {
            onIntent(DebugMenuIntent.ExportCsv)
        }

        SectionHeader("Sync")
        SyncActionItem(state.syncWorkerState) {
            onIntent(DebugMenuIntent.ForceSyncNow)
        }
        ActionItem("Simulate network failure", "60s fault injection") {
            onIntent(DebugMenuIntent.SimulateNetworkFailure)
        }

        SectionHeader("Health Connect")
        ActionItem("Open HC app", "com.google.android.apps.healthdata") {
            onIntent(DebugMenuIntent.OpenHealthConnect)
        }
        ActionItem("Dump raw records (today)", "View each HC record") {
            onIntent(DebugMenuIntent.DumpRecords)
        }
        ActionItem("Reset change token", "Next sync reads full day") {
            onIntent(DebugMenuIntent.ResetChangeToken)
        }

        SectionHeader("Google Health API")
        if (state.googleHealthSignedIn) {
            InfoLine("Status", "Signed in")
            ActionItem("Sign out", "Clears OAuth tokens") {
                onIntent(DebugMenuIntent.GoogleHealthSignOut)
            }
        } else {
            InfoLine("Status", "Not signed in (session-only)")
            ActionItem("Sign in with Google", "Token not persisted across restarts") {
                onIntent(DebugMenuIntent.GoogleHealthSignIn)
            }
        }

        SectionHeader("Fitbit API")
        if (state.fitbitSignedIn) {
            InfoLine("Status", "Connected")
            state.fitbitSyncCursor?.let { InfoLine("Last synced to", it) }
            state.fitbitSyncProgress?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            ActionItem("Force Fitbit sync", "Fetches all history from Fitbit cloud") {
                onIntent(DebugMenuIntent.ForceFitbitSync)
            }
            ActionItem("Disconnect Fitbit", "Clears OAuth tokens") {
                onIntent(DebugMenuIntent.FitbitSignOut)
            }
        } else {
            InfoLine("Status", "Not connected")
            ActionItem("Connect Fitbit", "OAuth sign-in for unlimited history") {
                onIntent(DebugMenuIntent.FitbitSignIn)
            }
        }

        SectionHeader("Feature flags")
        FlagRow(
            "Shared-element transitions",
            state.featureFlags.sharedElementTransitions,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.SharedElementTransitions, it)) }
        FlagRow(
            "Vico gradient bars",
            state.featureFlags.vicoGradientBars,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.VicoGradientBars, it)) }
        FlagRow(
            "WoW/MoM on dashboard",
            state.featureFlags.wowMomOnDashboard,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.WowMomOnDashboard, it)) }
        FlagRow(
            "Google Health reconciliation",
            state.featureFlags.googleHealthReconcile,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.GoogleHealthReconcile, it)) }
        FlagRow(
            "Force dark mode",
            state.featureFlags.forceDarkMode,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.ForceDarkMode, it)) }
        FlagRow(
            "Use dynamic color",
            state.featureFlags.useDynamicColor,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.UseDynamicColor, it)) }
        FlagRow(
            "Drive backup (You screen)",
            state.featureFlags.driveBackupEnabled,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.DriveBackupEnabled, it)) }

        SectionHeader("Data Coverage")
        val rangeText = if (state.dataRangeStart != null && state.dataRangeEnd != null) {
            "${state.dataRangeStart} to ${state.dataRangeEnd}"
        } else {
            "No data"
        }
        InfoLine("Step data range", rangeText)
        InfoLine("Step days", "${state.totalStepDays}")
        InfoLine("Exercise sessions", "${state.totalExerciseSessions}")
        InfoLine("Sleep sessions", "${state.totalSleepSessions}")
        state.metricCounts.entries
            .sortedByDescending { it.value }
            .filter { it.key != "Steps" }
            .forEach { (metric, count) ->
                InfoLine(metric, "$count days")
            }

        SectionHeader("Background Sync")
        val backfillStatus = when {
            state.backfillComplete -> "Complete"
            state.backfillCursor != null -> "In progress (cursor: ${state.backfillCursor})"
            else -> "Not started"
        }
        InfoLine("History backfill", backfillStatus)
        val syncWindowText = if (state.syncWindowStart != null && state.syncWindowEnd != null) {
            "${state.syncWindowStart} to ${state.syncWindowEnd}"
        } else {
            "N/A"
        }
        InfoLine("Periodic sync window", syncWindowText)

        SectionHeader("Info")
        state.buildInfo?.let { info ->
            InfoLine("Version", info.appVersion)
            InfoLine("Git SHA", info.gitSha)
            InfoLine("Device", info.deviceId)
            InfoLine("HC SDK", info.healthConnectSdkVersion)
        }

        state.lastAction?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    state.confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { onIntent(DebugMenuIntent.CancelConfirm) },
            title = {
                Text(
                    if (action == ConfirmAction.HardReset) "Hard reset?" else "Clear cache?"
                )
            },
            text = {
                Text(
                    if (action == ConfirmAction.HardReset)
                        "Wipes Room, sync tokens, and resets feature flags. You will re-sync from scratch."
                    else
                        "Wipes Room cache. Sync tokens preserved so only new data pulls."
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(DebugMenuIntent.ConfirmDestructive(action)) }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(DebugMenuIntent.CancelConfirm) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    HorizontalDivider()
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionItem(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClick) { Text("Run") }
    }
}

@Composable
private fun FlagRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SyncActionItem(syncState: SyncWorkerState, onClick: () -> Unit) {
    val isBusy = syncState == SyncWorkerState.Enqueued || syncState == SyncWorkerState.Running
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Force sync now", style = MaterialTheme.typography.bodyLarge)
            val subtitle = when (syncState) {
                SyncWorkerState.Idle -> "Fetches 1 year of HC data"
                SyncWorkerState.Enqueued -> "Waiting to start..."
                SyncWorkerState.Running -> "Syncing from Health Connect..."
                SyncWorkerState.Succeeded -> "Sync completed"
                SyncWorkerState.Failed -> "Sync failed"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (syncState) {
                        SyncWorkerState.Succeeded -> MaterialTheme.colorScheme.primary
                        SyncWorkerState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        TextButton(onClick = onClick, enabled = !isBusy) { Text("Run") }
    }
}
