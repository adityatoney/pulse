package com.pulse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.pulse.core.designsystem.theme.FitbitTheme
import com.pulse.data.cloud.DriveAuthManager
import com.pulse.data.cloud.DriveAuthOutcome
import com.pulse.data.cloud.fitbit.FitbitAuthManager
import com.pulse.data.datastore.FeatureFlagRepository
import com.pulse.data.health.HealthConnectDataSource
import com.pulse.data.sync.EnhancedHealthSyncManager
import com.pulse.data.work.SyncScheduler
import com.pulse.feature.dashboard.navigation.Dashboard
import com.pulse.feature.dashboard.navigation.dashboardScreen
import com.pulse.feature.debug.navigation.DebugMenu
import com.pulse.feature.debug.navigation.debugMenuScreen
import com.pulse.feature.detail.navigation.MetricDetail
import com.pulse.feature.detail.navigation.metricDetailScreen
import com.pulse.feature.exercise.navigation.ExerciseDetail
import com.pulse.feature.exercise.navigation.ExerciseLog
import com.pulse.feature.exercise.navigation.exerciseDetailScreen
import com.pulse.feature.exercise.navigation.exerciseLogScreen
import com.pulse.feature.insights.navigation.HeatmapDetail
import com.pulse.feature.insights.navigation.Insights
import com.pulse.feature.insights.navigation.heatmapDetailScreen
import com.pulse.feature.insights.navigation.insightsScreen
import com.pulse.feature.you.navigation.YouProfile
import com.pulse.feature.you.navigation.youScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var featureFlags: FeatureFlagRepository
    @Inject lateinit var healthConnectDataSource: HealthConnectDataSource
    @Inject lateinit var driveAuthManager: DriveAuthManager
    @Inject lateinit var fitbitAuthManager: FitbitAuthManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var syncManager: EnhancedHealthSyncManager

    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        lifecycleScope.launch {
            val tokenResult = driveAuthManager.handleConsentResult(this@MainActivity, result.data)
            if (tokenResult.isSuccess) {
                Log.d("Pulse", "Drive: consent granted")
            } else {
                Log.d("Pulse", "Drive: consent failed: ${tokenResult.exceptionOrNull()?.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        attemptDriveSignIn()
        attemptFitbitRestore()

        // Handle Fitbit redirect if launched via deep link
        handleFitbitRedirect(intent)

        setContent {
            val flags by featureFlags.observe().collectAsState(
                initial = com.pulse.data.datastore.FeatureFlagSnapshot.Default
            )

            FitbitTheme(
                darkTheme = flags.forceDarkMode || isSystemInDarkTheme(),
                dynamicColor = flags.useDynamicColor,
            ) {
                Surface(modifier = Modifier) {
                    FitbitNavHost(healthConnectDataSource)
                }
            }
        }
    }

    /**
     * Attempt Drive sign-in on launch.
     * - Returning users: silent auth (no UI).
     * - First-time users: launches consent screen via ActivityResultLauncher.
     * On success, triggers an immediate sync.
     */
    private fun attemptDriveSignIn() {
        lifecycleScope.launch {
            try {
                when (val outcome = driveAuthManager.requestAuth(this@MainActivity)) {
                    is DriveAuthOutcome.Authorized -> {
                        Log.d("Pulse", "Drive: already authorized")
                    }
                    is DriveAuthOutcome.ConsentRequired -> {
                        Log.d("Pulse", "Drive: launching consent screen")
                        val request = IntentSenderRequest.Builder(outcome.pendingIntent).build()
                        driveConsentLauncher.launch(request)
                    }
                }
            } catch (e: Exception) {
                Log.d("Pulse", "Drive: sign-in failed: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // HC requires foreground context — sync here, not via WorkManager.
        // On cold starts, the system may not yet consider the UID in foreground,
        // so retry with backoff if we get a SecurityException.
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1_500)
            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                val result = runCatching { syncManager.syncRecent(days = 7) }
                if (result.isSuccess) break
                val cause = result.exceptionOrNull()
                if (cause?.message?.contains("foreground") == true && attempt < maxRetries) {
                    Log.d("Pulse", "HC foreground check failed (attempt $attempt/$maxRetries), retrying...")
                    kotlinx.coroutines.delay(attempt * 2_000L)
                } else {
                    Log.w("Pulse", "Foreground sync failed: ${cause?.message}")
                    break
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFitbitRedirect(intent)
    }

    /**
     * Handle the Fitbit OAuth redirect (pulse://fitbit/callback?code=...).
     */
    private fun handleFitbitRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "pulse" || data.host != "fitbit") return
        val code = data.getQueryParameter("code") ?: return

        Log.d("Pulse", "Fitbit: OAuth redirect received, exchanging code")
        lifecycleScope.launch {
            val result = fitbitAuthManager.handleRedirect(code)
            if (result.isSuccess) {
                Log.d("Pulse", "Fitbit: Auth complete, scheduling sync")
                Toast.makeText(this@MainActivity, "Fitbit connected! Syncing history...", Toast.LENGTH_LONG).show()
                syncScheduler.scheduleFitbitSync()
            } else {
                Log.w("Pulse", "Fitbit: Auth failed: ${result.exceptionOrNull()?.message}")
                Toast.makeText(this@MainActivity, "Fitbit sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Restore Fitbit tokens from storage and trigger a background sync if authenticated.
     */
    private fun attemptFitbitRestore() {
        lifecycleScope.launch {
            if (fitbitAuthManager.tryRestoreTokens()) {
                Log.d("Pulse", "Fitbit: tokens restored, scheduling sync")
                syncScheduler.scheduleFitbitSync()
            }
        }
    }

    @Composable
    private fun isSystemInDarkTheme() = androidx.compose.foundation.isSystemInDarkTheme()
}

@Composable
fun FitbitNavHost(hcDataSource: HealthConnectDataSource) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // Health Connect permission launcher
    var permissionsChecked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        permissionsChecked = true
        if (granted.isEmpty()) {
            Toast.makeText(context, "Health Connect permissions required for data sync", Toast.LENGTH_LONG).show()
        }
    }

    // Check and request permissions on first composition
    LaunchedEffect(Unit) {
        if (!hcDataSource.isAvailable()) {
            // HC not installed — skip
            permissionsChecked = true
            return@LaunchedEffect
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val needed = hcDataSource.requiredPermissions - granted
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        } else {
            permissionsChecked = true
        }
    }

    NavHost(navController = nav, startDestination = Dashboard) {
        dashboardScreen(
            onNavigateToMetric = { metric -> nav.navigate(MetricDetail(metric.name)) },
            onNavigateToExerciseLog = { nav.navigate(ExerciseLog) },
            onNavigateToExerciseDetail = { sessionId -> nav.navigate(ExerciseDetail(sessionId)) },
            onNavigateToInsights = { nav.navigate(Insights) },
            onNavigateToChat = {
                Toast.makeText(context, "Coach coming soon", Toast.LENGTH_SHORT).show()
            },
            onNavigateToProfile = {
                nav.navigate(YouProfile)
            },
            onNavigateToDebug = {
                if (BuildConfig.DEBUG_MENU_ENABLED) nav.navigate(DebugMenu)
            },
        )
        metricDetailScreen(onBack = { nav.popBackStack() })
        exerciseLogScreen(
            onBack = { nav.popBackStack() },
            onNavigateToDetail = { sessionId -> nav.navigate(ExerciseDetail(sessionId)) },
        )
        exerciseDetailScreen(onBack = { nav.popBackStack() })
        youScreen(onBack = { nav.popBackStack() })
        insightsScreen(
            onBack = { nav.popBackStack() },
            onNavigateToHeatmap = { metric -> nav.navigate(HeatmapDetail(metric)) },
        )
        heatmapDetailScreen(onBack = { nav.popBackStack() })
        debugMenuScreen(onDismiss = { nav.popBackStack() })
    }
}
