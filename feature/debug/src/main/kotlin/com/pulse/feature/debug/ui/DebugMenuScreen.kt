package com.pulse.feature.debug.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.pulse.feature.debug.viewmodel.DebugMenuViewModel

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
                    DebugMenuEffect.NavigateBack -> onDismiss()
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
        ActionItem("Export backup as JSON", "Full DB snapshot, shareable") {
            onIntent(DebugMenuIntent.ExportBackup)
        }

        SectionHeader("Feature flags")
        FlagRow(
            "WoW/MoM on dashboard",
            state.featureFlags.wowMomOnDashboard,
        ) { onIntent(DebugMenuIntent.ToggleFlag(FeatureFlagKey.WowMomOnDashboard, it)) }

        SectionHeader("Data Coverage")
        if (state.dataRangeStart != null && state.dataRangeEnd != null) {
            Text(
                text = "${state.dataRangeStart} \u2013 ${state.dataRangeEnd}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = "${state.totalStepDays} step days \u00b7 ${state.totalExerciseSessions} exercises \u00b7 ${state.totalSleepSessions} sleep",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Text(
                text = "No data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

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
